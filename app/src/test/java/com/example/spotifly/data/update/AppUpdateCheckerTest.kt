package com.example.spotifly.data.update

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateCheckerTest {

    @Test
    fun normalizeVersion_stripsPrefixesAndWhitespace() {
        assertEquals("2.0", AppUpdateChecker.normalizeVersion("2.0"))
        assertEquals("2.0", AppUpdateChecker.normalizeVersion("v2.0"))
        assertEquals("2.1.3", AppUpdateChecker.normalizeVersion("V2.1.3"))
        assertEquals("2.10.0", AppUpdateChecker.normalizeVersion("  v2.10.0  "))
        assertEquals("", AppUpdateChecker.normalizeVersion("   "))
        assertEquals("", AppUpdateChecker.normalizeVersion("v"))
    }

    @Test
    fun compareVersions_handlesNumericSegmentsAndSuffixes() {
        assertTrue(AppUpdateChecker.compareVersions("2.1", "2.0") > 0)
        assertTrue(AppUpdateChecker.compareVersions("2.0.1", "2.0") > 0)
        assertTrue(AppUpdateChecker.compareVersions("2.10", "2.2") > 0)
        assertEquals(0, AppUpdateChecker.compareVersions("v2.0", "2.0"))
        assertEquals(0, AppUpdateChecker.compareVersions("2.0.0", "V2.0"))
        assertTrue(AppUpdateChecker.compareVersions("1.9.9", "2.0") < 0)

        assertTrue(AppUpdateChecker.compareVersions("2.1-beta", "2.0") > 0)
        assertTrue(AppUpdateChecker.compareVersions("2.0-beta", "2.0") < 0)
        assertTrue(AppUpdateChecker.compareVersions("2.0", "2.0-beta") > 0)
        assertEquals(0, AppUpdateChecker.compareVersions("2.0-beta", "2.0-beta"))

        assertTrue(AppUpdateChecker.compareVersions("", "2.0") < 0)
        assertTrue(AppUpdateChecker.compareVersions("2.0", "") > 0)
        assertEquals(0, AppUpdateChecker.compareVersions("", ""))
        assertTrue(AppUpdateChecker.compareVersions("nonnumeric", "2.0") < 0)
    }

    @Test
    fun parseLatestRelease_withApkAsset_returnsUpdateAvailable() {
        val json = """
            {
              "tag_name": "v2.1.0",
              "name": "Spotifly 2.1.0",
              "draft": false,
              "prerelease": false,
              "published_at": "2026-11-28T10:00:00Z",
              "html_url": "https://github.com/ALternate-cloud/Spotifly/releases/tag/v2.1.0",
              "body": "- Endless autoplay\n- In-app updater",
              "assets": [
                {
                  "name": "checksums.txt",
                  "size": 128,
                  "browser_download_url": "https://github.com/ALternate-cloud/Spotifly/releases/download/v2.1.0/checksums.txt"
                },
                {
                  "name": "spotifly-release.apk",
                  "size": 15728640,
                  "browser_download_url": "https://github.com/ALternate-cloud/Spotifly/releases/download/v2.1.0/spotifly-release.apk"
                }
              ]
            }
        """.trimIndent()

        val result = AppUpdateChecker.parseLatestRelease(json, currentVersion = "2.0")
        assertTrue(result is UpdateCheckResult.UpdateAvailable)
        val info = (result as UpdateCheckResult.UpdateAvailable).info
        assertEquals("v2.1.0", info.tagName)
        assertEquals("2.1.0", info.versionName)
        assertEquals("Spotifly 2.1.0", info.title)
        assertEquals("- Endless autoplay\n- In-app updater", info.changelog)
        assertEquals("https://github.com/ALternate-cloud/Spotifly/releases/tag/v2.1.0", info.releasePageUrl)
        assertEquals(
            "https://github.com/ALternate-cloud/Spotifly/releases/download/v2.1.0/spotifly-release.apk",
            info.apkDownloadUrl
        )
        assertEquals(15728640L, info.apkSizeBytes)
        assertEquals("2026-11-28T10:00:00Z", info.publishedAt)
    }

    @Test
    fun parseLatestRelease_withoutApkAsset_fallsBackToHtmlUrl() {
        val json = """
            {
              "tag_name": "v2.2",
              "name": "",
              "draft": false,
              "prerelease": false,
              "html_url": "https://github.com/ALternate-cloud/Spotifly/releases/tag/v2.2",
              "body": null,
              "assets": []
            }
        """.trimIndent()

        val result = AppUpdateChecker.parseLatestRelease(json, currentVersion = "2.0")
        assertTrue(result is UpdateCheckResult.UpdateAvailable)
        val info = (result as UpdateCheckResult.UpdateAvailable).info
        assertEquals("v2.2", info.title)
        assertEquals("", info.changelog)
        assertEquals("https://github.com/ALternate-cloud/Spotifly/releases/tag/v2.2", info.apkDownloadUrl)
        assertEquals(0L, info.apkSizeBytes)
    }

    @Test
    fun parseLatestRelease_sameOrOlderVersion_returnsUpToDate() {
        val sameVersionJson = """
            {
              "tag_name": "v2.0",
              "draft": false,
              "prerelease": false,
              "html_url": "https://github.com/ALternate-cloud/Spotifly/releases/tag/v2.0"
            }
        """.trimIndent()
        val sameResult = AppUpdateChecker.parseLatestRelease(sameVersionJson, currentVersion = "2.0")
        assertEquals(UpdateCheckResult.UpToDate("2.0"), sameResult)

        val olderJson = """
            {
              "tag_name": "v1.9.5",
              "draft": false,
              "prerelease": false
            }
        """.trimIndent()
        val olderResult = AppUpdateChecker.parseLatestRelease(olderJson, currentVersion = "2.0")
        assertEquals(UpdateCheckResult.UpToDate("2.0"), olderResult)
    }

    @Test
    fun parseLatestRelease_draftOrPrerelease_returnsUpToDate() {
        val draftJson = """
            {
              "tag_name": "v9.0.0",
              "draft": true,
              "prerelease": false
            }
        """.trimIndent()
        assertEquals(
            UpdateCheckResult.UpToDate("2.0"),
            AppUpdateChecker.parseLatestRelease(draftJson, currentVersion = "2.0")
        )

        val prereleaseJson = """
            {
              "tag_name": "v9.0.0-beta.1",
              "draft": false,
              "prerelease": true
            }
        """.trimIndent()
        assertEquals(
            UpdateCheckResult.UpToDate("2.0"),
            AppUpdateChecker.parseLatestRelease(prereleaseJson, currentVersion = "2.0")
        )
    }

    @Test
    fun parseLatestRelease_malformedOrInvalidJson_returnsError() {
        assertTrue(AppUpdateChecker.parseLatestRelease("{not valid json", "2.0") is UpdateCheckResult.Error)
        assertTrue(AppUpdateChecker.parseLatestRelease("", "2.0") is UpdateCheckResult.Error)
        assertTrue(AppUpdateChecker.parseLatestRelease("""{"name": "no tag"}""", "2.0") is UpdateCheckResult.Error)
    }

    @Test
    fun autoCheckThrottle_enforcesTwelveHourCooldown() {
        val prefs = InMemorySharedPreferences()
        val startMs = 1_700_000_000_000L
        val oneHourMs = 60L * 60L * 1000L

        assertTrue(AppUpdateChecker.shouldAutoCheck(prefs, startMs))

        AppUpdateChecker.recordAutoCheck(prefs, startMs)
        assertFalse(AppUpdateChecker.shouldAutoCheck(prefs, startMs))
        assertFalse(AppUpdateChecker.shouldAutoCheck(prefs, startMs + 11 * oneHourMs))
        assertTrue(AppUpdateChecker.shouldAutoCheck(prefs, startMs + 12 * oneHourMs))
        assertTrue(AppUpdateChecker.shouldAutoCheck(prefs, startMs + 13 * oneHourMs))
    }

    @Test
    fun skipVersion_persistsAndMatchesNormalizedTags() {
        val prefs = InMemorySharedPreferences()
        assertFalse(AppUpdateChecker.isVersionSkipped(prefs, "v2.1.0"))

        AppUpdateChecker.skipVersion(prefs, "v2.1.0")
        assertTrue(AppUpdateChecker.isVersionSkipped(prefs, "v2.1.0"))
        assertTrue(AppUpdateChecker.isVersionSkipped(prefs, "2.1.0"))
        assertFalse(AppUpdateChecker.isVersionSkipped(prefs, "v2.2.0"))
    }

    private class InMemorySharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = data.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
            return (data[key] as? Set<String>)?.toMutableSet() ?: defValues
        }
        override fun getInt(key: String?, defValue: Int): Int = data[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = data[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = data[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun edit(): SharedPreferences.Editor = EditorImpl()
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private inner class EditorImpl : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private var clearAll = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = values?.toSet()
            }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun remove(key: String?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = null
            }
            override fun clear(): SharedPreferences.Editor = apply { clearAll = true }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clearAll) data.clear()
                pending.forEach { (k, v) ->
                    if (v == null) data.remove(k) else data[k] = v
                }
            }
        }
    }
}
