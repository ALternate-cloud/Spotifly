package com.example.spotifly.data.cloud

/** Tokens distinguish A -> B -> A from the original A session. */
class LibrarySessionGeneration {
    private var generation = 0L
    private var uid: String? = null
    @Synchronized fun advance(account: String?): Long { uid = account; return ++generation }
    @Synchronized fun get(): Long = generation
    @Synchronized fun isCurrent(account: String?, token: Long) = uid == account && generation == token
}
