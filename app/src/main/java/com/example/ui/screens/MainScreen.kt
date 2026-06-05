package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.LikedSong
import com.example.ui.viewmodel.RadioViewModel
import com.example.ui.viewmodel.FmStreamViewModel
import com.example.ui.screens.FmStreamSearchScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavGraph.Companion.findStartDestination

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: RadioViewModel,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "favorites"
    val selectedTab = when (currentRoute) {
        "favorites" -> 0
        "radio_browser" -> 1
        "fm_stream" -> 2
        "soma_fm" -> 3
        else -> 0
    }
    var currentView by remember { mutableStateOf("main") }
    var showFullScreenPlayer by remember { mutableStateOf(false) }
    var showCustomUrlDialog by remember { mutableStateOf(false) }
    var showBackupRestoreDialog by remember { mutableStateOf(false) }
    var customUrlInput by remember { mutableStateOf("") }
    var dominantColor by remember { mutableStateOf(Color(0xFF121212)) }
    var showLikedSongsSheet by remember { mutableStateOf(false) }
    var deepLinkLikedSong by remember { mutableStateOf<LikedSong?>(null) }

    // Collect flow states reactively
    val somaChannels by viewModel.somaChannels.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val isLoadingSoma by viewModel.isLoadingSoma.collectAsStateWithLifecycle()

    val nowPlaying by viewModel.nowPlayingItem.collectAsStateWithLifecycle()
    val currentStation by viewModel.currentPlayingStation.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val isPreparing by viewModel.isPreparing.collectAsStateWithLifecycle()
    val streamMetadata by viewModel.streamMetadata.collectAsStateWithLifecycle()
    val isCurrentStationFavorited by viewModel.isCurrentStationFavorited.collectAsStateWithLifecycle()
    val historyList by viewModel.trackHistory.collectAsState()
    val currentSongInfo by viewModel.currentSongInfo.collectAsStateWithLifecycle()
    val isTimerActive by viewModel.isTimerActive.collectAsStateWithLifecycle()
    val remainingMinutes by viewModel.remainingMinutes.collectAsStateWithLifecycle()
    val currentLyrics by viewModel.currentLyrics.collectAsStateWithLifecycle()
    val currentArtworkUrl by viewModel.currentArtworkUrl.collectAsStateWithLifecycle()
    val likedSongs by viewModel.likedSongs.collectAsStateWithLifecycle()
    val isSongLiked by viewModel.isCurrentSongLiked.collectAsStateWithLifecycle()
    val expandPlayerTrigger by viewModel.expandPlayerTrigger.collectAsStateWithLifecycle()

    val currentStationName = currentStation?.name ?: "Internet Radio"
    val currentTitle = currentSongInfo?.title ?: streamMetadata?.title ?: nowPlaying?.mediaMetadata?.title?.toString() ?: "Unknown Station"
    val currentArtist = currentSongInfo?.artist ?: streamMetadata?.artist ?: "Live Broadcast"

    LaunchedEffect(expandPlayerTrigger) {
        if (expandPlayerTrigger) {
            showFullScreenPlayer = true
            viewModel.resetExpandPlayerTrigger()
        }
    }

    LaunchedEffect(currentArtworkUrl) {
        if (currentArtworkUrl.isNullOrBlank()) {
            dominantColor = Color(0xFF121212)
        }
    }

    BackHandler(enabled = showFullScreenPlayer) {
        showFullScreenPlayer = false
    }

    BackHandler(enabled = currentView == "liked_songs") {
        currentView = "main"
    }

    @OptIn(ExperimentalSharedTransitionApi::class)
    SharedTransitionLayout {
        Box(modifier = Modifier.fillMaxSize()) {
            if (currentView == "liked_songs") {
                LikedSongsScreen(
                    likedSongs = likedSongs,
                    onBack = { currentView = "main" },
                    onUnlikeSong = { artist, title -> viewModel.toggleSongLike(artist, title, null) }
                )
            } else {
                Scaffold(
                    modifier = modifier.fillMaxSize(),
                    containerColor = Color.Black,
                    bottomBar = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                        ) {
                            // Main navigation layout in Elegant Dark styled matching navigation specifications
                            NavigationBar(
                                containerColor = Color(0xFF121212),
                                tonalElevation = 0.dp
                            ) {
                                NavigationBarItem(
                                    selected = selectedTab == 0,
                                    onClick = {
                                        navController.navigate("favorites") {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = "Favorites",
                                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    icon = { Icon(Icons.Default.Favorite, contentDescription = "Favorites") },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primary,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    ),
                                    modifier = Modifier.testTag("nav_tab_favorites")
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 1,
                                    onClick = {
                                        navController.navigate("radio_browser") {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = "Radio Browser",
                                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    icon = { Icon(Icons.Default.Radio, contentDescription = "Radio Browser") },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primary,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    ),
                                    modifier = Modifier.testTag("nav_tab_radio_browser")
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 2,
                                    onClick = {
                                        navController.navigate("fm_stream") {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = "FM Stream",
                                            fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    icon = { Icon(Icons.Filled.QueueMusic, contentDescription = "FM Stream") },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primary,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    ),
                                    modifier = Modifier.testTag("nav_tab_fm_stream")
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 3,
                                    onClick = {
                                        navController.navigate("soma_fm") {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = "Soma FM",
                                            fontWeight = if (selectedTab == 3) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    icon = { Icon(Icons.Default.List, contentDescription = "Soma FM") },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primary,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    ),
                                    modifier = Modifier.testTag("nav_tab_soma_fm")
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(colors = listOf(Color(0xFF1E1026), Color(0xFF0A050D), Color.Black), startY = 0f, endY = 900f))
                            .padding(
                                top = 0.dp,
                                bottom = innerPadding.calculateBottomPadding()
                            )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                        ) {
                            // Elegant top branding and info bar matching header specs
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                                    .padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = when (selectedTab) {
                                            0 -> "My Favorites"
                                            1 -> "Radio Browser"
                                            2 -> "FM Stream"
                                            3 -> "SomaFM Channels"
                                            else -> "My Favorites"
                                        },
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { showCustomUrlDialog = true },
                                        modifier = Modifier.testTag("custom_url_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Link,
                                            contentDescription = "Play Custom URL",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { showBackupRestoreDialog = true },
                                        modifier = Modifier.testTag("backup_restore_menu_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = "Backup & Restore",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                            }

                            Box(modifier = Modifier.weight(1f)) {
                                NavHost(
                                    navController = navController,
                                    startDestination = "favorites",
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    composable("favorites") {
                                        FavoritesTab(
                                            favorites = favorites,
                                            likedSongsCount = likedSongs.size,
                                            onNavigateToLikedSongs = { currentView = "liked_songs" },
                                            onFavoritesReordered = { newList -> viewModel.updateFavoriteStationsOrder(newList) },
                                            onPlay = { favoriteItem ->
                                                viewModel.playStation(
                                                    streamUrl = favoriteItem.streamUrl,
                                                    name = favoriteItem.name,
                                                    imageUrl = favoriteItem.faviconUrl,
                                                    bitrate = favoriteItem.bitrate,
                                                    codec = favoriteItem.codec,
                                                    highResUrl = favoriteItem.highResUrl,
                                                    lowResUrl = favoriteItem.lowResUrl
                                                )
                                            },
                                            onRemoveFavorite = { fav -> viewModel.toggleFavorite(fav) },
                                            onRenameStation = { id, name -> viewModel.updateFavoriteName(id, name) }
                                        )
                                    }
                                    composable("radio_browser") {
                                        val radioBrowserViewModel: com.example.ui.viewmodel.RadioBrowserViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                                        val stations by radioBrowserViewModel.radioBrowserStations.collectAsStateWithLifecycle()
                                        val isLoading by radioBrowserViewModel.isLoadingRadioBrowser.collectAsStateWithLifecycle()
                                        val radioBrowserSearchQuery by radioBrowserViewModel.searchQuery.collectAsStateWithLifecycle()
                                        val currentOrder by radioBrowserViewModel.currentOrder.collectAsStateWithLifecycle()
                                        val favs by viewModel.favorites.collectAsStateWithLifecycle()
                                        var showCountrySheetLocal by remember { mutableStateOf(false) }

                                        RadioBrowserTab(
                                            stations = stations,
                                            favorites = favs,
                                            searchQuery = radioBrowserSearchQuery,
                                            isLoading = isLoading,
                                            onSearch = { query -> radioBrowserViewModel.searchRadioBrowser(query) },
                                            onSearchByTag = { tag -> radioBrowserViewModel.searchByTag(tag) },
                                            onPlay = { station ->
                                                viewModel.playStation(
                                                    streamUrl = station.url_resolved ?: station.url,
                                                    name = station.name,
                                                    imageUrl = station.favicon,
                                                    bitrate = station.bitrate,
                                                    codec = station.codec,
                                                    highResUrl = station.highResUrl,
                                                    lowResUrl = station.lowResUrl
                                                )
                                            },
                                            onToggleFavorite = { fav -> radioBrowserViewModel.toggleFavorite(fav) },
                                            onBrowseCountriesClick = {
                                                radioBrowserViewModel.fetchCountries()
                                                showCountrySheetLocal = true
                                            },
                                            currentOrder = currentOrder,
                                            onOrderChanged = { order -> radioBrowserViewModel.searchRadioBrowser(radioBrowserSearchQuery, order) }
                                        )

                                        if (showCountrySheetLocal) {
                                            ModalBottomSheet(
                                                onDismissRequest = { showCountrySheetLocal = false },
                                                containerColor = Color.Transparent,
                                                tonalElevation = 0.dp,
                                                scrimColor = Color.Black.copy(alpha = 0.8f),
                                                dragHandle = null
                                            ) {
                                                Surface(
                                                    color = Color(0xFF121212),
                                                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Column(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalAlignment = Alignment.CenterHorizontally
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .align(Alignment.CenterHorizontally)
                                                                .padding(top = 16.dp, bottom = 16.dp)
                                                                .size(width = 40.dp, height = 4.dp)
                                                                .background(Color.DarkGray, RoundedCornerShape(50.dp))
                                                        )
                                                        CountryListScreen(
                                                            viewModel = radioBrowserViewModel,
                                                            onCountrySelected = { country ->
                                                                showCountrySheetLocal = false
                                                            },
                                                            modifier = Modifier.fillMaxHeight(0.7f).background(Color.Transparent)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    composable("soma_fm") {
                                        val localRadioViewModel = viewModel
                                        SomaFmTab(
                                            channels = somaChannels,
                                            favorites = favorites,
                                            isLoading = isLoadingSoma,
                                            onPlay = { channel ->
                                                localRadioViewModel.playStation(
                                                    streamUrl = "https://ice1.somafm.com/${channel.id}-128-mp3",
                                                    name = channel.title,
                                                    imageUrl = channel.xlimage ?: channel.image,
                                                    bitrate = 128,
                                                    codec = "mp3",
                                                    highResUrl = channel.highResUrl,
                                                    lowResUrl = channel.lowResUrl
                                                )
                                            },
                                            onToggleFavorite = { fav -> localRadioViewModel.toggleFavorite(fav) }
                                        )
                                    }
                                    composable("fm_stream") {
                                        val screenFmStreamViewModel: FmStreamViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                                        val fmSearchQuery by screenFmStreamViewModel.searchQuery.collectAsStateWithLifecycle()
                                        val fmSearchState by screenFmStreamViewModel.searchState.collectAsStateWithLifecycle()
                                        FmStreamSearchScreen(
                                            searchQuery = fmSearchQuery,
                                            searchState = fmSearchState,
                                            onSearchQueryChange = { query -> screenFmStreamViewModel.setSearchQuery(query) },
                                            onSearchTriggered = { query -> screenFmStreamViewModel.search(query) },
                                            onPlay = { station, streamOption ->
                                                viewModel.playStation(
                                                    streamUrl = streamOption.url,
                                                    name = station.name,
                                                    imageUrl = station.imageUrl,
                                                    bitrate = streamOption.bitrate.filter { it.isDigit() }.toIntOrNull(),
                                                    codec = station.codec
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Floating MiniPlayer overlay sitting beautifully transparent without any black clipping
                        AnimatedVisibility(
                            visible = !showFullScreenPlayer && (nowPlaying != null || currentStationName != "Internet Radio"),
                            enter = slideInVertically(
                                animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
                                initialOffsetY = { it }
                            ) + fadeIn(animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing)),
                            exit = slideOutVertically(
                                animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
                                targetOffsetY = { it }
                            ) + fadeOut(animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing)),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 8.dp)
                                .padding(bottom = 8.dp)
                        ) {
                            NowPlayingBar(
                                stationName = currentStationName,
                                title = currentTitle,
                                artist = currentArtist,
                                imageUrl = currentArtworkUrl,
                                bitrate = streamMetadata?.bitrate,
                                codec = streamMetadata?.codec,
                                nowPlaying = nowPlaying,
                                isPlaying = isPlaying,
                                isPreparing = isPreparing,
                                onTogglePlayPause = { viewModel.togglePlayPause() },
                                onBarClick = { showFullScreenPlayer = true },
                                dominantColor = dominantColor,
                                onDominantColorChange = { dominantColor = it },
                                sharedTransitionScope = this@SharedTransitionLayout,
                                animatedVisibilityScope = this@AnimatedVisibility
                            )
                        }
                    }
                }
            }
        }

        // Custom URL dialog
        if (showCustomUrlDialog) {
            AlertDialog(
                onDismissRequest = {
                    showCustomUrlDialog = false
                    customUrlInput = ""
                },
                title = {
                    Text(
                        text = "Stream from URL",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Paste a direct audio stream link below to start listening instantly.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        OutlinedTextField(
                            value = customUrlInput,
                            onValueChange = { customUrlInput = it },
                            label = { Text("Stream URL") },
                            placeholder = { Text("https://example.com/stream.mp3") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Link,
                                    contentDescription = "Link Icon"
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("custom_url_input"),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            )
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val url = customUrlInput.trim()
                            if (url.startsWith("http://") || url.startsWith("https://")) {
                                viewModel.playStation(
                                    streamUrl = url,
                                    name = "Custom Stream",
                                    imageUrl = null
                                )
                                showCustomUrlDialog = false
                                customUrlInput = ""
                            }
                        },
                        enabled = customUrlInput.trim().startsWith("http://") || customUrlInput.trim().startsWith("https://")
                    ) {
                        Text("Play")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showCustomUrlDialog = false
                            customUrlInput = ""
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Backup & Restore Bottom Sheet
        if (showBackupRestoreDialog) {
            ModalBottomSheet(
                onDismissRequest = { showBackupRestoreDialog = false },
                containerColor = Color.Transparent,
                tonalElevation = 0.dp,
                scrimColor = Color.Black.copy(alpha = 0.8f),
                dragHandle = null
            ) {
                Surface(
                    color = Color(0xFF121212),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(top = 16.dp, bottom = 16.dp)
                                .size(width = 40.dp, height = 4.dp)
                                .background(Color.DarkGray, RoundedCornerShape(50.dp))
                        )
                        BackupRestoreSection(
                            viewModel = viewModel,
                            onComplete = { showBackupRestoreDialog = false },
                            modifier = Modifier.background(Color.Transparent)
                        )
                    }
                }
            }
        }

        // Full-Screen overlay
        AnimatedVisibility(
            visible = showFullScreenPlayer && nowPlaying != null,
            enter = slideInVertically(
                animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
                initialOffsetY = { it }
            ) + fadeIn(animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing)),
            exit = slideOutVertically(
                animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
                targetOffsetY = { it }
            ) + fadeOut(animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing)),
            modifier = Modifier.fillMaxSize()
        ) {
            FullScreenPlayer(
                stationName = currentStationName,
                title = currentTitle,
                artist = currentArtist,
                bitrate = streamMetadata?.bitrate,
                codec = streamMetadata?.codec,
                imageUrl = currentArtworkUrl,
                isPlaying = isPlaying,
                isPreparing = isPreparing,
                isFavorited = isCurrentStationFavorited,
                songHistory = historyList,
                onClearHistory = { viewModel.clearHistory() },
                onPlayPauseToggle = { viewModel.togglePlayPause() },
                onFavoriteToggle = { viewModel.toggleFavoriteCurrent() },
                onClose = { showFullScreenPlayer = false },
                onSkipPrevious = { viewModel.skipPrevious() },
                onSkipNext = { viewModel.skipNext() },
                dominantColor = dominantColor,
                onDominantColorChange = { dominantColor = it },
                isTimerActive = isTimerActive,
                remainingMinutes = remainingMinutes,
                onStartSleepTimer = { viewModel.startSleepTimer(it) },
                onCancelSleepTimer = { viewModel.cancelSleepTimer() },
                currentLyrics = currentLyrics,
                isSongLiked = isSongLiked,
                onToggleLike = { artist, title -> viewModel.toggleSongLike(artist, title, currentArtworkUrl) },
                likedSongs = likedSongs,
                onToggleHistoryLike = { historyItemId -> viewModel.toggleHistoryItemLikeStatus(historyItemId) },
                sharedTransitionScope = this@SharedTransitionLayout,
                animatedVisibilityScope = this@AnimatedVisibility
            )
        }
    }
}