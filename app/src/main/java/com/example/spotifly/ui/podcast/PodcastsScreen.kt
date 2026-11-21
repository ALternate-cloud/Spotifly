package com.example.spotifly.ui.podcast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.spotifly.data.podcast.PodcastRepository
import com.example.spotifly.data.podcast.PodcastService
import com.example.spotifly.model.PlaybackState
import com.example.spotifly.model.PodcastEpisode
import com.example.spotifly.model.PodcastShow
import com.example.spotifly.model.Song
import com.example.spotifly.theme.SpotifyBackground
import com.example.spotifly.theme.SpotifyCard
import com.example.spotifly.theme.SpotifyGreen
import com.example.spotifly.theme.SpotifyTextSecondary
import com.example.spotifly.theme.SpotifyWhite
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.spotifly.theme.SpotifyBlack

/**
 * The podcast pillar: discover a show through the public iTunes index, follow it,
 * browse its own RSS episodes, and play them through the same queue as music with
 * resume points and longer transport skips.
 */
@Composable
fun PodcastsScreen(
    playbackState: PlaybackState,
    onPlayEpisodeQueue: (List<Song>, Int) -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val repository = remember { PodcastRepository.getInstance(context) }
    val followedShows by repository.followedShows.collectAsStateWithLifecycle()
    val progressMap by repository.episodeProgress.collectAsStateWithLifecycle()
    val completedEpisodes by repository.completedEpisodes.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PodcastShow>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var selectedShow by remember { mutableStateOf<PodcastShow?>(null) }
    var episodes by remember { mutableStateOf<List<PodcastEpisode>>(emptyList()) }
    var isLoadingEpisodes by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    fun runSearch(text: String) {
        searchJob?.cancel()
        if (text.isBlank()) {
            results = emptyList()
            isSearching = false
            return
        }
        searchJob = scope.launch {
            isSearching = true
            delay(400)
            results = PodcastService.searchShows(text)
            isSearching = false
        }
    }

    LaunchedEffect(selectedShow) {
        val show = selectedShow ?: return@LaunchedEffect
        if (episodes.isNotEmpty() && episodes.first().showId == show.id) return@LaunchedEffect
        isLoadingEpisodes = true
        episodes = PodcastService.loadEpisodes(show)
        isLoadingEpisodes = false
    }

    val show = selectedShow
    if (show != null) {
        val episodeSongs = remember(episodes) { episodes.map(PodcastService::episodeAsSong) }
        val isFollowing = followedShows.any { it.id == show.id }

        Column(
            modifier = modifier
                .fillMaxSize()
                .background(SpotifyBackground)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    selectedShow = null
                    episodes = emptyList()
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to podcasts",
                        tint = SpotifyWhite
                    )
                }
                TextButton(onClick = { repository.toggleFollow(show) }) {
                    Text(
                        text = if (isFollowing) "Following" else "Follow",
                        color = if (isFollowing) SpotifyTextSecondary else SpotifyGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PodcastArtwork(
                                url = show.artworkUrl,
                                title = show.title,
                                size = 96,
                                modifier = Modifier
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = show.title,
                                    color = SpotifyWhite,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (show.publisher.isNotBlank()) {
                                    Text(
                                        text = show.publisher,
                                        color = SpotifyTextSecondary,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = "${episodes.size} episodes",
                                    color = SpotifyTextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        if (show.description.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = show.description,
                                color = SpotifyTextSecondary,
                                fontSize = 12.sp,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                when {
                    isLoadingEpisodes -> item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = SpotifyGreen)
                        }
                    }

                    episodes.isEmpty() -> item {
                        Text(
                            text = "This feed returned no playable episodes.",
                            color = SpotifyTextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }

                    else -> itemsIndexed(episodes, key = { _, episode -> episode.id }) { index, episode ->
                        val song = episodeSongs[index]
                        EpisodeRow(
                            episode = episode,
                            isPlaying = playbackState.currentSong?.id == song.id,
                            isCompleted = completedEpisodes.contains(episode.id),
                            resumeMs = progressMap[episode.id] ?: 0L,
                            onClick = { onPlayEpisodeQueue(episodeSongs, index) }
                        )
                    }
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(SpotifyBackground),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp)
    ) {
        item {
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
                    text = "Podcasts",
                    color = SpotifyWhite,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    runSearch(it)
                },
                placeholder = {
                    Text("Search podcasts", color = SpotifyTextSecondary, fontSize = 13.sp)
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = SpotifyTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { runSearch(query) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SpotifyCard,
                    unfocusedContainerColor = SpotifyCard,
                    focusedBorderColor = SpotifyGreen,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = SpotifyWhite,
                    unfocusedTextColor = SpotifyWhite
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            )
        }

        if (isSearching) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(30.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = SpotifyGreen)
                }
            }
        }

        if (query.isBlank()) {
            if (followedShows.isNotEmpty()) {
                item { PodcastSectionTitle("Your shows") }
                items(followedShows, key = { "followed_${it.id}" }) { followed ->
                    PodcastShowRow(
                        show = followed,
                        isFollowing = true,
                        onClick = { selectedShow = followed }
                    )
                }
            }
            item {
                Column(modifier = Modifier.padding(vertical = 20.dp)) {
                    Text(
                        text = if (followedShows.isEmpty()) "Find a show to follow" else "Discover more",
                        color = SpotifyWhite,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Search the public podcast index above. Episodes stream from each show's own feed.",
                        color = SpotifyTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Technology", "Comedy", "News", "Science").forEach { topic ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                query = topic
                                runSearch(topic)
                            },
                            label = { Text(topic, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = SpotifyCard,
                                labelColor = SpotifyWhite
                            ),
                            border = null
                        )
                    }
                }
            }
            return@LazyColumn
        }

        if (results.isEmpty() && !isSearching) {
            item {
                Text(
                    text = "No shows found for \"$query\"",
                    color = SpotifyTextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }
        } else {
            item { PodcastSectionTitle("Shows") }
            items(results, key = { "result_${it.id}" }) { result ->
                PodcastShowRow(
                    show = result,
                    isFollowing = followedShows.any { it.id == result.id },
                    onClick = { selectedShow = result }
                )
            }
        }
    }
}

@Composable
private fun PodcastSectionTitle(text: String) {
    Text(
        text = text,
        color = SpotifyWhite,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun PodcastShowRow(
    show: PodcastShow,
    isFollowing: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PodcastArtwork(url = show.artworkUrl, title = show.title, size = 56, modifier = Modifier)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = show.title,
                color = SpotifyWhite,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = buildString {
                    if (show.publisher.isNotBlank()) append(show.publisher)
                    if (show.episodeCount > 0) {
                        if (isNotEmpty()) append(" • ")
                        append("${show.episodeCount} episodes")
                    }
                },
                color = SpotifyTextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isFollowing) {
            Text(
                text = "Following",
                color = SpotifyGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: PodcastEpisode,
    isPlaying: Boolean,
    isCompleted: Boolean,
    resumeMs: Long,
    onClick: () -> Unit
) {
    val progressFraction = if (episode.durationMs > 0) {
        (resumeMs.toFloat() / episode.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (isPlaying) SpotifyGreen else SpotifyCard),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isCompleted) Icons.Default.CheckCircle else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = if (isPlaying) SpotifyBlack else SpotifyWhite,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = episode.title,
                    color = if (isPlaying) SpotifyGreen else SpotifyWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        if (episode.publishedAt > 0) {
                            append(SimpleDateFormat("d MMM yyyy", Locale.US).format(Date(episode.publishedAt)))
                        }
                        if (episode.displayDuration.isNotBlank()) {
                            if (isNotEmpty()) append(" • ")
                            append(episode.displayDuration)
                        }
                        if (resumeMs > 0) {
                            if (isNotEmpty()) append(" • ")
                            append("${resumeMs / 60_000} min in")
                        }
                        if (isCompleted) {
                            if (isNotEmpty()) append(" • ")
                            append("Played")
                        }
                    },
                    color = SpotifyTextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        if (progressFraction > 0f) {
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(SpotifyCard)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressFraction)
                        .height(3.dp)
                        .background(SpotifyGreen)
                )
            }
        }
    }
}

@Composable
private fun PodcastArtwork(
    url: String?,
    title: String,
    size: Int,
    modifier: Modifier
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(SpotifyCard),
        contentAlignment = Alignment.Center
    ) {
        if (!url.isNullOrEmpty()) {
            AsyncImage(
                model = url,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = null,
                tint = SpotifyTextSecondary,
                modifier = Modifier.size((size / 2.5).dp)
            )
        }
    }
}
