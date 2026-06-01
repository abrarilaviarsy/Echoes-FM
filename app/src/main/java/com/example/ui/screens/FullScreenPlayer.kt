package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun FullScreenPlayer(
    stationName: String? = null,
    title: String,
    artist: String,
    imageUrl: String?,
    isPlaying: Boolean,
    isPreparing: Boolean,
    isFavorited: Boolean,
    songHistory: List<com.example.data.model.HistoryEntity>,
    onClearHistory: () -> Unit,
    onPlayPauseToggle: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    bitrate: Int? = null,
    codec: String? = null,
    onSkipPrevious: () -> Unit = {},
    onSkipNext: () -> Unit = {},
    dominantColor: Color = Color(0xFF121212),
    onDominantColorChange: (Color) -> Unit = {},
    isTimerActive: Boolean = false,
    remainingMinutes: Int = 0,
    onStartSleepTimer: (Int) -> Unit = {},
    onCancelSleepTimer: () -> Unit = {},
    currentLyrics: String? = null,
    isSongLiked: Boolean = false,
    onToggleLike: (String, String) -> Unit = { _, _ -> },
    likedSongs: List<com.example.data.model.LikedSong> = emptyList(),
    onToggleHistoryLike: (Long) -> Unit = {},
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val context = LocalContext.current
    var showHistorySheet by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var deepLinkEntry by remember { mutableStateOf<com.example.data.model.HistoryEntity?>(null) }
    val sheetState = rememberModalBottomSheetState()
    var selectedTab by remember { mutableStateOf(0) }

    val infiniteTransition = rememberInfiniteTransition(label = "Pulse")

    val livePulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "live_pulse_alpha"
    )

    // Dynamic state to extract dominant/muted color
    var extractedTopColor by remember { mutableStateOf<Color?>(null) }

    LaunchedEffect(imageUrl) {
        if (imageUrl.isNullOrBlank()) {
            extractedTopColor = null
        }
    }

    val animatedDominantColor by animateColorAsState(targetValue = dominantColor, animationSpec = tween(1000), label = "DominantColor")

    // Determine top ambient color (prefer extracted muted/dominant, fallback to dark slate)
    val topGradientColor = remember(animatedDominantColor, extractedTopColor) {
        val baseColor = extractedTopColor ?: animatedDominantColor
        if (baseColor == Color(0xFF121212) || baseColor == Color.Black) {
            Color(0xFF1A1F2C) // Elegant dark Slate fallback
        } else {
            baseColor
        }
    }

    // Determine bottom ambient color (matching native app background)
    val bottomGradientColor = Color(0xFF121212)

    // Determine middle ambient color (a beautiful smooth blend heavily leaning towards the dark theme background)
    val middleGradientColor = remember(topGradientColor) {
        val ratio = 0.15f // 15% of top color, 85% of deep dark background for a smooth, mud-free fade
        val blendRed = topGradientColor.red * ratio + bottomGradientColor.red * (1f - ratio)
        val blendGreen = topGradientColor.green * ratio + bottomGradientColor.green * (1f - ratio)
        val blendBlue = topGradientColor.blue * ratio + bottomGradientColor.blue * (1f - ratio)
        Color(red = blendRed, green = blendGreen, blue = blendBlue)
    }

    val animatedTopColor by animateColorAsState(targetValue = topGradientColor, animationSpec = tween(1000), label = "AnimatedTopColor")
    val animatedMiddleColor by animateColorAsState(targetValue = middleGradientColor, animationSpec = tween(1000), label = "AnimatedMiddleColor")
    val animatedBottomColor by animateColorAsState(targetValue = bottomGradientColor, animationSpec = tween(1000), label = "AnimatedBottomColor")

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121212)) // Solid, 100% opaque dark base background to completely eliminate underlying sheet bleed-through
            .background(
                Brush.verticalGradient(
                    0.0f to animatedTopColor.copy(alpha = 0.45f), // Top: subtle ambient glow
                    0.5f to animatedMiddleColor.copy(alpha = 0.25f), // Middle: extra smooth transition
                    1.0f to Color.Transparent // Bottom: guaranteed 100% pure native dark theme background
                )
            )
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, dragAmount ->
                        if (dragAmount > 50) {
                            onClose()
                        }
                    }
                }
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .testTag("full_screen_player"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Top action header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(40.dp)
                        .align(Alignment.CenterStart)
                        .testTag("player_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse Player",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(28.dp)
                    )
                }

                Text(
                    text = (stationName ?: "NOW PLAYING").uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 11.sp,
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 48.dp)
                        .basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 2000)
                        .then(
                            if (sharedTransitionScope != null && animatedVisibilityScope != null && !isPreparing) {
                                with(sharedTransitionScope) {
                                    Modifier.sharedBounds(
                                        rememberSharedContentState(key = "station_name"),
                                        animatedVisibilityScope = animatedVisibilityScope,
                                        boundsTransform = BoundsTransform { _, _ ->
                                            tween(durationMillis = 480, easing = FastOutSlowInEasing)
                                        }
                                    )
                                }
                            } else {
                                Modifier
                            }
                        )
                )

                IconButton(
                    onClick = { showHistorySheet = true },
                    modifier = Modifier
                        .size(40.dp)
                        .align(Alignment.CenterEnd)
                        .testTag("player_history_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "Song History",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 2. Main content section that toggles between Album Art (tab 0) and Lyrics (tab 1)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Crossfade(
                    targetState = selectedTab,
                    animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                    modifier = Modifier.fillMaxSize()
                ) { currentTab ->
                    if (currentTab == 0) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            // Outer static container: Handles layout bounds and static screen transition perfectly
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .padding(horizontal = 0.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                // Inner container: Box wrapper that handles dynamic shadows, background, and ensures strict corner clip throughout transitions
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .then(
                                            if (sharedTransitionScope != null && animatedVisibilityScope != null && !isPreparing) {
                                                with(sharedTransitionScope) {
                                                    Modifier.sharedBounds(
                                                        rememberSharedContentState(key = "album_art"),
                                                        animatedVisibilityScope = animatedVisibilityScope,
                                                        boundsTransform = BoundsTransform { _, _ ->
                                                            tween(durationMillis = 480, easing = FastOutSlowInEasing)
                                                        },
                                                        clipInOverlayDuringTransition = remember {
                                                            OverlayClip(RoundedCornerShape(24.dp))
                                                        },
                                                        resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds()
                                                    )
                                                }
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .clip(RoundedCornerShape(24.dp))
                                        .shadow(elevation = 16.dp, shape = RoundedCornerShape(24.dp))
                                        .background(Color(0xFF212121))
                                        .testTag("player_artwork")
                                ) {
                                    if (imageUrl.isNullOrBlank()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color(0xFF212121)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.MusicNote,
                                                contentDescription = "Generic Track Artwork",
                                                modifier = Modifier.size(100.dp),
                                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                            )
                                        }
                                    } else {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(imageUrl)
                                                .allowHardware(false)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = "Station Artwork",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                            onSuccess = { successState ->
                                                try {
                                                    val drawable = successState.result.drawable
                                                    val bitmap = if (drawable is android.graphics.drawable.BitmapDrawable) {
                                                        drawable.bitmap
                                                    } else {
                                                        null
                                                    }
                                                    if (bitmap != null && !bitmap.isRecycled) {
                                                        androidx.palette.graphics.Palette.from(bitmap).generate { palette ->
                                                            try {
                                                                val vibrantSwatch = palette?.dominantSwatch
                                                                    ?: palette?.vibrantSwatch
                                                                    ?: palette?.darkVibrantSwatch
                                                                    ?: palette?.lightVibrantSwatch
                                                                    ?: palette?.mutedSwatch
                                                                    ?: palette?.darkMutedSwatch
                                                                    ?: palette?.lightMutedSwatch
                                                                val secondarySwatch = palette?.mutedSwatch
                                                                    ?: palette?.darkMutedSwatch
                                                                    ?: palette?.dominantSwatch
                                                                    ?: palette?.darkVibrantSwatch
                                                                    ?: palette?.vibrantSwatch

                                                                vibrantSwatch?.rgb?.let { topColorInt ->
                                                                    val hsv = FloatArray(3)
                                                                    android.graphics.Color.colorToHSV(topColorInt, hsv)
                                                                    
                                                                    // Let it be colorful and vibrant while keeping text readable
                                                                    if (hsv[2] > 0.82f) {
                                                                        hsv[2] = 0.82f // Safe upper limit for readability of white overlays
                                                                    }
                                                                    hsv[2] = hsv[2].coerceIn(0.35f, 0.70f) // Keep readable and ambient (optimal dark overlay contrast)
                                                                    if (hsv[1] > 0.05f) {
                                                                        hsv[1] = hsv[1].coerceIn(0.20f, 0.80f) // Keep colors rich but not extreme/neon
                                                                    }
                                                                    
                                                                    val boostedColorInt = android.graphics.Color.HSVToColor(hsv)
                                                                    val extractedColor = Color(boostedColorInt)
                                                                    extractedTopColor = extractedColor
                                                                    onDominantColorChange(extractedColor)
                                                                }
                                                                
                                                                // Bottom and middle colors are gracefully handled dynamically in the 3-step vertical gradient
                                                                    

                                                            } catch (ex: Exception) {
                                                                ex.printStackTrace()
                                                            }
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                            }
                                        )
                                    }

                                    // Preparing overlay
                                    if (isPreparing) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.65f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                color = MaterialTheme.colorScheme.primary,
                                                strokeWidth = 4.dp,
                                                modifier = Modifier.size(64.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Lyric panel overlay - occupies the main content area with beautiful glassmorphism scroll
                        Card(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .testTag("lyrics_card"),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
                            shape = RoundedCornerShape(24.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp)
                            ) {
                                if (currentLyrics.isNullOrBlank()) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lyrics,
                                            contentDescription = "Lyrics Unavailable",
                                            tint = Color.White.copy(alpha = 0.4f),
                                            modifier = Modifier.size(64.dp)
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = "Lyrics Unavailable",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White.copy(alpha = 0.8f),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Could not find lyrics for this song.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White.copy(alpha = 0.5f),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                } else {
                                    val scrollState = rememberScrollState()
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .verticalScroll(scrollState)
                                    ) {
                                        Text(
                                            text = currentLyrics,
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontSize = 18.sp,
                                                lineHeight = 28.sp,
                                                letterSpacing = 0.5.sp,
                                                fontWeight = FontWeight.Medium
                                            ),
                                            color = Color.White,
                                            textAlign = TextAlign.Start,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Spacer(modifier = Modifier.height(24.dp))
                                        Text(
                                            text = "Lyrics provided by LRCLIB",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.5f),
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Middle Section (Metadata & Core Actions Row) - Below album art
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showSearchDialog = true },
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White,
                        textAlign = TextAlign.Start,
                        maxLines = 1,
                        modifier = Modifier
                            .testTag("player_track_title")
                            .basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 2000)
                            .then(
                                if (sharedTransitionScope != null && animatedVisibilityScope != null && !isPreparing) {
                                    with(sharedTransitionScope) {
                                        Modifier.sharedBounds(
                                            rememberSharedContentState(key = "track_title"),
                                            animatedVisibilityScope = animatedVisibilityScope,
                                            boundsTransform = BoundsTransform { _, _ ->
                                                tween(durationMillis = 480, easing = FastOutSlowInEasing)
                                            }
                                        )
                                    }
                                } else {
                                    Modifier
                                }
                            )
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = artist,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Start,
                        maxLines = 1,
                        modifier = Modifier
                            .testTag("player_track_artist")
                            .basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 2000)
                            .then(
                                if (sharedTransitionScope != null && animatedVisibilityScope != null && !isPreparing) {
                                    with(sharedTransitionScope) {
                                        Modifier.sharedBounds(
                                            rememberSharedContentState(key = "track_artist"),
                                            animatedVisibilityScope = animatedVisibilityScope,
                                            boundsTransform = BoundsTransform { _, _ ->
                                                tween(durationMillis = 480, easing = FastOutSlowInEasing)
                                            }
                                        )
                                    }
                                } else {
                                    Modifier
                                }
                            )
                    )
                }

                IconButton(
                    onClick = { onToggleLike(artist, title) },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("player_song_like_button")
                ) {
                    Icon(
                        imageVector = if (isSongLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Like Song",
                        tint = if (isSongLiked) Color.Red else Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 4. Refined Playback Info Row - Replacing Interactive SeekBar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Side: "LIVE" indicator with a subtle Apple-Music style pulsing red dot
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color.Red.copy(alpha = livePulseAlpha))
                    )
                    Text(
                        text = "LIVE",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }

                // Right Side: Format stream quality and codec dynamically with graceful fallbacks
                val displayBitrate = if (bitrate != null && bitrate > 0) bitrate else 128
                val displayCodec = if (!codec.isNullOrBlank()) codec.uppercase() else "AAC"
                Text(
                    text = "${displayBitrate} kbps • $displayCodec",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 5. Primary Playback Controls: Previous, Play/Pause, Next
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(40.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Skip Previous
                IconButton(
                    onClick = onSkipPrevious,
                    modifier = Modifier
                        .size(68.dp)
                        .testTag("player_skip_prev_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "Skip Previous",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }

                // Center Play/Pause button
                IconButton(
                    onClick = onPlayPauseToggle,
                    modifier = Modifier
                        .size(100.dp)
                        .testTag("player_play_pause_container")
                ) {
                    if (isPreparing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(56.dp),
                            strokeWidth = 4.dp,
                            color = Color.White
                        )
                    } else {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play or Pause",
                            tint = Color.White,
                            modifier = Modifier.size(76.dp)
                        )
                    }
                }

                // Skip Next
                IconButton(
                    onClick = onSkipNext,
                    modifier = Modifier
                        .size(68.dp)
                        .testTag("player_skip_next_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Skip Next",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 6. Footer (Lyrics and utility toggles) - At the very bottom
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Favorite/Star button (Station Favorite)
                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("player_favorite_button")
                ) {
                    Icon(
                        imageVector = if (isFavorited) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Toggle Favorite Station",
                        tint = if (isFavorited) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Apple-Music Quote/Lyrics icon!
                IconButton(
                    onClick = { 
                        selectedTab = if (selectedTab == 0) 1 else 0 
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .then(
                            if (selectedTab == 1) {
                                Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.18f))
                            } else {
                                Modifier
                            }
                        )
                        .testTag("player_lyrics_toggle_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Lyrics,
                        contentDescription = "Toggle Lyrics Overlay",
                        tint = if (selectedTab == 1) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Sleep Timer button
                IconButton(
                    onClick = { showSleepTimer = true },
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("sleep_timer_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Bedtime,
                            contentDescription = "Sleep Timer",
                            tint = if (isTimerActive) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(24.dp)
                        )
                        if (isTimerActive && remainingMinutes > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 6.dp, y = (-6).dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${remainingMinutes}m",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Sleep Timer Dialog ---
    if (showSleepTimer) {
        SleepTimerDialog(
            onDismiss = { showSleepTimer = false },
            onStart = { minutes ->
                onStartSleepTimer(minutes)
                showSleepTimer = false
            },
            onCancel = {
                onCancelSleepTimer()
                showSleepTimer = false
            },
            isTimerActive = isTimerActive
        )
    }

    // --- Deep Linking Modal Bottom Sheet ---
    if (deepLinkEntry != null) {
        val entry = deepLinkEntry!!
        val uriHandler = LocalUriHandler.current
        
        ModalBottomSheet(
            onDismissRequest = { deepLinkEntry = null },
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            scrimColor = Color.Black.copy(alpha = 0.8f),
            dragHandle = null
        ) {
            Surface(
                color = Color(0xFF101010),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 32.dp, start = 24.dp, end = 24.dp, top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 16.dp, bottom = 16.dp)
                            .size(width = 40.dp, height = 4.dp)
                            .background(Color.DarkGray, CircleShape)
                    )

                    Text(
                        text = "Open Song In...",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "\"${entry.title}\" by ${entry.artist}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    val query = java.net.URLEncoder.encode("${entry.artist} ${entry.title}", "UTF-8")
                    
                    // YouTube Music Button
                    Button(
                        onClick = {
                            uriHandler.openUri("https://music.youtube.com/search?q=$query")
                            deepLinkEntry = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("deeplink_ytmusic_btn"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1E1E1E),
                            contentColor = Color(0xFFFF5252)
                        ),
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "YouTube Music Icon",
                                    tint = Color(0xFFFF5252),
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = "YouTube Music",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF5252),
                                    fontSize = 16.sp
                                )
                            }
                            Text(
                                text = "Open",
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 15.sp
                            )
                        }
                    }

                    // Spotify Button
                    Button(
                        onClick = {
                            uriHandler.openUri("https://open.spotify.com/search/$query")
                            deepLinkEntry = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("deeplink_spotify_btn"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1E1E1E),
                            contentColor = Color(0xFF1DB954)
                        ),
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                             ) {
                                 Icon(
                                     imageVector = Icons.Default.MusicNote,
                                     contentDescription = "Spotify Icon",
                                     tint = Color(0xFF1DB954),
                                     modifier = Modifier.size(24.dp)
                                 )
                                 Text(
                                     text = "Spotify",
                                     fontWeight = FontWeight.Bold,
                                     color = Color(0xFF1DB954),
                                     fontSize = 16.sp
                                 )
                             }
                             Text(
                                 text = "Open",
                                 fontWeight = FontWeight.Bold,
                                 color = Color.White.copy(alpha = 0.8f),
                                 fontSize = 15.sp
                             )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    TextButton(
                        onClick = { deepLinkEntry = null },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                    ) {
                        Text(
                            text = "Cancel",
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }

    if (showHistorySheet) {
        ModalBottomSheet(
            onDismissRequest = { showHistorySheet = false },
            sheetState = sheetState,
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            scrimColor = Color.Black.copy(alpha = 0.8f),
            dragHandle = null,
            contentColor = Color.White
        ) {
            Surface(
                color = Color(0xFF101010),
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
                            .background(Color.DarkGray, CircleShape)
                    )
                    HistorySheetContent(
                        history = songHistory,
                        likedSongs = likedSongs,
                        onClearHistory = onClearHistory,
                        onDismiss = { showHistorySheet = false },
                        onItemClick = { entry ->
                            deepLinkEntry = entry
                            showHistorySheet = false
                        },
                        onToggleLike = onToggleHistoryLike
                    )
                }
            }
        }
    }

    if (showSearchDialog) {
        ExternalSearchDialog(
            searchQuery = "$title $artist",
            onDismiss = { showSearchDialog = false }
        )
    }
}

@Composable
fun HistorySheetContent(
    history: List<com.example.data.model.HistoryEntity>,
    likedSongs: List<com.example.data.model.LikedSong>,
    onClearHistory: () -> Unit,
    onDismiss: () -> Unit,
    onItemClick: (com.example.data.model.HistoryEntity) -> Unit,
    onToggleLike: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
            .background(Color.Transparent)
            .padding(16.dp)
    ) {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recently Played",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            if (history.isNotEmpty()) {
                TextButton(
                    onClick = onClearHistory,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFF2B8B5)),
                    modifier = Modifier.testTag("clear_history_button")
                ) {
                    Text("Clear All")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (history.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = "Empty History",
                        tint = Color.Gray,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No history yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Songs played on active stations will appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).testTag("history_list"),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(history) { entry ->
                    val isLiked = likedSongs.any {
                        it.artist.equals(entry.artist, ignoreCase = true) &&
                        it.title.equals(entry.title, ignoreCase = true)
                    }
                    HistoryItem(
                        entry = entry,
                        isLiked = isLiked,
                        onItemLikeToggle = { onToggleLike(entry.id) },
                        onClick = { onItemClick(entry) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("close_history_button"),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("Close", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun HistoryItem(
    entry: com.example.data.model.HistoryEntity,
    isLiked: Boolean,
    onItemLikeToggle: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF222222))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!entry.coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(entry.coverUrl)
                        .allowHardware(false)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Album Art",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .align(Alignment.CenterVertically)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .align(Alignment.CenterVertically),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Played Song",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically)
            ) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = entry.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.LightGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onItemLikeToggle,
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .testTag("history_like_btn_${entry.id}")
            ) {
                Icon(
                    imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (isLiked) "Unlike Song" else "Like Song",
                    tint = if (isLiked) Color(0xFFE91E63) else Color.Gray,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = formatRelativeTime(entry.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
        }
    }
}

fun formatRelativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        seconds < 60 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        else -> "${days}d ago"
    }
}

fun searchSongInExternalApp(context: android.content.Context, query: String, target: String) {
    val encodedQuery = try {
        java.net.URLEncoder.encode(query, "UTF-8")
    } catch (e: Exception) {
        query
    }
    
    val uriString = if (target.lowercase() == "spotify") {
        "spotify:search:$encodedQuery"
    } else {
        "https://music.youtube.com/search?q=$encodedQuery"
    }
    
    try {
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(uriString)).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        if (target.lowercase() == "spotify") {
            try {
                val fallbackUri = "https://open.spotify.com/search/$encodedQuery"
                val fallbackIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(fallbackUri)).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            } catch (fallbackEx: Exception) {
                android.widget.Toast.makeText(context, "Could not open Spotify search", android.widget.Toast.LENGTH_SHORT).show()
            }
        } else {
            android.widget.Toast.makeText(context, "Could not open YouTube Music search", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExternalSearchDialog(searchQuery: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = 0.8f),
        dragHandle = null
    ) {
        Surface(
            color = Color(0xFF101010),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 32.dp, start = 24.dp, end = 24.dp, top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 16.dp, bottom = 16.dp)
                        .size(width = 40.dp, height = 4.dp)
                        .background(Color.DarkGray, CircleShape)
                )

                Text(
                    text = "Search Song In...",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Search for \"$searchQuery\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))

                // YouTube Music button
                Button(
                    onClick = {
                        searchSongInExternalApp(context, searchQuery, "youtube")
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("search_ytmusic_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1E1E1E),
                        contentColor = Color(0xFFFF5252)
                    ),
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "YouTube Music Icon",
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "YouTube Music",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF5252),
                                fontSize = 16.sp
                            )
                        }
                        Text(
                            text = "Search",
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 15.sp
                        )
                    }
                }

                // Spotify Button
                Button(
                    onClick = {
                        searchSongInExternalApp(context, searchQuery, "spotify")
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("search_spotify_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1E1E1E),
                        contentColor = Color(0xFF1DB954)
                    ),
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = "Spotify Icon",
                                tint = Color(0xFF1DB954),
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Spotify",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1DB954),
                                fontSize = 16.sp
                            )
                        }
                        Text(
                            text = "Search",
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                ) {
                    Text(
                        text = "Cancel",
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}
