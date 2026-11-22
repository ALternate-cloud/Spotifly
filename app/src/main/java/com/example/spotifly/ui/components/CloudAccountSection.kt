package com.example.spotifly.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.spotifly.data.cloud.AccountState
import com.example.spotifly.data.cloud.CloudBackupState
import com.example.spotifly.theme.SpotifyTextSecondary
import com.example.spotifly.theme.SpotifyWhite

@Composable
fun CloudAccountSection(
    account: AccountState,
    backup: CloudBackupState,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text("Library backup", color = SpotifyWhite, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        if (account.uid == null) {
            Text("Sign in to back up playlists and liked songs and restore them after reinstalling.", color = SpotifyTextSecondary)
            Button(onClick = onSignIn, enabled = !account.busy) {
                Text(if (account.busy) "Signing in…" else "Sign in with Google")
            }
        } else {
            Text(account.email ?: "Signed in with Google", color = SpotifyWhite)
            Text(backup.message, color = SpotifyTextSecondary)
            TextButton(onClick = onSignOut, enabled = !account.busy) { Text("Sign out") }
        }
        if (backup.error) {
            if (account.uid == null) Text(backup.message, color = SpotifyTextSecondary)
            TextButton(onClick = onRetry, enabled = !backup.syncing) { Text("Retry") }
        }
        if (account.busy || backup.syncing) CircularProgressIndicator()
        account.error?.let { Text(it, color = SpotifyTextSecondary) }
        Text("Changes waiting for backup need an internet connection before uninstalling.", color = SpotifyTextSecondary)
    }
}
