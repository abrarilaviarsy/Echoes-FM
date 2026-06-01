package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.FavoriteStation

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FavoritesTab(
    favorites: List<FavoriteStation>,
    likedSongsCount: Int,
    onNavigateToLikedSongs: () -> Unit,
    onFavoritesReordered: (List<FavoriteStation>) -> Unit,
    onPlay: (FavoriteStation) -> Unit,
    onRemoveFavorite: (FavoriteStation) -> Unit,
    onRenameStation: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showEditDialog by remember { mutableStateOf(false) }
    var selectedStationId by remember { mutableStateOf("") }
    var editingName by remember { mutableStateOf("") }

    val density = LocalDensity.current.density
    val mutableFavorites = remember(favorites) {
        mutableStateListOf<FavoriteStation>().apply {
            addAll(favorites)
        }
    }

    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }

    val listState = rememberLazyListState()

    LaunchedEffect(draggedIndex, dragOffset) {
        val index = draggedIndex ?: return@LaunchedEffect
        while (true) {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            val draggedItem = visibleItems.find { it.index == index + 1 }
            if (draggedItem != null) {
                val densityVal = density
                val currentTop = draggedItem.offset + (dragOffset * densityVal)
                val currentBottom = currentTop + draggedItem.size
                
                val viewportHeight = layoutInfo.viewportEndOffset
                val scrollThreshold = 100 * densityVal // 100dp threshold
                
                var scrollAmount = 0f
                if (currentTop < scrollThreshold && listState.canScrollBackward) {
                    val intensity = (scrollThreshold - currentTop) / scrollThreshold
                    scrollAmount = -15f * densityVal * intensity.coerceIn(0.1f, 1f)
                } else if (currentBottom > (viewportHeight - scrollThreshold) && listState.canScrollForward) {
                    val intensity = (currentBottom - (viewportHeight - scrollThreshold)) / scrollThreshold
                    scrollAmount = 15f * densityVal * intensity.coerceIn(0.1f, 1f)
                }
                
                if (scrollAmount != 0f) {
                    listState.scrollBy(scrollAmount)
                    dragOffset += scrollAmount / densityVal
                    kotlinx.coroutines.delay(16) // ~60fps scroll loop
                } else {
                    break
                }
            } else {
                break
            }
        }
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = {
                showEditDialog = false
                selectedStationId = ""
                editingName = ""
            },
            title = {
                Text(
                    text = "Rename Station",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter a new name for this station:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    OutlinedTextField(
                        value = editingName,
                        onValueChange = { editingName = it },
                        label = { Text("Station Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_station_name_input"),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            cursorColor = MaterialTheme.colorScheme.primary,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (editingName.isNotBlank()) {
                            onRenameStation(selectedStationId, editingName.trim())
                            showEditDialog = false
                            selectedStationId = ""
                            editingName = ""
                        }
                    },
                    enabled = editingName.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showEditDialog = false
                        selectedStationId = ""
                        editingName = ""
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)
    ) {
        // Prominent Spotify-Style "Liked Songs" Card
        item {
            Card(
                onClick = onNavigateToLikedSongs,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .testTag("liked_songs_playlist_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Transparent
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF4C00D1).copy(alpha = 0.4f),
                                    Color(0xFF161616)
                                )
                            )
                        )
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF8C34FF), Color(0xFF4C00D1))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Liked Songs Icon",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Liked Songs",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Playlist • $likedSongsCount songs",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Open Liked Songs",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        if (favorites.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillParentMaxSize()
                        .padding(horizontal = 16.dp, vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.HeartBroken,
                            contentDescription = "No Favorites",
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No saved favorites",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tap the heart icon on any station in Radio Browser or SomaFM to add it here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            itemsIndexed(mutableFavorites, key = { _, station -> station.id }) { index, station ->
                val isDragged = (draggedIndex == index)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            translationY = if (isDragged) dragOffset * density else 0f
                            scaleX = if (isDragged) 1.05f else 1f
                            scaleY = if (isDragged) 1.05f else 1f
                            shadowElevation = if (isDragged) 16f else 0f
                        }
                        .zIndex(if (isDragged) 10f else 1f)
                        .clip(RoundedCornerShape(20.dp))
                        .combinedClickable(
                            onClick = { onPlay(station) },
                            onLongClick = {
                                selectedStationId = station.id
                                editingName = station.name
                                showEditDialog = true
                            }
                        )
                        .testTag("favorite_card_${station.id}"),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(
                        width = if (isDragged) 2.dp else 1.dp,
                        color = if (isDragged) MaterialTheme.colorScheme.primary else Color(0x3349454F)
                    ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDragged) Color(0xFF1C1C1D) else Color(0xFF121212)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Radio,
                                contentDescription = "Favorite station fallback icon",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )

                            if (!station.faviconUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(station.faviconUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "${station.name} Logo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Transparent)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = station.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = station.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (station.sourceType == "soma_fm") "SomaFM" else "Radio Browser",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = { onRemoveFavorite(station) },
                            modifier = Modifier.testTag("remove_fav_button_${station.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = "Unfavorite Station",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .pointerInput(station.id) {
                                    detectDragGestures(
                                        onDragStart = {
                                            val curIndex = mutableFavorites.indexOfFirst { it.id == station.id }
                                            if (curIndex != -1) {
                                                draggedIndex = curIndex
                                                dragOffset = 0f
                                            }
                                        },
                                        onDrag = { change, dragAmount ->
                                            val curIndex = draggedIndex
                                            if (curIndex != null) {
                                                change.consume()
                                                dragOffset += dragAmount.y / density

                                                val currentDragOffsetY = dragOffset
                                                val cardHeightDp = 90f

                                                if (currentDragOffsetY > cardHeightDp && curIndex < mutableFavorites.size - 1) {
                                                    val temp = mutableFavorites[curIndex]
                                                    mutableFavorites[curIndex] = mutableFavorites[curIndex + 1]
                                                    mutableFavorites[curIndex + 1] = temp
                                                    draggedIndex = curIndex + 1
                                                    dragOffset -= cardHeightDp
                                                } else if (currentDragOffsetY < -cardHeightDp && curIndex > 0) {
                                                    val temp = mutableFavorites[curIndex]
                                                    mutableFavorites[curIndex] = mutableFavorites[curIndex - 1]
                                                    mutableFavorites[curIndex - 1] = temp
                                                    draggedIndex = curIndex - 1
                                                    dragOffset += cardHeightDp
                                                }
                                            }
                                        },
                                        onDragEnd = {
                                            draggedIndex = null
                                            dragOffset = 0f
                                            val reorderedList = mutableFavorites.mapIndexed { i, s ->
                                                s.copy(sortOrder = i)
                                            }
                                            onFavoritesReordered(reorderedList)
                                        },
                                        onDragCancel = {
                                            draggedIndex = null
                                            dragOffset = 0f
                                        }
                                    )
                                }
                                .testTag("drag_handle_${station.id}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DragHandle,
                                contentDescription = "Drag to reorder",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
