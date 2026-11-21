package com.example.spotifly

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.spotifly.data.deeplink.DeepLinkTarget
import com.example.spotifly.ui.main.MainScreen

@Composable
fun MainNavigation(
    deepLink: DeepLinkTarget? = null,
    onDeepLinkConsumed: () -> Unit = {}
) {
    val backStack = rememberNavBackStack(Main)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Main> {
                MainScreen(
                    modifier = Modifier.safeDrawingPadding(),
                    deepLink = deepLink,
                    onDeepLinkConsumed = onDeepLinkConsumed
                )
            }
        }
    )
}
