package com.example.spotifly

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import com.example.spotifly.data.deeplink.DeepLinkParser
import com.example.spotifly.data.deeplink.DeepLinkTarget
import com.example.spotifly.data.prefs.UserPreferencesManager
import com.example.spotifly.model.ThemeMode
import com.example.spotifly.theme.AccentThemes
import com.example.spotifly.theme.SpotiflyTheme

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /** Inbound spotifly:// link waiting to be handled by the Compose tree. */
    private val pendingDeepLink = mutableStateOf<DeepLinkTarget?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        // Only the first creation handles the intent: a rotation restores the
        // same intent and must not replay the deep link over the user's state.
        if (savedInstanceState == null) {
            pendingDeepLink.value = DeepLinkParser.parse(intent?.dataString)
        }

        val prefs = UserPreferencesManager.getInstance(applicationContext)

        setContent {
            val accentName by prefs.accentName.collectAsState()
            val themeMode by prefs.themeMode.collectAsState()
            val accent = AccentThemes.find { it.name == accentName } ?: AccentThemes.first()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> systemDark
            }

            // Status/navigation icons have to invert with the palette or they
            // disappear against the new background.
            LaunchedEffect(darkTheme) {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }

            SpotiflyTheme(accent = accent, darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainNavigation(
                        deepLink = pendingDeepLink.value,
                        onDeepLinkConsumed = { pendingDeepLink.value = null }
                    )
                }
            }
        }
    }

    /**
     * singleTop means a second link arrives here instead of recreating the
     * activity, so playback and navigation state survive.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        DeepLinkParser.parse(intent.dataString)?.let { pendingDeepLink.value = it }
    }

    /**
     * The media notification is the backbone of background playback. Without this
     * runtime request (Android 13+) the manifest permission alone does nothing and
     * the lock screen / notification controls never appear.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val permission = Manifest.permission.POST_NOTIFICATIONS
        val granted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(permission)
        }
    }
}
