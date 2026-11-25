package com.example.spotifly

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.spotifly.data.deeplink.DeepLinkTarget
import com.example.spotifly.data.prefs.UserPreferencesManager
import com.example.spotifly.ui.main.MainScreen
import com.example.spotifly.ui.onboarding.DiscoverOnboardingScreen

@Composable
fun MainNavigation(
    deepLink: DeepLinkTarget? = null,
    onDeepLinkConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { UserPreferencesManager.getInstance(context) }
    val hasSeenOnboarding by prefs.hasSeenOnboarding.collectAsState()

    // Determine initial entry based on user onboarding status
    val initialKey: NavKey = remember {
        if (hasSeenOnboarding) Main else Onboarding
    }
    val backStack = rememberNavBackStack(initialKey)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Main> {
                MainScreen(
                    modifier = Modifier.safeDrawingPadding(),
                    deepLink = deepLink,
                    onDeepLinkConsumed = onDeepLinkConsumed,
                    onOpenDiscover = {
                        backStack.add(Onboarding)
                    }
                )
            }
            entry<Onboarding> {
                DiscoverOnboardingScreen(
                    onStartClick = {
                        prefs.setHasSeenOnboarding(true)
                        if (!backStack.contains(Main)) {
                            backStack.add(Main)
                        }
                        backStack.remove(Onboarding)
                    },
                    onBackClick = {
                        if (backStack.contains(Main)) {
                            backStack.remove(Onboarding)
                        } else {
                            prefs.setHasSeenOnboarding(true)
                            backStack.add(Main)
                            backStack.remove(Onboarding)
                        }
                    }
                )
            }
        }
    )
}
