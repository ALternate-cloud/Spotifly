package com.example.spotifly.ui.search

import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WifiOff
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
import coil.compose.AsyncImage
import com.example.spotifly.data.catalog.LibraryGrouping
import com.example.spotifly.data.catalog.SearchSuggestions
import com.example.spotifly.data.online.OnlineMusicService
import com.example.spotifly.model.Album
import com.example.spotifly.model.Artist
import com.example.spotifly.model.DownloadStatus
import com.example.spotifly.model.PlaybackState
import com.example.spotifly.model.Playlist
import com.example.spotifly.model.Song
import com.example.spotifly.theme.SpotifyBackground
import com.example.spotifly.theme.SpotifyCard
import com.example.spotifly.theme.SpotifyElevated
import com.example.spotifly.theme.SpotifyGreen
import com.example.spotifly.theme.SpotifyTextSecondary
import com.example.spotifly.theme.SpotifyWhite
import com.example.spotifly.ui.components.TrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.spotifly.theme.SpotifyBlack

private data class BrowseCategory(
    val title: String,
    val color: Color
)

private val categories = listOf(
    BrowseCategory("Pop", Color(0xFFE1306C)),
    BrowseCategory("Hip-Hop", Color(0xFFBC2A8D)),
    BrowseCategory("Rock", Color(0xFFC13584)),
    BrowseCategory("Electronic", Color(0xFF5851DB)),
    BrowseCategory("Chill", Color(0xFF405DE6)),
    BrowseCategory("Indie", Color(0xFF2E7D32)),
    BrowseCategory("Workout", Color(0xFFE65100)),
    BrowseCategory("Focus", Color(0xFF455A64))
)

private val filters = listOf("All", "Songs", "Albums", "Artists", "Playlists", "Device")

@Composable
fun SearchScreen(
    playbackState: PlaybackState,
    favorites: Set<String>,
    localSongs: List<Song>,
    modifier: Modifier = Modifier,
    recentSearches: List<String> = emptyList(),
    downloadStatus: Map<String, DownloadStatus> = emptyMap(),
    playlists: List<Playlist> = emptyList(),
    onSongClick: (Song) -> Unit,
    onPlayQueue: (List<Song>, Int) -> Unit = { _, _ -> },
    onFavoriteClick: (Song) -> Unit,
    onTrackMoreClick: ((Song) -> Unit)? = null,
    onOpenAlbum: (Album) -> Unit = {},
    onOpenArtist: (Artist) -> Unit = {},
    onOpenPodcasts: () -> Unit = {},
    onAddSearchQuery: (String) -> Unit = {},
    onRemoveSearchQuery: (String) -> Unit = {},
    onClearSearchHistory: () -> Unit = {},
    offlineMode: Boolean = false,
    initialQuery: String? = null,
    onInitialQueryConsumed: () -> Unit = {},
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    var trackResults by remember { mutableStateOf<List<Song>>(emptyList()) }
    var albumResults by remember { mutableStateOf<List<Album>>(emptyList()) }
    var artistResults by remember { mutableStateOf<List<Artist>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }

    fun performSearch(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            trackResults = emptyList()
            albumResults = emptyList()
            artistResults = emptyList()
            isSearching = false
            return
        }

        searchJob = coroutineScope.launch {
            isSearching = true
            delay(350) // Debounce typing

            val matchingLocal = localSongs.filter { LibraryGrouping.matches(it, query) }
            // Offline mode must not touch the network, so the catalogue legs are
            // skipped entirely and only downloaded/device tracks are searched.
            val (onlineTracks, onlineAlbums, onlineArtists) = if (offlineMode) {
                Triple(emptyList<Song>(), emptyList<Album>(), emptyList<Artist>())
            } else {
                coroutineScope {
                    val tracks = async {
                        val songs = OnlineMusicService.searchSongs(query) { partial ->
                            withContext(Dispatchers.Main) {
                                trackResults = (matchingLocal + partial).distinctBy { it.id }
                                isSearching = false
                            }
                        }
                        trackResults = (matchingLocal + songs).distinctBy { it.id }
                        isSearching = false
                        songs
                    }
                    val albums = async { runCatching { OnlineMusicService.searchAlbums(query) }.getOrDefault(emptyList()) }
                    val artists = async { runCatching { OnlineMusicService.searchArtists(query) }.getOrDefault(emptyList()) }
                    Triple(tracks.await(), albums.await(), artists.await())
                }
            }

            trackResults = (matchingLocal + onlineTracks).distinctBy { it.id }
            albumResults = (LibraryGrouping.albums(matchingLocal) + onlineAlbums).distinctBy { it.id }
            artistResults = (LibraryGrouping.artists(matchingLocal) + onlineArtists).distinctBy { it.id }
            isSearching = false
        }
    }

    // spotifly://search?q= opens the tab with the query already running.
    LaunchedEffect(initialQuery) {
        val query = initialQuery?.trim().orEmpty()
        if (query.isBlank()) return@LaunchedEffect
        searchQuery = query
        onAddSearchQuery(query)
        performSearch(query)
        onInitialQueryConsumed()
    }

    // Voice search: speak a song, artist or genre and it fills + runs the search
    val voiceSearchLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spokenText = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (!spokenText.isNullOrBlank()) {
            searchQuery = spokenText
            onAddSearchQuery(spokenText.trim())
            performSearch(spokenText)
        } else {
            Toast.makeText(context, "Didn't catch that — try again", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchVoiceSearch() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Search songs, artists or albums")
        }
        try {
            voiceSearchLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Voice search isn't available on this device", Toast.LENGTH_SHORT).show()
        }
    }

    // Local-only autocomplete: recent searches first, then library artists/titles.
    val suggestions = remember(searchQuery, recentSearches, localSongs) {
        SearchSuggestions.build(searchQuery, localSongs, recentSearches)
    }

    val matchingPlaylists = remember(searchQuery, playlists) {
        if (searchQuery.isBlank()) emptyList()
        else playlists.filter {
            it.name.contains(searchQuery.trim(), ignoreCase = true) ||
                it.description.contains(searchQuery.trim(), ignoreCase = true)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SpotifyBackground)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Search",
            color = SpotifyWhite,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                performSearch(it)
            },
            placeholder = {
                Text(
                    text = "What do you want to listen to?",
                    color = SpotifyTextSecondary,
                    fontSize = 14.sp
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = SpotifyTextSecondary
                )
            },
            trailingIcon = {
                Row {
                    if (searchQuery.isEmpty()) {
                        IconButton(onClick = { launchVoiceSearch() }) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Voice search",
                                tint = SpotifyGreen
                            )
                        }
                    } else {
                        IconButton(onClick = {
                            searchQuery = ""
                            trackResults = emptyList()
                            albumResults = emptyList()
                            artistResults = emptyList()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = SpotifyWhite
                            )
                        }
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    if (searchQuery.isNotBlank()) {
                        onAddSearchQuery(searchQuery.trim())
                    }
                }
            ),
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SpotifyCard,
                unfocusedContainerColor = SpotifyCard,
                focusedBorderColor = SpotifyGreen,
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = SpotifyWhite,
                unfocusedTextColor = SpotifyWhite
            ),
            modifier = Modifier.fillMaxWidth()
        )

        if (suggestions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                suggestions.forEach { suggestion ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(SpotifyCard)
                            .clickable {
                                searchQuery = suggestion
                                onAddSearchQuery(suggestion)
                                performSearch(suggestion)
                            }
                            .padding(horizontal = 11.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = SpotifyTextSecondary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = suggestion,
                            color = SpotifyWhite,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filters.forEach { filter ->
                val isSelected = selectedFilter == filter
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedFilter = filter },
                    label = {
                        Text(
                            text = filter,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
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

        Spacer(modifier = Modifier.height(14.dp))

        if (offlineMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SpotifyCard)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.WifiOff,
                    contentDescription = null,
                    tint = SpotifyTextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Offline mode: searching downloaded and device tracks only",
                    color = SpotifyTextSecondary,
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (searchQuery.isBlank()) {
            IdleSearchContent(
                recentSearches = recentSearches,
                onQueryClick = { query ->
                    searchQuery = query
                    onAddSearchQuery(query)
                    performSearch(query)
                },
                onRemoveSearchQuery = onRemoveSearchQuery,
                onClearSearchHistory = onClearSearchHistory,
                onOpenPodcasts = onOpenPodcasts
            )
        } else if (isSearching && trackResults.isEmpty() && albumResults.isEmpty() && artistResults.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 120.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = SpotifyGreen)
            }
        } else {
            SearchResultsContent(
                query = searchQuery,
                filter = selectedFilter,
                tracks = trackResults,
                albums = albumResults,
                artists = artistResults,
                playlists = matchingPlaylists,
                playbackState = playbackState,
                favorites = favorites,
                downloadStatus = downloadStatus,
                suggestions = suggestions,
                onSongClick = { song ->
                    onAddSearchQuery(searchQuery.trim())
                    onSongClick(song)
                },
                onPlayQueue = onPlayQueue,
                onFavoriteClick = onFavoriteClick,
                onTrackMoreClick = onTrackMoreClick,
                onOpenAlbum = onOpenAlbum,
                onOpenArtist = onOpenArtist,
                onPlayPlaylist = { playlist ->
                    onAddSearchQuery(searchQuery.trim())
                    onPlayQueue(playlist.songs, 0)
                },
                onSuggestionClick = { suggestion ->
                    searchQuery = suggestion
                    onAddSearchQuery(suggestion)
                    performSearch(suggestion)
                }
            )
        }
    }
}

@Composable
private fun IdleSearchContent(
    recentSearches: List<String>,
    onQueryClick: (String) -> Unit,
    onRemoveSearchQuery: (String) -> Unit,
    onClearSearchHistory: () -> Unit,
    onOpenPodcasts: () -> Unit = {}
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 120.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        if (recentSearches.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent searches",
                        color = SpotifyWhite,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = onClearSearchHistory) {
                        Text(
                            text = "Clear all",
                            color = SpotifyTextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            items(recentSearches.take(6)) { query ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onQueryClick(query) }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = SpotifyTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = query,
                            color = SpotifyWhite,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = { onRemoveSearchQuery(query) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Remove",
                            tint = SpotifyTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }

        item {
            Text(
                text = "Browse all",
                color = SpotifyWhite,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 10.dp)
            )
        }

        // Spoken audio lives beside music but plays by its own rules (resume, 15/30s
        // skips), so it gets a dedicated entry instead of a genre tile.
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E3264))
                    .clickable { onOpenPodcasts() }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = SpotifyWhite,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Podcasts",
                        color = SpotifyWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Search shows, follow them, resume episodes",
                        color = SpotifyWhite.copy(alpha = 0.75f),
                        fontSize = 12.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        items(categories.chunked(2)) { rowCategories ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                for (category in rowCategories) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(84.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(category.color)
                            .clickable { onQueryClick(category.title) }
                            .padding(12.dp)
                    ) {
                        Text(
                            text = category.title,
                            color = SpotifyWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (rowCategories.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SearchResultsContent(
    query: String,
    filter: String,
    tracks: List<Song>,
    albums: List<Album>,
    artists: List<Artist>,
    playlists: List<Playlist>,
    playbackState: PlaybackState,
    favorites: Set<String>,
    downloadStatus: Map<String, DownloadStatus>,
    suggestions: List<String>,
    onSongClick: (Song) -> Unit,
    onPlayQueue: (List<Song>, Int) -> Unit,
    onFavoriteClick: (Song) -> Unit,
    onTrackMoreClick: ((Song) -> Unit)?,
    onOpenAlbum: (Album) -> Unit,
    onOpenArtist: (Artist) -> Unit,
    onPlayPlaylist: (Playlist) -> Unit,
    onSuggestionClick: (String) -> Unit
) {
    val visibleTracks = when (filter) {
        "Albums", "Artists", "Playlists" -> emptyList()
        "Device" -> tracks.filter { it.isLocal }
        else -> tracks
    }
    val visibleAlbums = if (filter == "All" || filter == "Albums") albums else emptyList()
    val visibleArtists = if (filter == "All" || filter == "Artists") artists else emptyList()
    val visiblePlaylists = if (filter == "All" || filter == "Playlists") playlists else emptyList()

    val hasAnything = visibleTracks.isNotEmpty() || visibleAlbums.isNotEmpty() ||
        visibleArtists.isNotEmpty() || visiblePlaylists.isNotEmpty()

    if (!hasAnything) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 120.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No results found for '$query'",
                        color = SpotifyWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Check the spelling or try another keyword",
                        color = SpotifyTextSecondary,
                        fontSize = 13.sp
                    )
                }
            }

            if (suggestions.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(22.dp))
                    Text(
                        text = "Suggestions",
                        color = SpotifyTextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
                items(suggestions) { suggestion ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onSuggestionClick(suggestion) }
                            .padding(vertical = 9.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = SpotifyGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = suggestion, color = SpotifyWhite, fontSize = 14.sp)
                    }
                }
            }
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 120.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Top result comes first, exactly like the original's ranked card
        if (filter == "All" && tracks.isNotEmpty()) {
            item {
                Text(
                    text = "Top result",
                    color = SpotifyWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 10.dp)
                )
            }
            item {
                TopResultCard(
                    song = tracks.first(),
                    isPlaying = playbackState.currentSong?.id == tracks.first().id,
                    onClick = { onSongClick(tracks.first()) },
                    onPlay = { onSongClick(tracks.first()) }
                )
            }
        }

        if (visibleTracks.isNotEmpty()) {
            item {
                SectionHeader(if (filter == "Device") "On this device" else "Songs")
            }
            itemsIndexed(visibleTracks) { index, song ->
                TrackItem(
                    song = song,
                    isPlaying = playbackState.currentSong?.id == song.id,
                    isFavorite = favorites.contains(song.id),
                    isDownloaded = downloadStatus[song.id] == DownloadStatus.DOWNLOADED || song.isDownloaded,
                    isDownloading = downloadStatus[song.id] == DownloadStatus.DOWNLOADING,
                    onClick = { onSongClick(song) },
                    onFavoriteClick = { onFavoriteClick(song) },
                    onMoreClick = { onTrackMoreClick?.invoke(song) }
                )
            }
        }

        if (visibleArtists.isNotEmpty()) {
            item { SectionHeader("Artists") }
            items(visibleArtists, key = { it.id }) { artist ->
                EntityRow(
                    title = artist.name,
                    subtitle = "${artist.trackCount} songs",
                    artworkUrl = artist.artworkUrl,
                    isCircle = true,
                    onClick = { onOpenArtist(artist) }
                )
            }
        }

        if (visibleAlbums.isNotEmpty()) {
            item { SectionHeader("Albums") }
            items(visibleAlbums, key = { it.id }) { album ->
                EntityRow(
                    title = album.title,
                    subtitle = buildString {
                        append(album.displayArtist)
                        if (album.year > 0) append(" • ${album.year}")
                    },
                    artworkUrl = album.artworkUrl,
                    isCircle = false,
                    onClick = { onOpenAlbum(album) }
                )
            }
        }

        if (visiblePlaylists.isNotEmpty()) {
            item { SectionHeader("Your playlists") }
            items(visiblePlaylists, key = { it.id }) { playlist ->
                EntityRow(
                    title = playlist.name,
                    subtitle = "${playlist.songs.size} songs",
                    artworkUrl = playlist.songs.firstOrNull()?.artworkUrl,
                    isCircle = false,
                    trailingIcon = true,
                    onClick = { onPlayPlaylist(playlist) }
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        color = SpotifyWhite,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)
    )
}

@Composable
private fun TopResultCard(
    song: Song,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onPlay: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SpotifyCard)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(SpotifyElevated),
            contentAlignment = Alignment.Center
        ) {
            if (!song.artworkUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = song.artworkUrl,
                    contentDescription = song.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = SpotifyTextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = if (isPlaying) SpotifyGreen else SpotifyWhite,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = buildString {
                    append(song.artist)
                    if (song.album.isNotBlank()) append(" • ${song.album}")
                },
                color = SpotifyTextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(SpotifyGreen)
                .clickable(onClick = onPlay),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play",
                tint = SpotifyBlack,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
private fun EntityRow(
    title: String,
    subtitle: String,
    artworkUrl: String?,
    isCircle: Boolean,
    onClick: () -> Unit,
    trailingIcon: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(if (isCircle) CircleShape else RoundedCornerShape(6.dp))
                .background(SpotifyCard),
            contentAlignment = Alignment.Center
        ) {
            if (!artworkUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = artworkUrl,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = if (isCircle) Icons.Default.Person else Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = null,
                    tint = SpotifyTextSecondary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = SpotifyWhite,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = SpotifyTextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (trailingIcon) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play",
                tint = SpotifyGreen,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
