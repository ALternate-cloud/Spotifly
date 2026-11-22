package com.example.spotifly.data.cloud

import java.io.File
import java.io.FileOutputStream
import kotlinx.serialization.encodeToString

/** Durable journal boundary; backups recover interrupted writes without defaulting corrupt data. */
class LibraryProfileFiles(private val directory: File) {
    private val json = LibrarySyncRules.json
    fun read(uid: String?): LibraryProfile? {
        val file = File(directory, LibrarySyncRules.profileName(uid))
        val backup = File(file.path + ".bak")
        val source = if (backup.exists()) backup else file
        if (!source.exists()) return null
        return json.decodeFromString(source.readText())
    }
    fun write(uid: String?, value: LibraryProfile) {
        check(directory.exists() || directory.mkdirs()) { "Cannot create library storage" }
        val file = File(directory, LibrarySyncRules.profileName(uid))
        val backup = File(file.path + ".bak")
        if (file.exists()) {
            if (backup.exists()) check(file.delete()) else check(file.renameTo(backup)) { "Cannot preserve previous library" }
        }
        try {
            FileOutputStream(file).use { stream -> stream.write(json.encodeToString(value).toByteArray()); stream.fd.sync() }
            if (backup.exists()) check(backup.delete()) { "Cannot finish library write" }
        } catch (failure: Exception) {
            file.delete()
            if (backup.exists()) backup.renameTo(file)
            throw failure
        }
    }
}
