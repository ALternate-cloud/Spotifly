package com.example.spotifly.data.cloud

import android.content.Context
import com.example.spotifly.model.Playlist
import com.example.spotifly.model.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import java.io.File
import java.util.UUID

/** A single atomic journal contains both visible records and their unacknowledged edits. */
class LibraryProfileStore private constructor(private val context: Context) {
    private val lock = Any()
    private val session = LibrarySessionGeneration()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    val deviceId: String = context.getSharedPreferences("library_device", Context.MODE_PRIVATE).let { prefs ->
        prefs.getString("id", null) ?: UUID.randomUUID().toString().also { check(prefs.edit().putString("id", it).commit()) }
    }
    private val directory = File(context.filesDir, "library_profiles")
    private val files = LibraryProfileFiles(directory)
    private val json = LibrarySyncRules.json
    private var profile = LibraryProfile()
    @Volatile var uid: String? = null; private set
    private var ready = false
    private val deferred = mutableListOf<() -> Unit>()
    private val _hydration = MutableStateFlow(LibraryHydrationState())
    val hydration = _hydration.asStateFlow()
    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists = _playlists.asStateFlow()
    private val _favorites = MutableStateFlow<List<Song>>(emptyList())
    val favorites = _favorites.asStateFlow()
    private val _changes = MutableStateFlow(0L)
    val changes = _changes.asStateFlow()

    init { scope.launch { synchronized(lock) { if (!ready && uid == null) load(null) } } }

    private fun read(account: String?): LibraryProfile = files.read(account)
        ?: if (account == null) migrateGuest() else LibraryProfile()
    private fun write(account: String?, value: LibraryProfile) = files.write(account, value)
    private fun migrateGuest(): LibraryProfile {
        fun legacy(name: String): String? = File(context.filesDir, name).takeIf { it.exists() }?.readText()
        val playlists = legacy("playlists.json")?.let { json.decodeFromString<List<Playlist>>(it) }
            ?: listOf(Playlist("favorites_playlist", "My Favorites"))
        val favorites = legacy("favorites.json")?.let { json.decodeFromString<List<Song>>(it) } ?: emptyList()
        val records = playlists.associate { "p:${it.id}" to LibraryRecord("p:${it.id}", payload = json.encodeToString(it)) } +
            favorites.associate { favoriteKey(it) to LibraryRecord(favoriteKey(it), payload = json.encodeToString(it)) }
        return LibraryProfile(records).also { write(null, it) } // Originals intentionally retained.
    }
    private fun load(account: String?) {
        _hydration.value = LibraryHydrationState()
        ready = false
        uid = account
        try {
            profile = read(account)
            if (account != null) {
                val staged = LibrarySyncRules.stageGuest(profile, read(null), account) { UUID.randomUUID().toString() }
                if (staged != profile) {
                    write(account, staged)
                    profile = staged
                }
            }
            ready = true
            publish(true)
            _hydration.value = LibraryHydrationState(false)
            val actions = deferred.toList()
            deferred.clear()
            actions.forEach { it() }
        } catch (failure: Exception) {
            ready = false
            _playlists.value = emptyList()
            _favorites.value = emptyList()
            _hydration.value = LibraryHydrationState(false, "Library could not be loaded: ${failure.message}. Original files were preserved.")
        }
    }
    fun switchProfile(account: String?) = synchronized(lock) {
        if (uid != account || !ready) {
            if (uid != account) deferred.clear()
            session.advance(account)
            uid = account
            ready = false
            _hydration.value = LibraryHydrationState()
            _playlists.value = emptyList()
            _favorites.value = emptyList()
            scope.launch { synchronized(lock) { if (uid == account && !ready) load(account) } }
        }
    }
    fun retryHydration() = synchronized(lock) { if (!ready) load(uid) }
    fun snapshot(account: String): LibraryProfile = synchronized(lock) { check(ready && uid == account); profile }
    private fun publish(notify: Boolean = false) {
        _playlists.value = profile.records.values.filter { it.key.startsWith("p:") && !it.deleted }.map { json.decodeFromString<Playlist>(it.payload) }
        _favorites.value = profile.records.values.filter { it.key.startsWith("f:") && !it.deleted }.map { json.decodeFromString<Song>(it.payload) }
        if (notify) _changes.value++
    }
    fun sessionToken(): Long = session.get()
    private fun edit(token: Long = session.get(), action: () -> Unit) {
        val owner = uid
        scope.launch {
            synchronized(lock) {
                if (!session.isCurrent(owner, token)) return@synchronized
                if (!ready) {
                    if (_hydration.value.error == null) deferred.add(action)
                    return@synchronized
                }
                try {
                    action()
                } catch (failure: Exception) {
                    ready = false
                    _hydration.value = LibraryHydrationState(
                        loading = false,
                        error = "Library could not be saved: ${failure.message}"
                    )
                }
            }
        }
    }
    private fun put(key: String, payload: String?, deleted: Boolean) {
        val prior = profile.records[key]
        if (prior != null && prior.payload == payload && prior.deleted == deleted) return
        val operation = LibraryOperation(UUID.randomUUID().toString(), key, prior?.revision ?: 0, deleted, payload ?: "", profile.pending.lastOrNull { it.key == key }?.id)
        val updated = profile.copy(records = profile.records + (key to LibraryRecord(key, prior?.revision ?: 0, deleted, payload ?: "")), pending = profile.pending + operation)
        write(uid, updated)
        profile = updated
        publish(true)
    }
    fun createPlaylist(playlist: Playlist) = edit { put("p:${playlist.id}", json.encodeToString(playlist), false) }
    fun updatePlaylist(id: String, expectedSession: Long = session.get(), transform: (Playlist) -> Playlist) = edit(expectedSession) {
        val record = profile.records["p:$id"]
        if (record != null && !record.deleted) put(record.key, json.encodeToString(transform(json.decodeFromString(record.payload))), false)
    }
    fun deletePlaylist(id: String) = edit { put("p:$id", null, true) }
    private fun favoriteKey(song: Song): String = profile.records.values.firstOrNull {
        it.key.startsWith("f:") && !it.deleted && runCatching { json.decodeFromString<Song>(it.payload).id == song.id }.getOrDefault(false)
    }?.key ?: if (song.isLocal) "f:local:$deviceId:${song.id}" else "f:${LibrarySyncRules.songKey(song)}"
    fun toggleFavorite(song: Song) = edit {
        val key = favoriteKey(song)
        val exists = profile.records[key]?.deleted == false
        put(key, if (exists) null else json.encodeToString(song), exists)
    }
    /** Merge cloud into the base while keeping every edit created during the read. */
    fun merge(account: String, server: List<LibraryRecord>) = synchronized(lock) {
        check(ready && uid == account)
        val remoteByKey = server.associateBy { it.key }
        val guestBase = profile.pending.filter { it.guest }.associate { it.key to (it.guestBasePayload ?: it.payload) }
        val guestKeys = guestBase.keys
        val pending = profile.pending.map { operation ->
            val remote = remoteByKey[operation.key]
            val mergeGuestPlaylist = operation.key in guestKeys &&
                !operation.deleted && operation.key.startsWith("p:") &&
                remote != null && !remote.deleted
            if (mergeGuestPlaylist) {
                val guest = json.decodeFromString<Playlist>(operation.payload)
                val cloud = json.decodeFromString<Playlist>(remote.payload)
                val combined = LibrarySyncRules.mergeGuestPlaylist(
                    base = json.decodeFromString(guestBase.getValue(operation.key)),
                    local = guest,
                    remote = cloud,
                    deviceId = deviceId
                )
                operation.copy(payload = json.encodeToString(combined), guest = false)
            } else {
                operation
            }
        }
        val optimistic = profile.records.toMutableMap()
        pending.filter { it != profile.pending.find { original -> original.id == it.id } }.forEach {
            optimistic[it.key] = optimistic.getValue(it.key).copy(payload = it.payload)
        }
        val base = profile.copy(records = optimistic, pending = pending)
        val result = LibrarySyncRules.merge(base, server)
        val pendingKeys = pending.map { it.key }.toSet()
        val updated = result.copy(
            records = result.records.mapValues { (key, value) ->
                if (key in pendingKeys) value else preserveLocal(value)
            }
        )
        write(account, updated)
        profile = updated
        publish()
    }
    private fun preserveLocal(server: LibraryRecord): LibraryRecord {
        if (server.deleted) return server
        val local = profile.records[server.key]?.takeIf { !it.deleted } ?: return server
        return if (server.key.startsWith("p:")) {
            val remote = json.decodeFromString<Playlist>(server.payload)
            val previous = json.decodeFromString<Playlist>(local.payload)
            server.copy(payload = json.encodeToString(remote.copy(coverPath = previous.coverPath, songs = remote.songs.map { track -> previous.songs.find { LibrarySyncRules.songKey(LibrarySyncRules.cloudSong(it, deviceId)) == LibrarySyncRules.songKey(track) }?.let { restoreDevice(track, it) } ?: track })))
        } else server.copy(payload = json.encodeToString(restoreDevice(json.decodeFromString(server.payload), json.decodeFromString(local.payload))))
    }
    private fun restoreDevice(remote: Song, local: Song): Song = if (remote.isLocal && LibrarySyncRules.cloudSong(local, deviceId).id != remote.id) remote else remote.copy(id = local.id, internalId = local.internalId, contentUriString = local.contentUriString, localFilePath = local.localFilePath, isDownloaded = local.isDownloaded, streamUrl = local.streamUrl, isPlayable = local.isPlayable)
    fun acknowledge(account: String, operation: LibraryOperation, server: LibraryRecord, acceptedRevision: Long?) = synchronized(lock) {
        check(ready && uid == account)
        val result = LibrarySyncRules.acknowledge(profile, operation, server, acceptedRevision)
        val updated = if (result.pending.none { it.key == operation.key }) result.copy(records = result.records + (server.key to preserveLocal(server))) else result
        write(account, updated)
        profile = updated
        publish()
    }
    fun acknowledgeImport(account: String) = synchronized(lock) {
        check(ready && uid == account && profile.pending.isEmpty())
        if (profile.guestImport == account && !profile.guestImportAcknowledged) {
            val guest = read(null)
            write(null, guest.copy(guestImport = account, guestImportAcknowledged = true, guestImportRevision = profile.guestImportRevision, pending = if (guest.records == profile.guestSnapshot) emptyList() else guest.pending))
            val updated = LibrarySyncRules.acknowledgeGuest(profile)
            write(account, updated)
            profile = updated
        }
    }
    companion object {
        // The singleton is constructed with applicationContext, never an Activity.
        @android.annotation.SuppressLint("StaticFieldLeak")
        @Volatile private var instance: LibraryProfileStore? = null
        fun getInstance(context: Context): LibraryProfileStore = instance ?: synchronized(this) {
            instance ?: LibraryProfileStore(context.applicationContext).also { instance = it }
        }
    }
}
