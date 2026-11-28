package com.example.spotifly.data.update

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.spotifly.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

data class AppUpdateInfo(
    val tagName: String,
    val versionName: String,
    val title: String,
    val changelog: String,
    val releasePageUrl: String,
    val apkDownloadUrl: String,
    val apkSizeBytes: Long,
    val publishedAt: String
)

sealed class UpdateCheckResult {
    data class UpdateAvailable(val info: AppUpdateInfo) : UpdateCheckResult()
    data class UpToDate(val currentVersion: String) : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

object AppUpdateChecker {

    const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/ALternate-cloud/Spotifly/releases/latest"
    const val RELEASES_PAGE_FALLBACK_URL =
        "https://github.com/ALternate-cloud/Spotifly/releases/latest"

    private const val PREFS_NAME = "spotifly_update_prefs"
    private const val KEY_LAST_AUTO_CHECK_MS = "last_auto_check_ms"
    private const val KEY_SKIPPED_VERSIONS = "skipped_versions"
    private const val AUTO_CHECK_INTERVAL_MS = 12L * 60L * 60L * 1000L

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    fun normalizeVersion(raw: String): String {
        return raw.trim()
            .dropWhile { it == 'v' || it == 'V' }
            .trim()
    }

    fun compareVersions(latest: String, current: String): Int {
        val latestNorm = normalizeVersion(latest)
        val currentNorm = normalizeVersion(current)

        if (latestNorm.isEmpty() && currentNorm.isEmpty()) return 0
        if (latestNorm.isEmpty()) return -1
        if (currentNorm.isEmpty()) return 1

        val (latestSegments, latestSuffix) = parseCoreAndSuffix(latestNorm)
        val (currentSegments, currentSuffix) = parseCoreAndSuffix(currentNorm)

        val maxLen = maxOf(latestSegments.size, currentSegments.size)
        for (i in 0 until maxLen) {
            val a = latestSegments.getOrElse(i) { 0 }
            val b = currentSegments.getOrElse(i) { 0 }
            if (a != b) return a.compareTo(b)
        }

        return when {
            latestSuffix == null && currentSuffix == null -> 0
            latestSuffix == null && currentSuffix != null -> 1
            latestSuffix != null && currentSuffix == null -> -1
            else -> latestSuffix!!.compareTo(currentSuffix!!, ignoreCase = true).coerceIn(-1, 1)
        }
    }

    fun parseLatestRelease(jsonBody: String, currentVersion: String): UpdateCheckResult {
        val normalizedCurrent = normalizeVersion(currentVersion).ifEmpty { currentVersion.trim() }
        if (jsonBody.isBlank()) {
            return UpdateCheckResult.Error("Empty release response")
        }

        return try {
            val json = JSONObject(jsonBody)
            if (json.optBoolean("draft", false) || json.optBoolean("prerelease", false)) {
                return UpdateCheckResult.UpToDate(normalizedCurrent)
            }

            val tagName = json.optNonNullString("tag_name")
            if (tagName.isEmpty()) {
                return UpdateCheckResult.Error("Release response missing tag_name")
            }

            val versionName = normalizeVersion(tagName).ifEmpty { tagName }
            if (compareVersions(versionName, currentVersion) <= 0) {
                return UpdateCheckResult.UpToDate(normalizedCurrent)
            }

            val title = json.optNonNullString("name").ifEmpty { tagName }
            val changelog = json.optNonNullString("body")
            val releasePageUrl = json.optNonNullString("html_url")
                .ifEmpty { RELEASES_PAGE_FALLBACK_URL }
            val publishedAt = json.optNonNullString("published_at")

            var apkDownloadUrl = releasePageUrl
            var apkSizeBytes = 0L

            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optNonNullString("name")
                    val downloadUrl = asset.optNonNullString("browser_download_url")
                    val isApk = name.endsWith(".apk", ignoreCase = true) ||
                        downloadUrl.endsWith(".apk", ignoreCase = true)
                    if (isApk && downloadUrl.isNotEmpty()) {
                        apkDownloadUrl = downloadUrl
                        apkSizeBytes = asset.optLong("size", 0L).coerceAtLeast(0L)
                        break
                    }
                }
            }

            UpdateCheckResult.UpdateAvailable(
                AppUpdateInfo(
                    tagName = tagName,
                    versionName = versionName,
                    title = title,
                    changelog = changelog,
                    releasePageUrl = releasePageUrl,
                    apkDownloadUrl = apkDownloadUrl,
                    apkSizeBytes = apkSizeBytes,
                    publishedAt = publishedAt
                )
            )
        } catch (e: Exception) {
            UpdateCheckResult.Error(e.message ?: "Failed to parse release metadata")
        }
    }

    suspend fun checkForUpdates(
        currentVersion: String = BuildConfig.VERSION_NAME,
        force: Boolean = false
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        val requestBuilder = Request.Builder()
            .url(LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Spotifly-Android")
        if (force) {
            requestBuilder.header("Cache-Control", "no-cache")
        }

        try {
            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext UpdateCheckResult.Error(
                        "Update check failed (HTTP ${response.code})"
                    )
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) {
                    return@withContext UpdateCheckResult.Error("Empty response from GitHub")
                }
                parseLatestRelease(body, currentVersion)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            UpdateCheckResult.Error(e.message ?: "Unable to check for updates")
        }
    }

    fun shouldAutoCheck(context: Context, nowMs: Long = System.currentTimeMillis()): Boolean {
        return shouldAutoCheck(prefs(context), nowMs)
    }

    fun recordAutoCheck(context: Context, nowMs: Long = System.currentTimeMillis()) {
        recordAutoCheck(prefs(context), nowMs)
    }

    fun isVersionSkipped(context: Context, tagName: String): Boolean {
        return isVersionSkipped(prefs(context), tagName)
    }

    fun skipVersion(context: Context, tagName: String) {
        skipVersion(prefs(context), tagName)
    }

    internal fun shouldAutoCheck(prefs: SharedPreferences, nowMs: Long): Boolean {
        val lastCheckMs = prefs.getLong(KEY_LAST_AUTO_CHECK_MS, -1L)
        if (lastCheckMs < 0L) return true
        return (nowMs - lastCheckMs) >= AUTO_CHECK_INTERVAL_MS
    }

    internal fun recordAutoCheck(prefs: SharedPreferences, nowMs: Long) {
        prefs.edit { putLong(KEY_LAST_AUTO_CHECK_MS, nowMs) }
    }

    internal fun isVersionSkipped(prefs: SharedPreferences, tagName: String): Boolean {
        val key = normalizeSkipKey(tagName)
        if (key.isEmpty()) return false
        val skipped = prefs.getStringSet(KEY_SKIPPED_VERSIONS, emptySet()).orEmpty()
        return skipped.contains(key)
    }

    internal fun skipVersion(prefs: SharedPreferences, tagName: String) {
        val key = normalizeSkipKey(tagName)
        if (key.isEmpty()) return
        val updated = prefs.getStringSet(KEY_SKIPPED_VERSIONS, emptySet()).orEmpty().toMutableSet()
        updated.add(key)
        prefs.edit { putStringSet(KEY_SKIPPED_VERSIONS, updated) }
    }

    fun formatApkSize(bytes: Long): String {
        if (bytes <= 0L) return ""
        val kb = bytes / 1024.0
        if (kb < 1024.0) {
            return String.format(Locale.US, "%.0f KB", kb)
        }
        val mb = kb / 1024.0
        return if (mb >= 1024.0) {
            String.format(Locale.US, "%.2f GB", mb / 1024.0)
        } else {
            String.format(Locale.US, "%.1f MB", mb)
        }
    }

    private fun prefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun normalizeSkipKey(tagName: String): String {
        return normalizeVersion(tagName).ifEmpty { tagName.trim() }
    }

    private fun parseCoreAndSuffix(normalized: String): Pair<List<Int>, String?> {
        val withoutBuild = normalized.substringBefore('+')
        val dashIdx = withoutBuild.indexOf('-')
        val core = if (dashIdx >= 0) withoutBuild.substring(0, dashIdx) else withoutBuild
        val suffix = if (dashIdx >= 0) {
            withoutBuild.substring(dashIdx + 1).trim().ifEmpty { null }
        } else {
            null
        }
        val segments = core.split('.').map { segment ->
            segment.trim().takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        }
        return segments to suffix
    }

    private fun JSONObject.optNonNullString(key: String): String {
        if (!has(key) || isNull(key)) return ""
        return optString(key, "").trim()
    }
}
