package com.example.spotifly.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spotifly.BuildConfig
import com.example.spotifly.theme.SpotifyBackground
import com.example.spotifly.theme.SpotifyCard
import com.example.spotifly.theme.SpotifyGreen
import com.example.spotifly.theme.SpotifyTextSecondary
import com.example.spotifly.theme.SpotifyWhite

/**
 * About & legal. Written as an honest inventory: what this app is, where every byte
 * comes from, and what it deliberately does not do.
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    versionName: String = BuildConfig.VERSION_NAME,
    currentVersionName: String = versionName,
    updateStatusText: String? = null,
    isCheckingUpdate: Boolean = false,
    onCheckForUpdates: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SpotifyBackground)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = SpotifyWhite
                )
            }
            Text(
                text = "About & legal",
                color = SpotifyWhite,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Spotifly",
                    color = SpotifyWhite,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Version $currentVersionName • Android music player",
                    color = SpotifyTextSecondary,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SpotifyCard)
                        .clickable(enabled = !isCheckingUpdate, onClick = onCheckForUpdates)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = SpotifyGreen,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Check for updates",
                            color = SpotifyWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = when {
                                isCheckingUpdate -> "Checking GitHub Releases…"
                                !updateStatusText.isNullOrBlank() -> updateStatusText
                                else -> "Current version v$currentVersionName"
                            },
                            color = SpotifyTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    if (isCheckingUpdate) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = SpotifyGreen,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Check",
                            color = SpotifyGreen,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                AboutBlock(
                    title = "Clean-room build",
                    body = "Spotifly recreates music-player functionality and interaction patterns. " +
                        "It ships no Spotify catalogue, artwork, logos, trademarks or copy, and it " +
                        "calls no Spotify private APIs. Spotify is referenced only as the product " +
                        "whose behaviour this app was measured against."
                )
            }

            item {
                AboutBlock(
                    title = "Where audio comes from",
                    body = "• Files on this device, read with the system media permission.\n" +
                        "• Tracks you download for offline play, stored in app-private storage.\n" +
                        "• Public streaming catalogue results for search and streaming.\n" +
                        "• Podcast episodes from each show's own public RSS feed, discovered " +
                        "through the public iTunes search index."
                )
            }

            item {
                AboutBlock(
                    title = "What is stored locally",
                    body = "Liked songs, playlists, play history, listening stats, follow list, " +
                        "episode resume points and audio settings are written to this app's own " +
                        "storage. Optional Google sign-in backs up playlists and liked songs to Firebase. " +
                        "Google processes account and library data for this backup. Pending offline changes " +
                        "need to finish syncing before uninstalling. Downloaded audio stays on this device."
                )
            }

            item {
                AboutBlock(
                    title = "Playback stack",
                    body = "Media3 / ExoPlayer for decoding, a foreground MediaSessionService for " +
                        "background playback and system media controls, and Android's audio effects " +
                        "framework for the equalizer, bass boost and loudness normalisation."
                )
            }

            item {
                AboutBlock(
                    title = "Known limits",
                    body = "Crossfade is a fade-through on a single player, not two overlapping " +
                        "decks. Recommendations are heuristics over your own play log rather than " +
                        "a trained model. Cloud library backup covers playlists and liked songs. " +
                        "Local audio files and custom cover images remain device-local."
                )
            }
        }
    }
}

@Composable
private fun AboutBlock(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(SpotifyCard)
            .padding(14.dp)
    ) {
        Box {
            Text(
                text = title,
                color = SpotifyGreen,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = body,
            color = SpotifyTextSecondary,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
    }
}
