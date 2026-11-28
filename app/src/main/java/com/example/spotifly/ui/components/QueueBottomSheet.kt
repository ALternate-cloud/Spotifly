package com.example.spotifly.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.spotifly.model.Song
import com.example.spotifly.theme.Glassmorphism
import com.example.spotifly.theme.SpotifyGreen
import com.example.spotifly.theme.SpotifyGreenBright
import com.example.spotifly.theme.SpotifyTextSecondary
import com.example.spotifly.theme.SpotifyWhite
import kotlin.math.roundToInt

// 72.dp row height (52.dp art + 20.dp vertical padding) + 4.dp LazyColumn item spacing
private val QUEUE_ITEM_PITCH = 76.dp

private fun buildStableQueueKeys(prefix: String, songs: List<Song>): List<String> {
    val seen = HashMap<String, Int>(songs.size)
    return songs.map { song ->
        val occurrence = (seen[song.id] ?: 0) + 1
        seen[song.id] = occurrence
        "${prefix}_${song.id}_$occurrence"
    }
}

/**
 * Premium Dark Cinematic Queue Bottom Sheet:
 * - Fluid drag-and-drop reordering with dedicated grab handles & tactile haptics
 * - Key-stable pointer input so multi-slot drags remain unbroken across swaps
 * - Dynamic item elevation, scale, and glowing specular border during dragging
 * - Full support across manual queue, autoplay queue, and upcoming playlist tracks
 */
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
    isAutoplayEnabled: Boolean = false,
    onLoadMoreAutoplay: () -> Unit = {},
    onClose: () -> Unit,
    onSaveAsPlaylist: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val itemPitchPx = with(density) { QUEUE_ITEM_PITCH.toPx() }
    val listState = rememberLazyListState()
    val latestOnLoadMore by rememberUpdatedState(onLoadMoreAutoplay)
    val latestAutoplayEnabled by rememberUpdatedState(isAutoplayEnabled)

    LaunchedEffect(listState, queue.size, isAutoplayEnabled) {
        if (!isAutoplayEnabled) return@LaunchedEffect
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            totalItems > 0 && lastVisible >= (totalItems - 4).coerceAtLeast(0)
        }
            .distinctUntilChanged()
            .filter { it }
            .collect {
                if (latestAutoplayEnabled) {
                    latestOnLoadMore()
                }
            }
    }

    val upcoming = remember(queue, currentIndex) {
        queue.mapIndexedNotNull { index, song -> if (index > currentIndex) index to song else null }
    }

    val manualKeys = remember(manualQueue) { buildStableQueueKeys("manual", manualQueue) }
    val autoplayKeys = remember(autoplayQueue) { buildStableQueueKeys("autoplay", autoplayQueue) }
    val upcomingKeys = remember(upcoming) { buildStableQueueKeys("upcoming", upcoming.map { it.second }) }

    val hasCategorizedQueue = manualQueue.isNotEmpty() || autoplayQueue.isNotEmpty()

    var draggingScope by remember { mutableStateOf<String?>(null) }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.88f)
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(Color(0xF20C0C14))
            .border(1.dp, Glassmorphism.specularBorderBrush, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Drag Pill Handle
        Box(
            modifier = Modifier
                .width(44.dp)
                .height(4.5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0x55FFFFFF))
                .align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Header: Icon + Title + Count + Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Glassmorphism.accentGlassBrush(SpotifyGreen, 0.20f))
                    .border(1.dp, Glassmorphism.accentGlassBorderBrush(SpotifyGreen), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = null,
                    tint = SpotifyGreenBright,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Play Queue",
                    color = SpotifyWhite,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp
                )
                Text(
                    text = "${queue.size} tracks · drag handles to reorder",
                    color = SpotifyTextSecondary,
                    fontSize = 12.sp
                )
            }

            if (queue.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Glassmorphism.darkGlassBrush)
                        .border(1.dp, Glassmorphism.specularBorderBrush, RoundedCornerShape(16.dp))
                        .clickable(onClick = onSaveAsPlaylist)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BookmarkBorder,
                            contentDescription = null,
                            tint = SpotifyGreenBright,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Save",
                            color = SpotifyGreenBright,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Glassmorphism.darkGlassBrush)
                    .border(1.dp, Glassmorphism.specularBorderBrush, CircleShape)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = SpotifyWhite,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

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
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 1. Now Playing Section
                item(key = "now_playing_label") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(SpotifyGreenBright)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "NOW PLAYING",
                            color = SpotifyGreenBright,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }

                val nowPlaying = queue.getOrNull(currentIndex)
                if (nowPlaying != null) {
                    item(key = "now_playing_${nowPlaying.id}") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Glassmorphism.accentGlassBrush(SpotifyGreen, 0.14f))
                                .border(1.dp, Glassmorphism.accentGlassBorderBrush(SpotifyGreen), RoundedCornerShape(14.dp))
                        ) {
                            TrackItem(
                                song = nowPlaying,
                                isPlaying = true,
                                onClick = { onSongClick(currentIndex) }
                            )
                        }
                    }
                }

                if (hasCategorizedQueue) {
                    // 2. Next in Queue (user-added)
                    if (manualQueue.isNotEmpty()) {
                        item(key = "next_in_queue_label") {
                            Text(
                                text = "NEXT IN QUEUE",
                                color = SpotifyGreenBright,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)
                            )
                        }

                        itemsIndexed(
                            manualQueue,
                            key = { idx, _ -> manualKeys[idx] }
                        ) { mIndex, song ->
                            val absIndex = currentIndex + 1 + mIndex
                            val isDragging = draggingScope == "manual" && draggingIndex == mIndex
                            DraggableQueueTrackRow(
                                stableKey = manualKeys[mIndex],
                                song = song,
                                localIndex = mIndex,
                                lastLocalIndex = manualQueue.lastIndex,
                                isDragging = isDragging,
                                dragOffset = if (isDragging) dragOffset else 0f,
                                itemPitchPx = itemPitchPx,
                                onSelectTrack = { onSongClick(absIndex) },
                                onRemoveTrack = { onRemoveQueueItem(absIndex) },
                                onDragStart = { startIdx ->
                                    draggingScope = "manual"
                                    draggingIndex = startIdx
                                    dragOffset = 0f
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragDelta = { deltaY, latestLastIndex ->
                                    val from = draggingIndex ?: return@DraggableQueueTrackRow
                                    dragOffset += deltaY
                                    val steps = (dragOffset / itemPitchPx).roundToInt()
                                    val target = (from + steps).coerceIn(0, latestLastIndex)
                                    if (target != from) {
                                        onMoveQueueItem(currentIndex + 1 + from, currentIndex + 1 + target)
                                        draggingIndex = target
                                        dragOffset -= (target - from) * itemPitchPx
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                },
                                onDragEnd = {
                                    draggingIndex = null
                                    draggingScope = null
                                    dragOffset = 0f
                                }
                            )
                        }
                    }

                    // 3. Autoplay / Similar Songs Section
                    if (autoplayQueue.isNotEmpty()) {
                        item(key = "autoplay_similar_label") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 18.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = SpotifyGreenBright,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "AUTOPLAY / SIMILAR SONGS",
                                    color = SpotifyGreenBright,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }
                        }

                        itemsIndexed(
                            autoplayQueue,
                            key = { idx, _ -> autoplayKeys[idx] }
                        ) { aIndex, song ->
                            val absIndex = currentIndex + 1 + manualQueue.size + aIndex
                            val isDragging = draggingScope == "autoplay" && draggingIndex == aIndex
                            DraggableQueueTrackRow(
                                stableKey = autoplayKeys[aIndex],
                                song = song,
                                localIndex = aIndex,
                                lastLocalIndex = autoplayQueue.lastIndex,
                                isDragging = isDragging,
                                dragOffset = if (isDragging) dragOffset else 0f,
                                itemPitchPx = itemPitchPx,
                                onSelectTrack = { onSongClick(absIndex) },
                                onRemoveTrack = { onRemoveQueueItem(absIndex) },
                                onDragStart = { startIdx ->
                                    draggingScope = "autoplay"
                                    draggingIndex = startIdx
                                    dragOffset = 0f
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragDelta = { deltaY, latestLastIndex ->
                                    val from = draggingIndex ?: return@DraggableQueueTrackRow
                                    dragOffset += deltaY
                                    val steps = (dragOffset / itemPitchPx).roundToInt()
                                    val target = (from + steps).coerceIn(0, latestLastIndex)
                                    if (target != from) {
                                        val base = currentIndex + 1 + manualQueue.size
                                        onMoveQueueItem(base + from, base + target)
                                        draggingIndex = target
                                        dragOffset -= (target - from) * itemPitchPx
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                },
                                onDragEnd = {
                                    draggingIndex = null
                                    draggingScope = null
                                    dragOffset = 0f
                                }
                            )
                        }
                    }
                } else {
                    // Fallback to standard upcoming queue list (albums, playlists)
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
                                    tint = SpotifyGreenBright,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Autoplay Active",
                                        color = SpotifyGreenBright,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Continuous queue powered by Vibe Vector similarity",
                                        color = SpotifyTextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    if (upcoming.isNotEmpty()) {
                        item(key = "next_up_label") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 18.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "NEXT UP",
                                    color = Color(0xEEFFFFFF),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "(${upcoming.size} songs) · drag handles to reorder",
                                    color = SpotifyTextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        itemsIndexed(
                            upcoming,
                            key = { index, _ -> upcomingKeys[index] }
                        ) { offset, pair ->
                            val (originalIndex, song) = pair
                            val isDragging = draggingScope == "upcoming" && draggingIndex == offset
                            DraggableQueueTrackRow(
                                stableKey = upcomingKeys[offset],
                                song = song,
                                localIndex = offset,
                                lastLocalIndex = upcoming.lastIndex,
                                isDragging = isDragging,
                                dragOffset = if (isDragging) dragOffset else 0f,
                                itemPitchPx = itemPitchPx,
                                onSelectTrack = { onSongClick(originalIndex) },
                                onRemoveTrack = { onRemoveQueueItem(originalIndex) },
                                onDragStart = { startIdx ->
                                    draggingScope = "upcoming"
                                    draggingIndex = startIdx
                                    dragOffset = 0f
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragDelta = { deltaY, latestLastIndex ->
                                    val from = draggingIndex ?: return@DraggableQueueTrackRow
                                    dragOffset += deltaY
                                    val steps = (dragOffset / itemPitchPx).roundToInt()
                                    val target = (from + steps).coerceIn(0, latestLastIndex)
                                    if (target != from) {
                                        onMoveQueueItem(currentIndex + 1 + from, currentIndex + 1 + target)
                                        draggingIndex = target
                                        dragOffset -= (target - from) * itemPitchPx
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                },
                                onDragEnd = {
                                    draggingIndex = null
                                    draggingScope = null
                                    dragOffset = 0f
                                }
                            )
                        }
                    }
                }

                if (isAutoplayEnabled) {
                    item(key = "endless_autoplay_footer") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 14.dp, bottom = 10.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Glassmorphism.accentGlassBrush(SpotifyGreen, 0.10f))
                                .border(
                                    0.8.dp,
                                    Glassmorphism.accentGlassBorderBrush(SpotifyGreen),
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = SpotifyGreenBright,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Endless autoplay active",
                                    color = SpotifyTextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                text = "Load more similar songs",
                                color = SpotifyGreenBright,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(onClick = onLoadMoreAutoplay)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DraggableQueueTrackRow(
    stableKey: String,
    song: Song,
    localIndex: Int,
    lastLocalIndex: Int,
    isDragging: Boolean,
    dragOffset: Float,
    itemPitchPx: Float,
    onSelectTrack: () -> Unit,
    onRemoveTrack: () -> Unit,
    onDragStart: (Int) -> Unit,
    onDragDelta: (Float, Int) -> Unit,
    onDragEnd: () -> Unit
) {
    val animatedScale by animateFloatAsState(
        targetValue = if (isDragging) 1.03f else 1f,
        animationSpec = tween(150),
        label = "queueRowScale"
    )

    val latestIndex by rememberUpdatedState(localIndex)
    val latestLastIndex by rememberUpdatedState(lastLocalIndex)
    val latestOnDragStart by rememberUpdatedState(onDragStart)
    val latestOnDragDelta by rememberUpdatedState(onDragDelta)
    val latestOnDragEnd by rememberUpdatedState(onDragEnd)

    val rowModifier = if (isDragging) {
        Modifier
            .fillMaxWidth()
            .zIndex(10f)
            .graphicsLayer {
                translationY = dragOffset
                scaleX = animatedScale
                scaleY = animatedScale
            }
            .shadow(16.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF222232))
            .border(1.2.dp, Glassmorphism.accentGlassBorderBrush(SpotifyGreen), RoundedCornerShape(12.dp))
    } else {
        Modifier
            .fillMaxWidth()
            .zIndex(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(Glassmorphism.darkGlassBrush)
            .border(0.8.dp, Glassmorphism.specularBorderBrush, RoundedCornerShape(12.dp))
    }

    Box(modifier = rowModifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                TrackItem(
                    song = song,
                    isPlaying = false,
                    onClick = onSelectTrack
                )
            }

            // Key only on stableKey so pointerInput persists cleanly across item swaps
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .pointerInput(stableKey, itemPitchPx) {
                        detectDragGestures(
                            onDragStart = {
                                latestOnDragStart(latestIndex)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                latestOnDragDelta(dragAmount.y, latestLastIndex)
                            },
                            onDragEnd = { latestOnDragEnd() },
                            onDragCancel = { latestOnDragEnd() }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "Drag to reorder",
                    tint = if (isDragging) SpotifyGreenBright else Color(0xCCFFFFFF),
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = onRemoveTrack,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = Color(0x88FFFFFF),
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
        }
    }
}
