package com.example.spotifly.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.spotifly.model.Song
import com.example.spotifly.theme.SpotifyCard
import com.example.spotifly.theme.SpotifyDarkGray
import com.example.spotifly.theme.SpotifyElevated
import com.example.spotifly.theme.SpotifyGreen
import com.example.spotifly.theme.SpotifyTextSecondary
import com.example.spotifly.theme.SpotifyWhite
import kotlin.math.roundToInt

private val QUEUE_ITEM_HEIGHT = 72.dp

@Composable
fun QueueBottomSheet(
    queue: List<Song>,
    currentIndex: Int,
    modifier: Modifier = Modifier,
    manualQueue: List<Song> = emptyList(),
    autoplayQueue: List<Song> = emptyList(),
    onSongClick: (Int) -> Unit,
    onMoveQueueItem: (from: Int, to: Int) -> Unit = { _, _ -> },
    onRemoveQueueItem: (Int) -> Unit = {},
    autoplayNextId: String? = null,
    onClose: () -> Unit,
    onSaveAsPlaylist: () -> Unit,
) {
    val upcoming = remember(queue, currentIndex) {
        queue.mapIndexedNotNull { index, song -> if (index > currentIndex) index to song else null }
    }

    val upcomingKeys = remember(upcoming) {
        val seen = HashMap<String, Int>()
        upcoming.map { (_, song) ->
            val occurrence = (seen[song.id] ?: 0) + 1
            seen[song.id] = occurrence
            "upcoming_${song.id}_$occurrence"
        }
    }

    val hasCategorizedQueue = manualQueue.isNotEmpty() || autoplayQueue.isNotEmpty()

    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val itemHeightPx = with(density) { QUEUE_ITEM_HEIGHT.toPx() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(SpotifyDarkGray)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Drag handle
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(SpotifyCard)
                .align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                contentDescription = null,
                tint = SpotifyGreen,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Current Queue",
                color = SpotifyWhite,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = SpotifyWhite
                )
            }
        }

        if (queue.isNotEmpty()) {
            TextButton(onClick = onSaveAsPlaylist) {
                Text("Save as Playlist", color = SpotifyGreen)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (queue.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Queue is empty",
                    color = SpotifyTextSecondary,
                    fontSize = 15.sp
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                // 1. Now Playing Section
                item(key = "now_playing_label") {
                    Text(
                        text = "NOW PLAYING",
                        color = SpotifyGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                val nowPlaying = queue.getOrNull(currentIndex)
                if (nowPlaying != null) {
                    item(key = "now_playing_${nowPlaying.id}") {
                        TrackItem(
                            song = nowPlaying,
                            isPlaying = true,
                            onClick = { onSongClick(currentIndex) }
                        )
                    }
                }

                // 2. Next in Queue (user-added)
                if (hasCategorizedQueue) {
                    if (manualQueue.isNotEmpty()) {
                        item(key = "next_in_queue_label") {
                            Text(
                                text = "NEXT IN QUEUE",
                                color = SpotifyGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                            )
                        }

                        itemsIndexed(
                            manualQueue,
                            key = { idx, s -> "manual_${s.id}_$idx" }
                        ) { mIndex, song ->
                            val absIndex = currentIndex + 1 + mIndex
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.weight(1f)) {
                                    TrackItem(
                                        song = song,
                                        isPlaying = false,
                                        onClick = { onSongClick(absIndex) }
                                    )
                                }
                                IconButton(
                                    onClick = { onRemoveQueueItem(absIndex) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove from queue",
                                        tint = SpotifyTextSecondary.copy(alpha = 0.6f),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 3. Autoplay / Similar Songs
                    if (autoplayQueue.isNotEmpty()) {
                        item(key = "autoplay_similar_label") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = SpotifyGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "AUTOPLAY / SIMILAR SONGS",
                                    color = SpotifyGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }
                        }

                        itemsIndexed(
                            autoplayQueue,
                            key = { idx, s -> "autoplay_${s.id}_$idx" }
                        ) { aIndex, song ->
                            val absIndex = currentIndex + 1 + manualQueue.size + aIndex
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.weight(1f)) {
                                    TrackItem(
                                        song = song,
                                        isPlaying = false,
                                        onClick = { onSongClick(absIndex) }
                                    )
                                }
                                IconButton(
                                    onClick = { onRemoveQueueItem(absIndex) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove from queue",
                                        tint = SpotifyTextSecondary.copy(alpha = 0.6f),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Fallback to standard queue list when not split
                    if (autoplayNextId != null) {
                        item(key = "autoplay_hint") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = SpotifyGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Autoplay",
                                        color = SpotifyGreen,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "A similar track is queued for when this one ends",
                                        color = SpotifyTextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    if (upcoming.isNotEmpty()) {
                        item(key = "next_up_label") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "NEXT UP",
                                    color = SpotifyTextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "long-press to reorder",
                                    color = SpotifyTextSecondary.copy(alpha = 0.6f),
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                                )
                            }
                        }

                        itemsIndexed(
                            upcoming,
                            key = { index, _ -> upcomingKeys[index] }
                        ) { offset, pair ->
                            val (originalIndex, song) = pair
                            val isDragging = draggingIndex == offset

                            val currentOffset by rememberUpdatedState(offset)
                            val currentUpcoming by rememberUpdatedState(upcoming)

                            Box(
                                modifier = Modifier
                                    .zIndex(if (isDragging) 1f else 0f)
                                    .graphicsLayer {
                                        translationY = if (isDragging) dragOffset else 0f
                                        scaleX = if (isDragging) 1.03f else 1f
                                        scaleY = if (isDragging) 1.03f else 1f
                                    }
                                    .then(
                                        if (isDragging) {
                                            Modifier.shadow(12.dp, RoundedCornerShape(8.dp))
                                        } else Modifier
                                    )
                                    .background(if (isDragging) SpotifyElevated else SpotifyDarkGray)
                                    .pointerInput(currentIndex, upcoming.size) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                draggingIndex = currentOffset
                                                dragOffset = 0f
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                val from = draggingIndex ?: return@detectDragGesturesAfterLongPress
                                                dragOffset += dragAmount.y
                                                val target = (from + (dragOffset / itemHeightPx).roundToInt())
                                                    .coerceIn(0, currentUpcoming.lastIndex)
                                                if (target != from) {
                                                    onMoveQueueItem(
                                                        currentIndex + 1 + from,
                                                        currentIndex + 1 + target
                                                    )
                                                    draggingIndex = target
                                                    dragOffset -= (target - from) * itemHeightPx
                                                }
                                            },
                                            onDragEnd = {
                                                draggingIndex = null
                                                dragOffset = 0f
                                            },
                                            onDragCancel = {
                                                draggingIndex = null
                                                dragOffset = 0f
                                            }
                                        )
                                    }
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        TrackItem(
                                            song = song,
                                            isPlaying = false,
                                            onClick = { onSongClick(originalIndex) }
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.DragHandle,
                                        contentDescription = "Reorder",
                                        tint = if (isDragging) SpotifyGreen else SpotifyTextSecondary.copy(alpha = 0.5f),
                                        modifier = Modifier
                                            .padding(end = 2.dp)
                                            .size(18.dp)
                                    )
                                    IconButton(
                                        onClick = { onRemoveQueueItem(originalIndex) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove from queue",
                                            tint = SpotifyTextSecondary.copy(alpha = 0.6f),
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
