package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.FavoriteStation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioBrowserTab(
    stations: List<com.example.data.model.RadioBrowserStation>,
    favorites: List<FavoriteStation>,
    searchQuery: String,
    isLoading: Boolean,
    onSearch: (String) -> Unit,
    onSearchByTag: (String) -> Unit,
    onPlay: (com.example.data.model.RadioBrowserStation) -> Unit,
    onToggleFavorite: (FavoriteStation) -> Unit,
    onBrowseCountriesClick: () -> Unit,
    currentOrder: String = "clickcount",
    onOrderChanged: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var rawText by remember { mutableStateOf(searchQuery) }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    val keyboardController = LocalSoftwareKeyboardController.current
    var sortMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(searchQuery) {
        rawText = searchQuery
        val popularList = listOf("top 40", "pop", "rock", "jazz", "electronic", "news", "indie")
        selectedTag = if (searchQuery in popularList) searchQuery else null
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(8.dp))
        // Search bar row with inline countries trigger button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = rawText,
                onValueChange = {
                    rawText = it
                    if (selectedTag != null && it != selectedTag) {
                        selectedTag = null
                    }
                    onSearch(it) // Real-time feedback
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("station_search_field"),
                placeholder = { Text("Search radio stations...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Icon") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    onSearch(rawText)
                    keyboardController?.hide()
                }),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
                    unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    focusedContainerColor = Color(0xFF131313),
                    unfocusedContainerColor = Color(0xFF090909),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            Box {
                IconButton(
                    onClick = { sortMenuExpanded = true },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("sort_stations_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Sort,
                        contentDescription = "Sort Stations",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false },
                    modifier = Modifier
                        .width(220.dp)
                        .background(Color(0xFF1C1C1E))
                        .testTag("sort_stations_dropdown")
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Sort by Name",
                                color = if (currentOrder == "name") MaterialTheme.colorScheme.primary else Color.White,
                                fontWeight = if (currentOrder == "name") FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            sortMenuExpanded = false
                            onOrderChanged("name")
                        },
                        modifier = Modifier.testTag("sort_by_name_option")
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Sort by Popularity (Votes)",
                                color = if (currentOrder == "votes") MaterialTheme.colorScheme.primary else Color.White,
                                fontWeight = if (currentOrder == "votes") FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            sortMenuExpanded = false
                            onOrderChanged("votes")
                        },
                        modifier = Modifier.testTag("sort_by_votes_option")
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Sort by Trending (Clicks)",
                                color = if (currentOrder == "clickcount") MaterialTheme.colorScheme.primary else Color.White,
                                fontWeight = if (currentOrder == "clickcount") FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            sortMenuExpanded = false
                            onOrderChanged("clickcount")
                        },
                        modifier = Modifier.testTag("sort_by_clicks_option")
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onBrowseCountriesClick,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("browse_countries_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Public,
                    contentDescription = "Browse by Country",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Predefined tags scrollable row (similar to YouTube Music's category filters)
        val popularTags = remember { listOf("top 40", "pop", "rock", "jazz", "electronic", "news", "indie") }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("tag_chips_row")
        ) {
            items(popularTags) { tag ->
                val isSelected = selectedTag == tag
                val backgroundColor = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color(0xFF1E1E1E)
                }
                val contentColor = if (isSelected) {
                    Color.Black
                } else {
                    Color.White
                }
                val borderStroke = if (isSelected) {
                    null
                } else {
                    BorderStroke(1.dp, Color(0x3349454F))
                }

                Surface(
                    onClick = {
                        val newTag = if (selectedTag == tag) null else tag
                        selectedTag = newTag
                        rawText = newTag ?: ""
                        if (newTag != null) {
                            onSearchByTag(newTag)
                        } else {
                            onSearch("")
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = backgroundColor,
                    contentColor = contentColor,
                    border = borderStroke,
                    modifier = Modifier.testTag("tag_chip_$tag")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tag.uppercase(),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (stations.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = "No Stations",
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No stations found",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Try typing something else or check your network.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                items(stations, key = { it.stationuuid }) { station ->
                    val isFav = favorites.any { it.id == station.stationuuid }
                    
                    Card(
                        onClick = { onPlay(station) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("station_card_${station.stationuuid}"),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, Color(0x3349454F)),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF121212)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // High-fidelity favicon / logo with fallback
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Radio,
                                    contentDescription = "Radio fallback icon",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )

                                if (!station.favicon.isNullOrBlank()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(station.favicon)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "${station.name} Logo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            // Metadata details with elegant typography
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = station.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = station.tags?.takeIf { it.isNotBlank() } ?: "General Music",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.Center
                            ) {
                                // Favorite Icon trigger with dynamic styling
                                IconButton(
                                    onClick = { onToggleFavorite(station.toFavorite()) },
                                    modifier = Modifier.testTag("fav_button_${station.stationuuid}")
                                ) {
                                    Icon(
                                        imageVector = if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                        contentDescription = if (isFav) "Remove Favorite" else "Add Favorite",
                                        tint = if (isFav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                                
                                val formattedClicks = remember(station.clickcount) {
                                    java.text.NumberFormat.getIntegerInstance().format(station.clickcount)
                                }
                                Text(
                                    text = "🔥 $formattedClicks",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
