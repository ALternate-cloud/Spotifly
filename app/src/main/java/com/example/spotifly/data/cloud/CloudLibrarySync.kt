package com.example.spotifly.data.cloud

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import com.example.spotifly.data.prefs.UserPreferencesManager
import com.example.spotifly.model.Playlist
import com.example.spotifly.model.Song
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString

/** Status is acknowledged only after an authoritative server read and drained durable journal. */
data class CloudBackupState(val message: String, val syncing: Boolean = false, val error: Boolean = false)

class CloudLibrarySync private constructor(context: Context) {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = LibraryProfileStore.getInstance(app)
    private val preferences by lazy { UserPreferencesManager.getInstance(app) }
    private val _state = MutableStateFlow(CloudBackupState("Sign in to back up playlists and liked songs"))
    val state: StateFlow<CloudBackupState> = _state.asStateFlow()
    private val generation = LibrarySessionGeneration()
    private val signals = Channel<Unit>(Channel.CONFLATED)
    private var listener: ListenerRegistration? = null
    private var auth: FirebaseAuth? = null
    private var database: FirebaseFirestore? = null
    private val json = LibrarySyncRules.json

    init {
        scope.launch {
            signals.receiveAsFlow().collect {
                val account = auth?.currentUser?.uid
                val token = generation.get()
                if (account == null) return@collect
                if (preferences.offlineMode.value) {
                    _state.value = CloudBackupState("Offline mode: edits are saved on this device and awaiting backup")
                    return@collect
                }
                try {
                    store.retryHydration()
                    check(store.hydration.value.error == null && !store.hydration.value.loading) { store.hydration.value.error ?: "Loading local library" }
                    checkSession(account, token)
                    _state.value = CloudBackupState("Restoring and backing up library…", syncing = true)
                    val items = collection(account)
                    // Source.SERVER is essential: missing cache entries do not mean an empty account.
                    val manifests = items.get(Source.SERVER).await()
                    checkSession(account, token)
                    val remote = manifests.documents.map { readRecord(account, it, token) }
                    checkSession(account, token)
                    store.merge(account, remote)
                    while (true) {
                        checkSession(account, token)
                        val operation = store.snapshot(account).pending.firstOrNull() ?: break
                        val result = commit(account, token, operation)
                        checkSession(account, token)
                        store.acknowledge(account, operation, result.first, result.second)
                    }
                    checkSession(account, token)
                    store.acknowledgeImport(account)
                    check(store.hydration.value.error == null) { store.hydration.value.error ?: "Local save failed" }
                    _state.value = CloudBackupState("Library backed up")
                } catch (_: CancellationException) {
                    // Identity changes invalidate old callbacks without changing the new profile.
                } catch (failure: Exception) {
                    if (generation.get() == token && auth?.currentUser?.uid == account) {
                        _state.value = CloudBackupState("Backup pending: ${failure.localizedMessage ?: "connection failed"}. Retry when connected.", error = true)
                    }
                }
            }
        }
        scope.launch { store.changes.collect { signals.trySend(Unit) } }
        scope.launch { preferences.offlineMode.collect { offline ->
            listener?.remove(); listener = null
            if (!offline) auth?.currentUser?.uid?.let { attachListener(it, generation.get()) }
            signals.trySend(Unit)
        } }
        scope.launch {
            var recovering = false
            store.hydration.collect { local ->
                if (local.error != null) { recovering = true; _state.value = CloudBackupState(local.error, error = true) }
                else if (!local.loading && recovering) {
                    recovering = false
                    if (auth?.currentUser == null && auth != null) _state.value = CloudBackupState("Sign in to back up playlists and liked songs")
                    else signals.trySend(Unit)
                }
            }
        }
        scope.launch { while (isActive) { delay(60_000); if (_state.value.error || auth?.currentUser?.uid?.let { runCatching { store.snapshot(it).pending.isNotEmpty() }.getOrDefault(false) } == true) signals.trySend(Unit) } }
        runCatching {
            val connectivity = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            connectivity.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { signals.trySend(Unit) }
            })
        }
        initializeFirebase()
    }
    private fun initializeFirebase() {
        runCatching {
            val firebaseAuth = FirebaseAuth.getInstance()
            database = FirebaseFirestore.getInstance().also {
                it.firestoreSettings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
            }
            auth = firebaseAuth
            firebaseAuth.addAuthStateListener { identity ->
                val account = identity.currentUser?.uid
                val token = generation.advance(account)
                listener?.remove(); listener = null
                store.switchProfile(account)
                if (account == null) {
                    _state.value = store.hydration.value.error?.let { CloudBackupState(it, error = true) }
                        ?: CloudBackupState("Sign in to back up playlists and liked songs")
                } else {
                    _state.value = CloudBackupState("Preparing library backup…", syncing = true)
                    if (!preferences.offlineMode.value) attachListener(account, token)
                    signals.trySend(Unit)
                }
            }
        }.onFailure { _state.value = CloudBackupState("Cloud backup is unavailable: ${it.localizedMessage}", error = true) }
    }
    private fun attachListener(account: String, token: Long) {
        if (database == null || preferences.offlineMode.value) return
        listener = collection(account).addSnapshotListener { _, failure ->
            if (generation.get() == token && auth?.currentUser?.uid == account) {
                if (failure != null) _state.value = CloudBackupState("Backup pending: ${failure.localizedMessage}", error = true)
                else signals.trySend(Unit)
            }
        }
    }
    fun retry() {
        if (auth == null) initializeFirebase()
        scope.launch { store.retryHydration(); signals.trySend(Unit) }
    }
    private fun checkSession(account: String, token: Long) {
        if (!generation.isCurrent(account, token) || auth?.currentUser?.uid != account || store.uid != account || preferences.offlineMode.value) throw CancellationException("Library session changed")
    }
    private fun collection(account: String) = requireNotNull(database).collection("users").document(account).collection("libraryItems")
    private suspend fun readRecord(account: String, manifest: DocumentSnapshot, token: Long): LibraryRecord {
        checkSession(account, token)
        val key = requireNotNull(manifest.getString("key")) { "Invalid library manifest" }
        val revision = requireNotNull(manifest.getLong("revision"))
        val deleted = manifest.getBoolean("deleted") ?: false
        val operation = manifest.getString("operationId") ?: ""
        if (deleted) return LibraryRecord(key, revision, true, operationId = operation)
        val cached = store.snapshot(account).records[key]
        if (cached != null && cached.revision == revision && cached.operationId == operation && !cached.deleted) return cached
        val chunkCount = requireNotNull(manifest.getLong("chunkCount")).toInt()
        require(chunkCount in 1..100_000) { "Invalid library revision size" }
        val revisionId = requireNotNull(manifest.getString("revisionId"))
        val payload = buildString {
            for (index in 0 until chunkCount) {
                checkSession(account, token)
                val chunk = collection(account).document(manifest.id).collection("revisions").document(revisionId).collection("chunks").document(index.toString()).get(Source.SERVER).await()
                checkSession(account, token)
                append(requireNotNull(chunk.getString("payload")) { "Incomplete library revision" })
            }
        }
        require(LibrarySyncRules.hash(payload) == manifest.getString("payloadHash")) { "Library revision checksum mismatch" }
        // Validate before merging so corrupt data is never interpreted as an empty record.
        if (key.startsWith("p:")) json.decodeFromString<Playlist>(payload) else json.decodeFromString<Song>(payload)
        return LibraryRecord(key, revision, false, payload, operation)
    }
    private fun cloudPayload(operation: LibraryOperation): String = when {
        operation.deleted -> ""
        operation.key.startsWith("p:") -> json.encodeToString(json.decodeFromString<Playlist>(operation.payload).let { it.copy(coverPath = null, songs = it.songs.map { song -> LibrarySyncRules.cloudSong(song, store.deviceId) }) })
        else -> json.encodeToString(LibrarySyncRules.cloudSong(json.decodeFromString(operation.payload), store.deviceId))
    }
    private suspend fun commit(account: String, token: Long, operation: LibraryOperation): Pair<LibraryRecord, Long?> {
        checkSession(account, token)
        val db = requireNotNull(database)
        val item = collection(account).document(LibrarySyncRules.hash(operation.key))
        val receipt = db.collection("users").document(account).collection("libraryOperations").document(operation.id)
        val payload = cloudPayload(operation)
        val chunks = if (operation.deleted) emptyList() else LibrarySyncRules.chunks(payload)
        // Immutable chunks are fully server-acknowledged before publishing their manifest.
        chunks.withIndex().chunked(20).forEach { group ->
            checkSession(account, token)
            db.runTransaction { transaction ->
                transaction.get(item) // Online transaction; never enqueue an offline write.
                checkSession(account, token)
                group.forEach { (index, chunk) ->
                    val target = item.collection("revisions").document(operation.id)
                        .collection("chunks").document(index.toString())
                    transaction.set(target, mapOf("payload" to chunk))
                }
            }.await()
        }
        checkSession(account, token)
        db.runTransaction { transaction ->
            checkSession(account, token)
            val priorReceipt = transaction.get(receipt)
            val current = transaction.get(item)
            if (!priorReceipt.exists()) {
                val revision = current.getLong("revision") ?: 0
                val tombstone = current.getBoolean("deleted") ?: false
                val previous = if (current.exists()) {
                    LibraryRecord(operation.key, revision, tombstone)
                } else {
                    null
                }
                val accepted = LibrarySyncRules.accepts(previous, operation)
                if (accepted) {
                    checkSession(account, token)
                    transaction.set(item, mapOf(
                        "key" to operation.key,
                        "revision" to revision + 1,
                        "deleted" to operation.deleted,
                        "operationId" to operation.id,
                        "revisionId" to operation.id,
                        "chunkCount" to chunks.size,
                        "payloadHash" to LibrarySyncRules.hash(payload),
                        "committedAt" to FieldValue.serverTimestamp()
                    ))
                }
                transaction.set(receipt, mapOf(
                    "key" to operation.key,
                    "accepted" to accepted,
                    "revision" to if (accepted) revision + 1 else revision
                ))
            }
        }.await()
        checkSession(account, token)
        // Reading the current winner also handles a later concurrent edit after our commit.
        val delivered = receipt.get(Source.SERVER).await()
        checkSession(account, token)
        val acceptedRevision = if (delivered.getBoolean("accepted") == true) delivered.getLong("revision") else null
        return readRecord(account, item.get(Source.SERVER).await(), token) to acceptedRevision
    }
    companion object {
        // This process-wide sync service retains only the application context.
        @android.annotation.SuppressLint("StaticFieldLeak")
        @Volatile private var instance: CloudLibrarySync? = null
        fun getInstance(context: Context): CloudLibrarySync = instance ?: synchronized(this) {
            instance ?: CloudLibrarySync(context.applicationContext).also { instance = it }
        }
    }
}
