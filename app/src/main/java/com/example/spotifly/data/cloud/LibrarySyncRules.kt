package com.example.spotifly.data.cloud

import com.example.spotifly.model.Song
import com.example.spotifly.model.Playlist
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest

@Serializable
data class LibraryRecord(val key: String, val revision: Long = 0, val deleted: Boolean = false, val payload: String = "", val operationId: String = "")
@Serializable
data class LibraryOperation(val id: String, val key: String, val observedRevision: Long = 0, val deleted: Boolean = false, val payload: String = "", val predecessor: String? = null, val guest: Boolean = false, val guestBasePayload: String? = null)
@Serializable
data class LibraryProfile(val records: Map<String, LibraryRecord> = emptyMap(), val pending: List<LibraryOperation> = emptyList(), val guestImport: String? = null, val guestImportAcknowledged: Boolean = false, val guestImportRevision: String? = null, val guestSnapshot: Map<String, LibraryRecord> = emptyMap())

object LibrarySyncRules {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun alreadyApplied(record: LibraryRecord?, operation: LibraryOperation) = record?.operationId == operation.id
    fun accepts(record: LibraryRecord?, operation: LibraryOperation): Boolean =
        record == null || !record.deleted || operation.observedRevision >= record.revision
    fun chunks(payload: String): List<String> = buildList {
        var start = 0
        while (start < payload.length) {
            var end = minOf(start + 40_000, payload.length)
            if (end < payload.length && payload[end - 1].isHighSurrogate()) end--
            add(payload.substring(start, end))
            start = end
        }
    }
    fun mergeGuestPlaylist(
        base: Playlist,
        local: Playlist,
        remote: Playlist,
        deviceId: String
    ): Playlist {
        fun key(song: Song) = songKey(cloudSong(song, deviceId))
        val localByKey = local.songs.associateBy(::key)
        val removed = base.songs.map(::key).toSet() - localByKey.keys
        val restored = remote.songs.filterNot { key(it) in removed }.map { remoteSong ->
            val localSong = localByKey[key(remoteSong)]
            if (localSong == null) {
                remoteSong
            } else {
                // A local file only matches after applying this installation's namespace.
                remoteSong.copy(
                    id = localSong.id,
                    internalId = localSong.internalId,
                    isLocal = localSong.isLocal,
                    contentUriString = localSong.contentUriString,
                    localFilePath = localSong.localFilePath,
                    isDownloaded = localSong.isDownloaded,
                    streamUrl = localSong.streamUrl,
                    isPlayable = localSong.isPlayable
                )
            }
        }
        return local.copy(songs = (restored + local.songs).distinctBy(::key))
    }

    fun stageGuest(
        profile: LibraryProfile,
        guest: LibraryProfile,
        account: String,
        operationId: () -> String
    ): LibraryProfile {
        if (guest.guestImport != null && guest.guestImport != account) return profile
        val changed = guest.records.values.filter { profile.guestSnapshot[it.key] != it }
        if (changed.isEmpty() && profile.guestImport != null) return profile

        val pending = changed.map { record ->
            val previousGuestPayload = profile.guestSnapshot[record.key]?.payload
                ?.takeIf { it.isNotEmpty() }
            LibraryOperation(
                id = operationId(),
                key = record.key,
                // Only changed guest records become operations. A new explicit action can
                // therefore observe an account tombstone without replaying unchanged data.
                observedRevision = profile.records[record.key]?.revision ?: 0,
                deleted = record.deleted,
                payload = record.payload,
                predecessor = profile.pending.lastOrNull { it.key == record.key }?.id,
                guest = true,
                guestBasePayload = previousGuestPayload ?: record.payload
            )
        }
        val guestRevision = guest.copy(
            pending = emptyList(),
            guestImport = null,
            guestImportRevision = null,
            guestImportAcknowledged = false,
            guestSnapshot = emptyMap()
        )
        val stagedRecords = changed.associate { record ->
            record.key to record.copy(revision = profile.records[record.key]?.revision ?: 0)
        }
        return profile.copy(
            records = profile.records + stagedRecords,
            pending = profile.pending + pending,
            guestImport = account,
            guestImportRevision = hash(json.encodeToString(LibraryProfile.serializer(), guestRevision)),
            guestSnapshot = guest.records,
            guestImportAcknowledged = false
        )
    }

    fun acknowledgeGuest(profile: LibraryProfile): LibraryProfile {
        require(profile.pending.isEmpty()) { "Guest snapshot is not server acknowledged" }
        require(profile.guestImport != null && profile.guestImportRevision != null)
        return profile.copy(guestImportAcknowledged = true)
    }
    fun merge(profile: LibraryProfile, server: List<LibraryRecord>): LibraryProfile {
        val pendingKeys = profile.pending.map { it.key }.toSet()
        return profile.copy(records = profile.records + server.filter { it.key !in pendingKeys }.associateBy { it.key })
    }
    fun acknowledge(profile: LibraryProfile, operation: LibraryOperation, server: LibraryRecord, acceptedRevision: Long?): LibraryProfile {
        val remaining = profile.pending.filterNot { it.id == operation.id }.map {
            if (it.predecessor == operation.id) it.copy(observedRevision = acceptedRevision ?: it.observedRevision, predecessor = null) else it
        }
        val visible = if (remaining.any { it.key == operation.key }) profile.records.getValue(operation.key).copy(revision = server.revision) else server
        return profile.copy(records = profile.records + (server.key to visible), pending = remaining)
    }
    fun hash(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    fun profileName(uid: String?) = if (uid == null) "guest.json" else "account_${hash(uid)}.json"
    fun songKey(song: Song): String {
        val migrated = song.ensureMigrated()
        return migrated.sourceIds.saavn?.takeIf { it.isNotBlank() }?.let { "saavn:$it" }
            ?: migrated.sourceIds.ytmusic?.takeIf { it.isNotBlank() }?.let { "yt:$it" }
            ?: migrated.sourceIds.deezer?.takeIf { it.isNotBlank() }?.let { "deezer:$it" }
            ?: migrated.sourceIds.itunes?.takeIf { it.isNotBlank() }?.let { "itunes:$it" }
            ?: "id:${song.id}"
    }
    fun cloudSong(song: Song, localNamespace: String = "unknown"): Song = song.ensureMigrated().copy(
        id = if (song.isLocal && !song.id.startsWith("unavailable_local:")) "unavailable_local:${hash(localNamespace + song.id)}" else song.id,
        internalId = if (song.isLocal && !song.id.startsWith("unavailable_local:")) "unavailable_local:${hash(localNamespace + song.id)}" else song.internalId,
        streamUrl = null, contentUriString = null, localFilePath = null, isDownloaded = false,
        artworkUrl = song.artworkUrl?.takeIf { it.startsWith("https://") || it.startsWith("http://") },
        isPlayable = !song.isLocal && song.ensureMigrated().sourceIds.let { it.saavn != null || it.ytmusic != null || it.deezer != null || it.itunes != null }
    )
}

data class LibraryHydrationState(val loading: Boolean = true, val error: String? = null)
