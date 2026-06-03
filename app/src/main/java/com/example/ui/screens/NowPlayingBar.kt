package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import coil.compose.AsyncImage
import coil.request.ImageRequest

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun NowPlayingBar(
    stationName: String = "NOW PLAYING",
    title: String,
    artist: String,
    nowPlaying: MediaItem?,
    isPlaying: Boolean,
    isPreparing: Boolean,
    onTogglePlayPause: () -> Unit,
    onBarClick: () -> Unit,
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    bitrate: Int? = null,
    codec: String? = null,
    dominantColor: Color = Color(0xFF121212),
    onDominantColorChange: (Color) -> Unit = {},
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    onVerticalDrag: ((Float) -> Unit)? = null,
    onDragEnd: ((Float) -> Unit)? = null
) {
    val artworkData: Any? = if (!imageUrl.isNullOrBlank()) imageUrl else nowPlaying?.mediaMetadata?.artworkUri
    val animatedBgColor by animateColorAsState(targetValue = dominantColor, animationSpec = tween(1000), label = "MiniPlayerBg")

    // Determine dynamic background color (prefer vibrant/dominant, fallback to Spotify-like dark grey)
    val finalCardBgColor = remember(animatedBgColor) {
        if (animatedBgColor == Color(0xFF121212) || animatedBgColor == Color.Black) {
            Color(0xFF1A1F2C) // Cohesive elegant fallback matching FullScreenPlayer
        } else {
            // Solid dark base (Color(0xFF121212)) blended with a very subtle 15% of the extracted color
            val ratio = 0.15f
            val blendRed = animatedBgColor.red * ratio + 0.07058824f * (1f - ratio)
            val blendGreen = animatedBgColor.green * ratio + 0.07058824f * (1f - ratio)
            val blendBlue = animatedBgColor.blue * ratio + 0.07058824f * (1f - ratio)
            Color(red = blendRed, green = blendGreen, blue = blendBlue)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(finalCardBgColor)
            .pointerInput(onVerticalDrag, onDragEnd) {
                var totalDrag = 0f
                detectVerticalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onDragEnd = {
                        if (onDragEnd != null) {
                            onDragEnd(totalDrag)
                        } else {
                            if (totalDrag < -50f) {
                                onBarClick()
                            }
                        }
                    },
                    onDragCancel = {
                        if (onDragEnd != null) {
                            onDragEnd(0f)
                        }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        totalDrag += dragAmount
                        if (onVerticalDrag != null) {
                            onVerticalDrag(dragAmount)
                        } else {
                            if (totalDrag < -50f) {
                                onBarClick()
                                totalDrag = 0f
                            }
                        }
                    }
                )
            }
            .clickable { onBarClick() }
            .testTag("now_playing_panel"),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Artwork Logo
            Box(
                modifier = Modifier
                    .size(52.dp)
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
                                        OverlayClip(RoundedCornerShape(8.dp))
                                    },
                                    resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds()
                                )
                            }
                        } else {
                            Modifier
                        }
                    )
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(artworkData)
                        .allowHardware(false)
                        .crossfade(true)
                        .build(),
                    placeholder = null,
                    error = null,
                    fallback = null,
                    contentDescription = "Station Logo",
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
                                        val topSwatch = palette?.dominantSwatch
                                            ?: palette?.vibrantSwatch
                                            ?: palette?.darkVibrantSwatch
                                            ?: palette?.lightVibrantSwatch
                                            ?: palette?.mutedSwatch
                                            ?: palette?.darkMutedSwatch
                                            ?: palette?.lightMutedSwatch
                                        
                                        topSwatch?.rgb?.let { topColorInt ->
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
                                            onDominantColorChange(Color(boostedColorInt))
                                        }
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

            Spacer(modifier = Modifier.width(10.dp))

            // Text Details
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 6.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stationName.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 4.dp)
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
                    val qualityText = if (bitrate != null && bitrate > 0) {
                        "$bitrate kbps • ${codec?.uppercase() ?: "UNKNOWN"}"
                    } else if (!codec.isNullOrBlank()) {
                        codec.uppercase()
                    } else {
                        null
                    }
                    if (qualityText != null) {
                        Text(
                            text = qualityText,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = Color.White.copy(alpha = 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    ),
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier
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
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = if (isPreparing) "Connecting stream..." else artist,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    modifier = Modifier
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

            // Loading Spinner OR Play/Pause Trigger inside IconButton
            IconButton(
                onClick = onTogglePlayPause,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("play_pause_button")
            ) {
                if (isPreparing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}
