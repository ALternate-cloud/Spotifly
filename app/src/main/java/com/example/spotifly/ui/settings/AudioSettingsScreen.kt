package com.example.spotifly.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.spotifly.player.AudioEffectsManager
import com.example.spotifly.player.PlaybackTuning
import com.example.spotifly.theme.SpotifyBackground
import com.example.spotifly.theme.SpotifyCard
import com.example.spotifly.theme.SpotifyGreen
import com.example.spotifly.theme.SpotifyTextSecondary
import com.example.spotifly.theme.SpotifyWhite
import kotlinx.coroutines.delay
import com.example.spotifly.theme.SpotifyBlack

/**
 * Spotify's Playback + Equalizer settings in one place. Every control here changes
 * real behaviour: crossfade ramps the player volume, the equalizer and bass boost
 * drive Android audiofx on ExoPlayer's session, and skip-silence is applied to the
 * decoder on the next service start.
 */
@Composable
fun AudioSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val manager = remember { AudioEffectsManager.getInstance(context) }

    val crossfadeMs by PlaybackTuning.crossfadeMs.collectAsStateWithLifecycle()
    val skipSilence by PlaybackTuning.skipSilence.collectAsStateWithLifecycle()
    val normalize by PlaybackTuning.normalizeVolume.collectAsStateWithLifecycle()
    val monoAudio by PlaybackTuning.monoAudio.collectAsStateWithLifecycle()
    val eqEnabled by PlaybackTuning.eqEnabled.collectAsStateWithLifecycle()
    val eqPreset by PlaybackTuning.eqPreset.collectAsStateWithLifecycle()
    val bassBoost by PlaybackTuning.bassBoost.collectAsStateWithLifecycle()
    val storedLevels by PlaybackTuning.bandLevels.collectAsStateWithLifecycle()

    var eqAvailable by remember { mutableStateOf(manager.isEqualizerAvailable()) }
    var frequencies by remember { mutableStateOf(manager.bandFrequencies()) }
    var levelRange by remember { mutableStateOf(manager.bandLevelRange()) }
    var presets by remember { mutableStateOf(manager.presetNames()) }

    // The effect instances only exist once audio has started, so poll lightly while
    // the screen is open instead of forcing a player restart.
    LaunchedEffect(eqEnabled) {
        while (!eqAvailable) {
            delay(1200)
            eqAvailable = manager.isEqualizerAvailable()
            if (eqAvailable) {
                frequencies = manager.bandFrequencies()
                levelRange = manager.bandLevelRange()
                presets = manager.presetNames()
            }
        }
    }

    val bandCount = frequencies.size
    var levels by remember(bandCount, storedLevels) { mutableStateOf(storedLevels) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(SpotifyBackground),
        contentPadding = PaddingValues(bottom = 140.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
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
                    text = "Playback",
                    color = SpotifyWhite,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        item { SectionLabel("Crossfade") }
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = if (crossfadeMs == 0) "Off" else "${crossfadeMs / 1000.0}s",
                    color = SpotifyWhite,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Slider(
                    value = crossfadeMs.toFloat(),
                    onValueChange = { PlaybackTuning.setCrossfadeMs(it.toInt()) },
                    valueRange = 0f..PlaybackTuning.MAX_CROSSFADE_MS.toFloat(),
                    steps = 23,
                    colors = SliderDefaults.colors(
                        thumbColor = SpotifyGreen,
                        activeTrackColor = SpotifyGreen,
                        inactiveTrackColor = SpotifyCard
                    )
                )
                Text(
                    text = "The tail of one track fades out while the next fades in. " +
                        "A single-player fade-through, so gapless albums are unaffected.",
                    color = SpotifyTextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        item { SectionLabel("Playback") }
        item {
            SwitchRow(
                title = "Skip silence",
                subtitle = "Jumps silent passages in spoken audio. Applies from the next playback session.",
                checked = skipSilence,
                onCheckedChange = { PlaybackTuning.setSkipSilence(it) }
            )
        }
        item {
            SwitchRow(
                title = "Normalise volume",
                subtitle = "Lifts quiet tracks with Android's loudness enhancer.",
                checked = normalize,
                onCheckedChange = {
                    PlaybackTuning.setNormalizeVolume(it)
                    manager.applyAll()
                }
            )
        }
        item {
            SwitchRow(
                title = "Mono audio",
                subtitle = "Downmixes both channels into one. Applies from the next playback session.",
                checked = monoAudio,
                onCheckedChange = { PlaybackTuning.setMonoAudio(it) }
            )
        }
        item {
            InfoRow(
                title = "Gapless playback",
                subtitle = "On. ExoPlayer joins compatible formats without a gap."
            )
        }

        item { SectionLabel("Equalizer") }
        item {
            SwitchRow(
                title = "Equalizer",
                subtitle = if (eqAvailable) {
                    "5-band tune on the live audio session."
                } else {
                    "Start a track to activate the equalizer on this device."
                },
                checked = eqEnabled,
                onCheckedChange = {
                    PlaybackTuning.setEqEnabled(it)
                    manager.applyAll()
                }
            )
        }

        if (eqEnabled && eqAvailable) {
            if (presets.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            text = "Preset",
                            color = SpotifyTextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            presets.take(4).forEachIndexed { index, name ->
                                val selected = eqPreset == index
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        PlaybackTuning.setEqPreset(index)
                                        PlaybackTuning.setBandLevels(IntArray(0))
                                        manager.applyAll()
                                    },
                                    label = {
                                        Text(
                                            text = name,
                                            fontSize = 11.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = SpotifyGreen,
                                        selectedLabelColor = SpotifyBlack,
                                        containerColor = SpotifyCard,
                                        labelColor = SpotifyWhite
                                    ),
                                    border = null
                                )
                            }
                        }
                    }
                }
            }

            if (bandCount > 0) {
                item {
                    Text(
                        text = "Bands",
                        color = SpotifyTextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 4.dp)
                    )
                }
                items(bandCount) { band ->
                    val hz = frequencies.getOrNull(band) ?: 0
                    val label = if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz"
                    val current = levels.getOrNull(band) ?: 0
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(label, color = SpotifyWhite, fontSize = 12.sp)
                            Text(
                                text = String.format(java.util.Locale.ROOT, "%+.1f dB", current / 100f),
                                color = SpotifyTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Slider(
                            value = current.toFloat(),
                            onValueChange = { value ->
                                val updated = IntArray(bandCount) { index ->
                                    if (index == band) value.toInt() else (levels.getOrNull(index) ?: 0)
                                }
                                levels = updated
                                PlaybackTuning.setBandLevels(updated)
                                manager.applyAll()
                            },
                            valueRange = levelRange.first.toFloat()..levelRange.second.toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = SpotifyGreen,
                                activeTrackColor = SpotifyGreen,
                                inactiveTrackColor = SpotifyCard
                            )
                        )
                    }
                }
            }

            item { SectionLabel("Bass") }
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        text = if (bassBoost == 0) "Off" else "${bassBoost / 10}%",
                        color = SpotifyWhite,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Slider(
                        value = bassBoost.toFloat(),
                        onValueChange = {
                            PlaybackTuning.setBassBoost(it.toInt())
                            manager.applyAll()
                        },
                        valueRange = 0f..1000f,
                        colors = SliderDefaults.colors(
                            thumbColor = SpotifyGreen,
                            activeTrackColor = SpotifyGreen,
                            inactiveTrackColor = SpotifyCard
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = SpotifyWhite,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 22.dp, bottom = 8.dp)
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = SpotifyWhite, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, color = SpotifyTextSecondary, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = SpotifyBlack,
                checkedTrackColor = SpotifyGreen,
                uncheckedThumbColor = SpotifyTextSecondary,
                uncheckedTrackColor = SpotifyCard
            )
        )
    }
}

@Composable
private fun InfoRow(title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = SpotifyWhite, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, color = SpotifyTextSecondary, fontSize = 12.sp)
        }
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = SpotifyGreen,
            modifier = Modifier.size(20.dp)
        )
    }
}
