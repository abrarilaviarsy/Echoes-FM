package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.example.data.api.RetrofitClient
import com.example.data.db.AppDatabase
import com.example.data.model.FavoriteStation
import com.example.data.model.RadioBrowserStation
import com.example.data.model.SomaChannel
import com.example.data.model.LikedSong
import com.example.data.repository.RadioRepository
import com.example.playback.RadioPlaybackConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class CurrentSong(
    val title: String,
    val artist: String,
    val coverUrl: String?
)

class RadioViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = RadioRepository(
        radioBrowserApi = RetrofitClient.radioBrowserApi,
        somaFmApi = RetrofitClient.somaFmApi,
        favoriteDao = db.favoriteDao(),
        songHistoryDao = db.songHistoryDao(),
        likedSongDao = db.likedSongDao()
    )

    val playbackConnection = RadioPlaybackConnection(application)

    val expandPlayerTrigger = MutableStateFlow(false)
    fun resetExpandPlayerTrigger() {
        expandPlayerTrigger.value = false
    }

    // --- UI States ---
    val favorites: StateFlow<List<FavoriteStation>> = repository.allFavorites
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val likedSongs: StateFlow<List<LikedSong>> = repository.allLikedSongs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _radioBrowserStations = MutableStateFlow<List<RadioBrowserStation>>(emptyList())
    val radioBrowserStations: StateFlow<List<RadioBrowserStation>> = kotlinx.coroutines.flow.combine(
        _radioBrowserStations,
        favorites
    ) { stations, favList ->
        stations.sortedByDescending { station ->
            favList.any { it.id == station.stationuuid }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _somaChannels = MutableStateFlow<List<SomaChannel>>(emptyList())
    val somaChannels: StateFlow<List<SomaChannel>> = _somaChannels.asStateFlow()

    private val _isLoadingRadioBrowser = MutableStateFlow(false)
    val isLoadingRadioBrowser: StateFlow<Boolean> = _isLoadingRadioBrowser.asStateFlow()

    private val _isLoadingSoma = MutableStateFlow(false)
    val isLoadingSoma: StateFlow<Boolean> = _isLoadingSoma.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _countries = MutableStateFlow<List<com.example.data.model.CountryDto>>(emptyList())
    val countries: StateFlow<List<com.example.data.model.CountryDto>> = _countries.asStateFlow()

    private val _isLoadingCountries = MutableStateFlow(false)
    val isLoadingCountries: StateFlow<Boolean> = _isLoadingCountries.asStateFlow()

    // --- Playback States from Connection ----
    val isPlaying: StateFlow<Boolean> = playbackConnection.isPlaying
    val nowPlayingItem = playbackConnection.nowPlayingItem
    val isPreparing: StateFlow<Boolean> = playbackConnection.isPreparing
    val streamMetadata = playbackConnection.streamMetadata

    val trackHistory: StateFlow<List<com.example.data.model.HistoryEntity>> = repository.allHistory
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val songHistory: StateFlow<List<com.example.data.model.HistoryEntity>> = trackHistory

    val currentSongInfo = MutableStateFlow<CurrentSong?>(null)
    val currentLyrics = MutableStateFlow<String?>(null)
    val currentArtworkUrl = MutableStateFlow<String?>(null)
    private var currentStationLogo: String? = null
    val currentPlayingStation = MutableStateFlow<FavoriteStation?>(null)

    // Sleep Timer States
    val isTimerActive = MutableStateFlow(false)
    val remainingMinutes = MutableStateFlow(0)
    val sleepTimerMinutes = MutableStateFlow<Int?>(null)
    private var sleepTimerJob: kotlinx.coroutines.Job? = null

    private var lastSavedTrack: String = ""
    private var lastFetchedTitle: String = ""
    private var lastFetchedArtist: String = ""

    val isCurrentStationFavorited: StateFlow<Boolean> = kotlinx.coroutines.flow.combine(
        currentPlayingStation,
        favorites
    ) { currentStation, favList ->
        if (currentStation == null) return@combine false
        favList.any { it.id == currentStation.id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isCurrentSongLiked: StateFlow<Boolean> = kotlinx.coroutines.flow.combine(
        currentSongInfo,
        streamMetadata,
        nowPlayingItem,
        likedSongs
    ) { songInfo, metadata, nowPlaying, likedList ->
        val title = songInfo?.title ?: metadata?.title ?: nowPlaying?.mediaMetadata?.title?.toString() ?: ""
        val artist = songInfo?.artist ?: metadata?.artist ?: "Live Broadcast"
        if (title.isBlank()) return@combine false
        likedList.any {
            it.artist.equals(artist, ignoreCase = true) &&
            it.title.equals(title, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val sharedPrefs = application.getSharedPreferences("radio_station_prefs", Context.MODE_PRIVATE)

    private fun saveLastPlayedStation(
        id: String,
        name: String,
        streamUrl: String,
        imageUrl: String?,
        sourceType: String,
        bitrate: Int?,
        codec: String?
    ) {
        sharedPrefs.edit()
            .putString("last_station_id", id)
            .putString("last_station_name", name)
            .putString("last_station_stream_url", streamUrl)
            .putString("last_station_image_url", imageUrl)
            .putString("last_station_source_type", sourceType)
            .putInt("last_station_bitrate", bitrate ?: -1)
            .putString("last_station_codec", codec)
            .apply()
    }

    private fun loadLastPlayedStation(): FavoriteStation? {
        val id = sharedPrefs.getString("last_station_id", null) ?: return null
        val name = sharedPrefs.getString("last_station_name", "") ?: ""
        val streamUrl = sharedPrefs.getString("last_station_stream_url", null) ?: return null
        val imageUrl = sharedPrefs.getString("last_station_image_url", null)
        val sourceType = sharedPrefs.getString("last_station_source_type", "radio_browser") ?: "radio_browser"
        val bitrateRaw = sharedPrefs.getInt("last_station_bitrate", -1)
        val bitrate = if (bitrateRaw == -1) null else bitrateRaw
        val codec = sharedPrefs.getString("last_station_codec", null)

        return FavoriteStation(
            id = id,
            name = name,
            description = "Live Broadcast Stream",
            streamUrl = streamUrl,
            faviconUrl = imageUrl,
            sourceType = sourceType,
            genre = "Live Stream",
            bitrate = bitrate,
            codec = codec
        )
    }

    init {
        // Immediately restore the saved state of current playing station on startup
        val savedStation = loadLastPlayedStation()
        if (savedStation != null) {
            currentPlayingStation.value = savedStation
            currentStationLogo = savedStation.faviconUrl
            currentArtworkUrl.value = savedStation.faviconUrl
        }

        fetchRadioBrowserTop()
        fetchSomaChannels()

        // Observe mediaController to register Player.Listener and fetch album art / lyrics
        viewModelScope.launch {
            while (playbackConnection.mediaController == null) {
                delay(100)
            }

            // Prepare the restored station but do not autoplay: playWhenReady = false
            val controller = playbackConnection.mediaController
            if (controller != null && controller.currentMediaItem == null && savedStation != null) {
                playStation(
                    streamUrl = savedStation.streamUrl,
                    name = savedStation.name,
                    imageUrl = savedStation.faviconUrl,
                    bitrate = savedStation.bitrate,
                    codec = savedStation.codec,
                    playWhenReady = false
                )
            }

            playbackConnection.mediaController?.addListener(object : Player.Listener {
                override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                    var title = mediaMetadata.title?.toString()
                    var artist = mediaMetadata.artist?.toString()
                    if (!title.isNullOrBlank()) {
                        val isArtistEmpty = artist.isNullOrBlank() || artist == "Internet Radio" || artist == "Live Broadcast"
                        if (isArtistEmpty && title.contains(" - ")) {
                            val parts = title.split(" - ", limit = 2)
                            artist = parts[0].trim()
                            title = parts[1].trim()
                        }
                        val resolvedArtist = if (artist.isNullOrBlank() || artist == "Internet Radio") "Live Broadcast" else artist
                        if (title != lastFetchedTitle || resolvedArtist != lastFetchedArtist) {
                            lastFetchedTitle = title
                            lastFetchedArtist = resolvedArtist
                            currentLyrics.value = null
                            fetchAlbumArtFromiTunes(resolvedArtist, title)
                            fetchLyrics(resolvedArtist, title)
                        }
                    }
                }
            })
        }

        // Keep currentArtworkUrl synchronized with currentSongInfo or nowPlaying static logo
        viewModelScope.launch {
            nowPlayingItem.collectLatest { mediaItem ->
                if (mediaItem != null) {
                    if (currentPlayingStation.value == null) {
                        val streamUrl = mediaItem.mediaId
                        val isSomaStr = streamUrl.contains("somafm.com")
                        val id = if (isSomaStr) {
                            streamUrl.substringBefore("-128-mp3").substringAfterLast("/")
                        } else {
                            streamUrl
                        }
                        val sourceType = if (isSomaStr) "soma_fm" else "radio_browser"
                        val stationName = mediaItem.mediaMetadata.station?.toString() ?: mediaItem.mediaMetadata.title?.toString() ?: "Unknown Station"
                        currentPlayingStation.value = FavoriteStation(
                            id = id,
                            name = stationName,
                            description = "Live Broadcast Stream",
                            streamUrl = streamUrl,
                            faviconUrl = mediaItem.mediaMetadata.artworkUri?.toString(),
                            sourceType = sourceType,
                            genre = "Live Stream",
                            bitrate = null,
                            codec = null
                        )
                    }
                    val dynamicUrl = currentSongInfo.value?.coverUrl
                    val staticUrl = mediaItem.mediaMetadata.artworkUri?.toString()
                    currentArtworkUrl.value = dynamicUrl ?: staticUrl
                    if (currentStationLogo == null) {
                        currentStationLogo = staticUrl
                    }
                } else {
                    currentArtworkUrl.value = null
                    currentStationLogo = null
                }
            }
        }
    }

    private fun saveToHistoryDb(title: String, artist: String, coverUrl: String?) {
        val trimmedTitle = title.trim()
        val trimmedArtist = artist.trim()
        if (trimmedTitle.isNotBlank() && trimmedTitle != lastSavedTrack && !trimmedTitle.contains("Radio", ignoreCase = true)) {
            val stationName = currentPlayingStation.value?.name ?: nowPlayingItem.value?.mediaMetadata?.title?.toString() ?: "Unknown Station"
            val stName = stationName.trim()
            if (stName.isNotBlank() && stName != "Unknown Station") {
                if (trimmedTitle.contains(stName, ignoreCase = true) || stName.contains(trimmedTitle, ignoreCase = true)) {
                    return
                }
            }
            val blacklist = arrayOf("Live Broadcast", "Promo", "Commercial", "Sponsor", "Advertisement")
            if (blacklist.any { tag -> trimmedTitle.contains(tag, ignoreCase = true) }) {
                return
            }

            lastSavedTrack = trimmedTitle
            val finalCover = coverUrl ?: currentStationLogo ?: nowPlayingItem.value?.mediaMetadata?.artworkUri?.toString()
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    Log.d("RadioViewModel", "Adding track to history: $trimmedTitle by $trimmedArtist. Dupes are cleaned in Dao.")
                    repository.addSongToHistory(
                        com.example.data.model.HistoryEntity(
                            trackTitle = trimmedTitle,
                            artist = trimmedArtist,
                            stationName = stationName,
                            coverUrl = finalCover
                        )
                    )
                } catch (e: Exception) {
                    Log.e("RadioViewModel", "Error saving history to DB", e)
                }
            }
        }
    }

    fun fetchAlbumArtFromiTunes(artist: String, title: String) {
        // Immediately set with null artwork so that title and artist update on screen in real time
        currentSongInfo.value = CurrentSong(title, artist, null)
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val queryTerm = "$artist $title"
                val encodedTerm = java.net.URLEncoder.encode(queryTerm, "UTF-8")
                val urlString = "https://itunes.apple.com/search?term=$encodedTerm&entity=song&limit=1"
                val response = java.net.URL(urlString).readText()
                
                val jsonObject = org.json.JSONObject(response)
                val results = jsonObject.optJSONArray("results")
                var coverUrl: String? = null
                if (results != null && results.length() > 0) {
                    val firstItem = results.getJSONObject(0)
                    val artworkUrl100 = firstItem.optString("artworkUrl100", "")
                    coverUrl = if (artworkUrl100.isNotEmpty()) artworkUrl100.replace("100x100bb.jpg", "500x500bb.jpg") else null
                    currentSongInfo.value = CurrentSong(title, artist, coverUrl)
                    currentArtworkUrl.value = coverUrl ?: currentStationLogo
                    
                    // Update MediaSession/Player's metadata so System Notification gets updated
                    if (coverUrl != null) {
                        viewModelScope.launch(Dispatchers.Main) {
                            val p = playbackConnection.mediaController
                            if (p != null) {
                                val currentItem = p.currentMediaItem
                                if (currentItem != null) {
                                    val currentArtwork = currentItem.mediaMetadata.artworkUri?.toString()
                                    if (currentArtwork != coverUrl) {
                                        val newMetadata = currentItem.mediaMetadata.buildUpon()
                                            .setArtworkUri(android.net.Uri.parse(coverUrl))
                                            .setTitle(title)
                                            .setArtist(artist)
                                            .build()
                                        val updatedItem = currentItem.buildUpon()
                                            .setMediaMetadata(newMetadata)
                                            .build()
                                        p.replaceMediaItem(p.currentMediaItemIndex, updatedItem)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    currentArtworkUrl.value = currentStationLogo
                }
                
                // Save to history list with iTunes cover art (or null if search was empty)
                saveToHistoryDb(title, artist, coverUrl)
            } catch (e: Exception) {
                Log.e("RadioViewModel", "Error fetching album art from iTunes", e)
                currentArtworkUrl.value = currentStationLogo
                // Save to history list on network error / fallback
                saveToHistoryDb(title, artist, null)
            }
        }
    }

    fun fetchRadioBrowserTop() {
        viewModelScope.launch {
            _isLoadingRadioBrowser.value = true
            _radioBrowserStations.value = repository.getTopStations()
            _isLoadingRadioBrowser.value = false
        }
    }

    fun fetchCountries() {
        viewModelScope.launch {
            _isLoadingCountries.value = true
            try {
                _countries.value = repository.getCountries().sortedByDescending { it.stationcount }
            } catch (e: Exception) {
                Log.e("RadioViewModel", "Error fetching countries: ${e.message}")
                _countries.value = emptyList()
            } finally {
                _isLoadingCountries.value = false
            }
        }
    }

    fun searchRadioBrowser(query: String) {
        _searchQuery.value = query
        viewModelScope.launch {
            _isLoadingRadioBrowser.value = true
            _radioBrowserStations.value = repository.searchStations(query)
            _isLoadingRadioBrowser.value = false
        }
    }

    fun searchByCountry(countryName: String) {
        _searchQuery.value = countryName
        viewModelScope.launch {
            _isLoadingRadioBrowser.value = true
            _radioBrowserStations.value = repository.getStationsByCountry(countryName)
            _isLoadingRadioBrowser.value = false
        }
    }

    fun searchByTag(tagName: String) {
        _searchQuery.value = tagName
        viewModelScope.launch {
            _isLoadingRadioBrowser.value = true
            _radioBrowserStations.value = repository.getStationsByTag(tagName)
            _isLoadingRadioBrowser.value = false
        }
    }

    fun fetchSomaChannels() {
        viewModelScope.launch {
            _isLoadingSoma.value = true
            _somaChannels.value = repository.getSomaChannels()
            _isLoadingSoma.value = false
        }
    }

    // --- Favorites Actions ---
    fun toggleFavorite(station: FavoriteStation) {
        viewModelScope.launch {
            val isFav = favorites.value.any { it.id == station.id }
            if (isFav) {
                repository.removeFavoriteById(station.id)
            } else {
                repository.addFavorite(station)
            }
        }
    }

    fun updateFavoriteName(stationId: String, newName: String) {
        viewModelScope.launch {
            repository.updateFavoriteName(stationId, newName)
        }
    }

    fun updateFavoriteStationsOrder(newList: List<FavoriteStation>) {
        viewModelScope.launch {
            repository.updateFavoriteStationsOrder(newList)
        }
    }

    // --- Liked Songs Actions ---
    fun toggleSongLike(artist: String, title: String, coverUrl: String?) {
        viewModelScope.launch {
            val isLiked = likedSongs.value.any {
                it.artist.equals(artist, ignoreCase = true) &&
                it.title.equals(title, ignoreCase = true)
            }
            if (isLiked) {
                repository.removeLikedSong(artist, title)
            } else {
                repository.addLikedSong(
                    LikedSong(
                        artist = artist,
                        title = title,
                        coverUrl = coverUrl ?: currentArtworkUrl.value,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun toggleHistoryItemLikeStatus(historyItemId: Long) {
        viewModelScope.launch {
            val historyItem = trackHistory.value.find { it.id == historyItemId } ?: return@launch
            val artist = historyItem.artist
            val title = historyItem.title
            val coverUrl = historyItem.coverUrl
            val isLiked = likedSongs.value.any {
                it.artist.equals(artist, ignoreCase = true) &&
                it.title.equals(title, ignoreCase = true)
            }
            if (isLiked) {
                repository.removeLikedSong(artist, title)
            } else {
                repository.addLikedSong(
                    LikedSong(
                        artist = artist,
                        title = title,
                        coverUrl = coverUrl ?: currentArtworkUrl.value,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    // --- Playback Actions ---
    fun toggleFavoriteCurrent() {
        val station = currentPlayingStation.value ?: return
        toggleFavorite(station)
    }

    fun playStation(
        streamUrl: String,
        name: String,
        imageUrl: String?,
        bitrate: Int? = null,
        codec: String? = null,
        highResUrl: String? = null,
        lowResUrl: String? = null,
        playWhenReady: Boolean = true
    ) {
        currentSongInfo.value = null
        currentLyrics.value = null
        lastFetchedTitle = ""
        lastFetchedArtist = ""
        currentStationLogo = imageUrl
        currentArtworkUrl.value = imageUrl

        val isSomaStr = streamUrl.contains("somafm.com")
        val id = if (isSomaStr) {
            streamUrl.substringBefore("-128-mp3").substringAfterLast("/")
        } else {
            streamUrl
        }
        val sourceType = if (isSomaStr) "soma_fm" else "radio_browser"

        currentPlayingStation.value = FavoriteStation(
            id = id,
            name = name,
            description = "Live Broadcast Stream",
            streamUrl = streamUrl,
            faviconUrl = imageUrl,
            sourceType = sourceType,
            genre = "Live Stream",
            bitrate = bitrate,
            codec = codec
        )

        saveLastPlayedStation(
            id = id,
            name = name,
            streamUrl = streamUrl,
            imageUrl = imageUrl,
            sourceType = sourceType,
            bitrate = bitrate,
            codec = codec
        )

        val targetBitrate = bitrate ?: 128
        val targetCodec = codec ?: "mp3"

        Log.d("PlayStation", "Playing URL: $streamUrl, playWhenReady: $playWhenReady")
        playbackConnection.playStream(streamUrl, name, imageUrl, targetBitrate, targetCodec, playWhenReady)
    }

    // --- Sleep Timer Actions ---
    fun startSleepTimer(minutes: Int) {
        cancelSleepTimer()
        if (minutes <= 0) return

        isTimerActive.value = true
        remainingMinutes.value = minutes
        sleepTimerMinutes.value = minutes

        sleepTimerJob = viewModelScope.launch(Dispatchers.Main) {
            while (remainingMinutes.value > 0) {
                delay(60000L)
                remainingMinutes.value -= 1
                sleepTimerMinutes.value = remainingMinutes.value
            }
            if (isPlaying.value) {
                playbackConnection.pause()
            }
            cancelSleepTimer()
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        isTimerActive.value = false
        remainingMinutes.value = 0
        sleepTimerMinutes.value = null
    }

    // --- Live Lyrics Actions ---
    fun fetchLyrics(artist: String, title: String) {
        currentLyrics.value = null
        val cleanTitle = title.replace(Regex("\\(.*?\\)|\\[.*?\\]"), "").trim()
        val cleanArtist = artist.replace(Regex("\\(.*?\\)|\\[.*?\\]"), "").trim()

        if (cleanTitle.isBlank() || cleanTitle.contains("Live Broadcast", ignoreCase = true) || cleanArtist.contains("Live Broadcast", ignoreCase = true)) {
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val queryTerm = "$cleanArtist $cleanTitle"
                val encodedQuery = java.net.URLEncoder.encode(queryTerm, "UTF-8")
                val urlString = "https://lrclib.net/api/search?q=$encodedQuery"

                val connection = java.net.URL(urlString).openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.setRequestProperty("User-Agent", "Mozilla/5.0")

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    try {
                        val jsonArray = org.json.JSONArray(response)
                        if (jsonArray.length() > 0) {
                            val firstObj = jsonArray.getJSONObject(0)
                            
                            val plainLyrics = firstObj.optString("plainLyrics", "").trim()
                            val syncedLyrics = firstObj.optString("syncedLyrics", "").trim()

                            val finalLyrics = when {
                                plainLyrics.isNotBlank() && plainLyrics != "null" -> plainLyrics
                                syncedLyrics.isNotBlank() && syncedLyrics != "null" -> {
                                    syncedLyrics.replace(Regex("\\[\\d{2}:\\d{2}\\.\\d{2,3}\\] *"), "").trim()
                                }
                                else -> null
                            }
                            currentLyrics.value = finalLyrics
                        } else {
                            currentLyrics.value = null
                        }
                    } catch (e: Exception) {
                        Log.e("RadioViewModel", "Error parsing LRCLIB array", e)
                        currentLyrics.value = null
                    }
                } else {
                    currentLyrics.value = null
                }
            } catch (e: Exception) {
                Log.e("RadioViewModel", "Error fetching lyrics from LRCLIB", e)
                currentLyrics.value = null
            }
        }
    }

    fun togglePlayPause() {
        if (isPlaying.value) {
            playbackConnection.pause()
        } else {
            playbackConnection.play()
        }
    }

    fun skipNext() {
        val currentItem = nowPlayingItem.value ?: return
        val streamUrl = currentItem.mediaId

        if (streamUrl.contains("somafm.com")) {
            val list = somaChannels.value
            if (list.isNotEmpty()) {
                val currentIndex = list.indexOfFirst {
                    "https://ice1.somafm.com/${it.id}-128-mp3" == streamUrl
                }
                if (currentIndex != -1) {
                    val nextIndex = (currentIndex + 1) % list.size
                    val nextChannel = list[nextIndex]
                    playStation(
                        streamUrl = "https://ice1.somafm.com/${nextChannel.id}-128-mp3",
                        name = nextChannel.title,
                        imageUrl = nextChannel.xlimage ?: nextChannel.image,
                        bitrate = 128,
                        codec = "mp3"
                    )
                }
            }
        } else {
            // Check favorites first since it might be played from favorites
            val favList = favorites.value
            val favIndex = favList.indexOfFirst { it.streamUrl == streamUrl }
            if (favIndex != -1 && favList.isNotEmpty()) {
                val nextIndex = (favIndex + 1) % favList.size
                val nextFav = favList[nextIndex]
                playStation(
                    streamUrl = nextFav.streamUrl,
                    name = nextFav.name,
                    imageUrl = nextFav.faviconUrl,
                    bitrate = nextFav.bitrate,
                    codec = nextFav.codec
                )
            } else {
                // Check radio browser stations
                val rbList = radioBrowserStations.value
                if (rbList.isNotEmpty()) {
                    val currentIndex = rbList.indexOfFirst { (it.url_resolved ?: it.url) == streamUrl }
                    if (currentIndex != -1) {
                        val nextIndex = (currentIndex + 1) % rbList.size
                        val nextStation = rbList[nextIndex]
                        playStation(
                            streamUrl = nextStation.url_resolved ?: nextStation.url,
                            name = nextStation.name,
                            imageUrl = nextStation.favicon,
                            bitrate = nextStation.bitrate,
                            codec = nextStation.codec
                        )
                    }
                }
            }
        }
    }

    fun skipPrevious() {
        val currentItem = nowPlayingItem.value ?: return
        val streamUrl = currentItem.mediaId

        if (streamUrl.contains("somafm.com")) {
            val list = somaChannels.value
            if (list.isNotEmpty()) {
                val currentIndex = list.indexOfFirst {
                    "https://ice1.somafm.com/${it.id}-128-mp3" == streamUrl
                }
                if (currentIndex != -1) {
                    val prevIndex = (currentIndex - 1 + list.size) % list.size
                    val prevChannel = list[prevIndex]
                    playStation(
                        streamUrl = "https://ice1.somafm.com/${prevChannel.id}-128-mp3",
                        name = prevChannel.title,
                        imageUrl = prevChannel.xlimage ?: prevChannel.image,
                        bitrate = 128,
                        codec = "mp3"
                    )
                }
            }
        } else {
            // Check favorites first
            val favList = favorites.value
            val favIndex = favList.indexOfFirst { it.streamUrl == streamUrl }
            if (favIndex != -1 && favList.isNotEmpty()) {
                val prevIndex = (favIndex - 1 + favList.size) % favList.size
                val prevFav = favList[prevIndex]
                playStation(
                    streamUrl = prevFav.streamUrl,
                    name = prevFav.name,
                    imageUrl = prevFav.faviconUrl,
                    bitrate = prevFav.bitrate,
                    codec = prevFav.codec
                )
            } else {
                // Check radio browser stations
                val rbList = radioBrowserStations.value
                if (rbList.isNotEmpty()) {
                    val currentIndex = rbList.indexOfFirst { (it.url_resolved ?: it.url) == streamUrl }
                    if (currentIndex != -1) {
                        val prevIndex = (currentIndex - 1 + rbList.size) % rbList.size
                        val prevStation = rbList[prevIndex]
                        playStation(
                            streamUrl = prevStation.url_resolved ?: prevStation.url,
                            name = prevStation.name,
                            imageUrl = prevStation.favicon,
                            bitrate = prevStation.bitrate,
                            codec = prevStation.codec
                        )
                    }
                }
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
            lastSavedTrack = ""
        }
    }

    fun exportDatabaseToJson(): String {
        val backup = com.example.data.model.BackupData(
            favoriteStations = favorites.value,
            likedSongs = likedSongs.value
        )
        return com.google.gson.Gson().toJson(backup)
    }

    fun importDatabaseFromJson(jsonString: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val backup = com.google.gson.Gson().fromJson(jsonString, com.example.data.model.BackupData::class.java)
                if (backup != null) {
                    if (!backup.favoriteStations.isNullOrEmpty()) {
                        repository.updateFavoriteStationsOrder(backup.favoriteStations)
                    }
                    if (!backup.likedSongs.isNullOrEmpty()) {
                        db.likedSongDao().insertAllLikedSongs(backup.likedSongs)
                    }
                }
            } catch (e: Exception) {
                Log.e("RadioViewModel", "Error importing backup JSON", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        cancelSleepTimer()
        playbackConnection.release()
    }
}
