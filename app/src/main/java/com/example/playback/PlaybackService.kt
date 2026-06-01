package com.example.playback

import com.example.MainActivity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionResult
import com.example.data.db.AppDatabase
import com.example.data.repository.RadioRepository
import com.example.data.api.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

private const val ACTION_TOGGLE_FAVORITE = "ACTION_TOGGLE_FAVORITE"

class PlaybackService : MediaSessionService() {
    private var player: Player? = null
    private var mediaSession: MediaSession? = null

    private lateinit var db: AppDatabase
    private lateinit var repository: RadioRepository

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var bbcPollingJob: Job? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        
        db = AppDatabase.getDatabase(this)
        repository = RadioRepository(
            radioBrowserApi = RetrofitClient.radioBrowserApi,
            somaFmApi = RetrofitClient.somaFmApi,
            favoriteDao = db.favoriteDao(),
            songHistoryDao = db.songHistoryDao(),
            likedSongDao = db.likedSongDao()
        )
        
        // Configure standard AudioAttributes for music playback and enable automatic Audio Focus handling
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        // Build ExoPlayer with standard settings optimized for live audio streams and HLS (.m3u8) support
        val exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(this))
            .setAudioAttributes(audioAttributes, true)
            .build()
        
        // Add Player error listener and metadata listener
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.e("PlaybackService", "Error during radio stream playback", error)
                val errorMessage = error.localizedMessage ?: error.message ?: "Unknown media error"
                Toast.makeText(
                    this@PlaybackService,
                    "Error playing stream: $errorMessage",
                    Toast.LENGTH_LONG
                ).show()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                restartBbcPollingOrMetaReset(mediaItem)
                updateMediaNotificationLayout()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val currentItem = exoPlayer.currentMediaItem
                restartBbcPollingOrMetaReset(currentItem)
                updateMediaNotificationLayout()
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                val currentItem = exoPlayer.currentMediaItem ?: return
                val stationName = currentItem.mediaMetadata.station?.toString() ?: currentItem.mediaMetadata.title?.toString() ?: ""
                val streamUrl = currentItem.mediaId ?: ""
                if (isBbcStation(stationName, streamUrl)) return

                val rawTitle = mediaMetadata.title?.toString()
                val rawArtist = mediaMetadata.artist?.toString()
                val displayTitle = mediaMetadata.displayTitle?.toString()

                val (extractedArtist, extractedTitle) = parseIcyMetadata(rawTitle, rawArtist, displayTitle)
                if (extractedTitle.isNotEmpty()) {
                    updatePlayerMetadata(extractedTitle, extractedArtist)
                }
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                val currentItem = exoPlayer.currentMediaItem ?: return
                val stationName = currentItem.mediaMetadata.station?.toString() ?: currentItem.mediaMetadata.title?.toString() ?: ""
                val streamUrl = currentItem.mediaId ?: ""
                if (isBbcStation(stationName, streamUrl)) return

                val meta = currentItem.mediaMetadata
                val rawTitle = meta.title?.toString()
                val rawArtist = meta.artist?.toString()
                val displayTitle = meta.displayTitle?.toString()

                val (extractedArtist, extractedTitle) = parseIcyMetadata(rawTitle, rawArtist, displayTitle)
                if (extractedTitle.isNotEmpty()) {
                    updatePlayerMetadata(extractedTitle, extractedArtist)
                }
            }

            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                val currentItem = exoPlayer.currentMediaItem ?: return
                val stationName = currentItem.mediaMetadata.station?.toString() ?: currentItem.mediaMetadata.title?.toString() ?: ""
                val streamUrl = currentItem.mediaId ?: ""

                val meta = currentItem.mediaMetadata
                val rawTitle = meta.title?.toString() ?: stationName
                val rawArtist = meta.artist?.toString() ?: "Live Broadcast"

                if (isBbcStation(stationName, streamUrl)) {
                    updatePlayerMetadata(rawTitle, rawArtist)
                    return
                }

                val (extractedArtist, extractedTitle) = parseIcyMetadata(rawTitle, rawArtist, if (rawTitle != stationName) rawTitle else null)
                val finalTitle = if (extractedTitle.isNotEmpty()) extractedTitle else stationName
                val finalArtist = if (extractedTitle.isNotEmpty()) extractedArtist else "Live Broadcast"
                
                updatePlayerMetadata(finalTitle, finalArtist)
            }

            override fun onMetadata(metadata: androidx.media3.common.Metadata) {
                val currentItem = exoPlayer.currentMediaItem ?: return
                val stationName = currentItem.mediaMetadata.station?.toString() ?: currentItem.mediaMetadata.title?.toString() ?: ""
                val streamUrl = currentItem.mediaId ?: ""
                if (isBbcStation(stationName, streamUrl)) return

                var songTitle: String? = null
                var songArtist: String? = null
                for (i in 0 until metadata.length()) {
                    val entry = metadata.get(i)
                    if (entry is androidx.media3.extractor.metadata.icy.IcyInfo) {
                        val icyTitle = entry.title
                        if (!icyTitle.isNullOrBlank()) {
                            if (icyTitle.contains(" - ")) {
                                val parts = icyTitle.split(" - ", limit = 2)
                                songArtist = parts[0].trim()
                                songTitle = parts[1].trim()
                            } else {
                                songTitle = icyTitle.trim()
                            }
                        }
                    } else if (entry is androidx.media3.extractor.metadata.id3.TextInformationFrame) {
                        try {
                            val value = try {
                                entry.value
                            } catch (e: Throwable) {
                                null
                            } ?: entry.values.firstOrNull()

                            if (!value.isNullOrBlank()) {
                                if (entry.id == "TIT2") {
                                    songTitle = value
                                } else if (entry.id == "TPE1") {
                                    songArtist = value
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                if (songTitle != null) {
                    updatePlayerMetadata(songTitle, songArtist ?: stationName)
                }
            }
        })
            
        player = exoPlayer

        val sessionActivityIntent = Intent(this, MainActivity::class.java).apply {
            putExtra("EXPAND_PLAYER", true)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivityPendingIntent = android.app.PendingIntent.getActivity(
            this,
            0,
            sessionActivityIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        // Build the initial Favorite/Like action button
        val toggleFavoriteCommand = SessionCommand(ACTION_TOGGLE_FAVORITE, Bundle.EMPTY)
        val initialFavoriteButton = CommandButton.Builder()
            .setSessionCommand(toggleFavoriteCommand)
            .setIconResId(com.example.R.drawable.ic_heart_outline)
            .setDisplayName("Favorite")
            .setEnabled(true)
            .build()

        // Build MediaSession exposing our media status and receiving remote controls
        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(sessionActivityPendingIntent)
            .setCallback(CustomSessionCallback())
            .setCustomLayout(listOf(initialFavoriteButton))
            .build()

        updateMediaNotificationLayout()

        // Observe liked songs and favorite stations database flows reactively to keep custom action buttons in sync!
        serviceScope.launch {
            repository.allLikedSongs.collect {
                updateMediaNotificationLayout()
            }
        }
        serviceScope.launch {
            repository.allFavorites.collect {
                updateMediaNotificationLayout()
            }
        }
    }

    override fun onDestroy() {
        bbcPollingJob?.cancel()
        bbcPollingJob = null
        try {
            serviceScope.cancel()
        } catch (e: Exception) {
            // Ignored
        }
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        player = null
        super.onDestroy()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    private inner class CustomSessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val connectionResult = super.onConnect(session, controller)
            val sessionCommands = connectionResult.availableSessionCommands
                .buildUpon()
                .add(SessionCommand(ACTION_TOGGLE_FAVORITE, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.accept(
                sessionCommands,
                connectionResult.availablePlayerCommands
            )
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == ACTION_TOGGLE_FAVORITE) {
                toggleFavoriteCurrent()
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    private fun toggleFavoriteCurrent() {
        val currentItem = player?.currentMediaItem ?: return
        val title = currentItem.mediaMetadata.title?.toString() ?: ""
        val artist = currentItem.mediaMetadata.artist?.toString() ?: ""
        val streamUrl = currentItem.mediaId ?: ""
        val stationLogo = currentItem.mediaMetadata.artworkUri?.toString()
        val stationName = currentItem.mediaMetadata.station?.toString() ?: currentItem.mediaMetadata.title?.toString() ?: ""

        val isSomaStr = streamUrl.contains("somafm.com")
        val id = if (isSomaStr) {
            streamUrl.substringBefore("-128-mp3").substringAfterLast("/")
        } else {
            streamUrl
        }

        val isSongPlay = title.isNotEmpty() && artist.isNotEmpty() && artist != "Live Broadcast" && artist != "Internet Radio"

        serviceScope.launch {
            if (isSongPlay) {
                val isLiked = repository.isSongLiked(artist, title).first()
                if (isLiked) {
                    repository.removeLikedSong(artist, title)
                } else {
                    repository.addLikedSong(
                        com.example.data.model.LikedSong(
                            artist = artist,
                            title = title,
                            coverUrl = stationLogo,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
            } else {
                if (id.isNotEmpty()) {
                    val isFav = repository.isFavorite(id).first()
                    if (isFav) {
                        repository.removeFavoriteById(id)
                    } else {
                        val sourceType = if (isSomaStr) "soma_fm" else "radio_browser"
                        repository.addFavorite(
                            com.example.data.model.FavoriteStation(
                                id = id,
                                name = stationName,
                                description = "Live Broadcast Stream",
                                streamUrl = streamUrl,
                                faviconUrl = stationLogo,
                                sourceType = sourceType,
                                genre = "Live Stream",
                                bitrate = null,
                                codec = null
                            )
                        )
                    }
                }
            }
        }
    }

    private fun updateMediaNotificationLayout() {
        val currentItem = player?.currentMediaItem
        if (currentItem == null) {
            mediaSession?.setCustomLayout(emptyList())
            return
        }

        val title = currentItem.mediaMetadata.title?.toString() ?: ""
        val artist = currentItem.mediaMetadata.artist?.toString() ?: ""
        val streamUrl = currentItem.mediaId ?: ""

        val isSomaStr = streamUrl.contains("somafm.com")
        val id = if (isSomaStr) {
            streamUrl.substringBefore("-128-mp3").substringAfterLast("/")
        } else {
            streamUrl
        }

        val isSongPlay = title.isNotEmpty() && artist.isNotEmpty() && artist != "Live Broadcast" && artist != "Internet Radio"

        serviceScope.launch {
            val isFav = if (isSongPlay) {
                repository.isSongLiked(artist, title).first()
            } else {
                if (id.isNotEmpty()) {
                    repository.isFavorite(id).first()
                } else {
                    false
                }
            }

            withContext(Dispatchers.Main) {
                val session = mediaSession ?: return@withContext
                
                // Define the Custom Command
                val toggleFavoriteCommand = SessionCommand(
                    ACTION_TOGGLE_FAVORITE, 
                    Bundle.EMPTY
                )
                
                // Build the CommandButton
                val favoriteButton = CommandButton.Builder()
                    .setSessionCommand(toggleFavoriteCommand)
                    .setIconResId(if (isFav) com.example.R.drawable.ic_heart_filled else com.example.R.drawable.ic_heart_outline)
                    .setDisplayName(if (isSongPlay) "Like Song" else "Favorite Station")
                    .setEnabled(true)
                    .build()
                
                // Add to MediaSession custom layout
                session.setCustomLayout(listOf(favoriteButton))
            }
        }
    }

    // --- Background Metadata Core Engine (Service Side) ---

    private fun restartBbcPollingOrMetaReset(mediaItem: MediaItem?) {
        bbcPollingJob?.cancel()
        bbcPollingJob = null

        val p = player ?: return
        if (mediaItem == null) return

        val name = mediaItem.mediaMetadata.station?.toString() ?: mediaItem.mediaMetadata.title?.toString() ?: ""
        val streamUrl = mediaItem.mediaId ?: ""
        val isPlayingLocal = p.isPlaying

        if (isPlayingLocal && isBbcStation(name, streamUrl)) {
            val networkId = getBbcNetworkId(name)
            if (networkId != null) {
                bbcPollingJob = serviceScope.launch {
                    val defaultStationName = name
                    while (isActive) {
                        fetchBbcMetadata(networkId, defaultStationName)
                        delay(20000) // Poll every 20 seconds
                    }
                }
            }
        }
    }

    private suspend fun fetchBbcMetadata(networkId: String, defaultStationName: String) {
        withContext(Dispatchers.IO) {
            try {
                val client = okhttp3.OkHttpClient()
                val request = okhttp3.Request.Builder()
                    .url("https://rms.api.bbc.co.uk/v2/services/$networkId/segments/latest")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) RadioApp/1.0")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string()
                        if (!bodyString.isNullOrBlank()) {
                            val moshi = com.squareup.moshi.Moshi.Builder()
                                .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
                                .build()
                            val adapter = moshi.adapter(Any::class.java)
                            val responseMap = adapter.fromJson(bodyString) as? Map<String, Any>
                            val data = responseMap?.get("data")

                            var artist: String? = null
                            var title: String? = null

                            if (data is List<*>) {
                                val firstSegment = data.firstOrNull() as? Map<String, Any>
                                val titles = firstSegment?.get("titles") as? Map<String, Any>
                                artist = titles?.get("primary")?.toString()
                                title = titles?.get("secondary")?.toString()
                            } else if (data is Map<*, *>) {
                                val titles = data["titles"] as? Map<String, Any>
                                artist = titles?.get("primary")?.toString()
                                title = titles?.get("secondary")?.toString()
                            }

                            if (!title.isNullOrBlank()) {
                                updatePlayerMetadata(title.trim(), (artist ?: defaultStationName).trim())
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun updatePlayerMetadata(title: String, artist: String) {
        serviceScope.launch(Dispatchers.Main) {
            val p = player ?: return@launch
            val currentItem = p.currentMediaItem ?: return@launch

            val currentTitle = currentItem.mediaMetadata.title?.toString()
            val currentArtist = currentItem.mediaMetadata.artist?.toString()

            if (currentTitle == title && currentArtist == artist) {
                return@launch // Already matches, skip to prevent infinite update loops!
            }

            val newMetadata = currentItem.mediaMetadata.buildUpon()
                .setTitle(title)
                .setArtist(artist)
                .setDisplayTitle(title)
                .build()

            val updatedItem = currentItem.buildUpon()
                .setMediaMetadata(newMetadata)
                .build()

            val index = p.currentMediaItemIndex
            p.replaceMediaItem(index, updatedItem)
            updateMediaNotificationLayout()
        }
    }

    private fun isBbcStation(stationName: String, streamUrl: String): Boolean {
        val nameLower = stationName.lowercase()
        val urlLower = streamUrl.lowercase()
        return nameLower.contains("dance") ||
               nameLower.contains("1xtra") ||
               nameLower.contains("radio 2") ||
               nameLower.contains("radio 1") ||
               urlLower.contains("bbc.co.uk") ||
               urlLower.contains("bbcmedia.co.uk")
    }

    private fun getBbcNetworkId(stationName: String): String? {
        val nameLower = stationName.lowercase()
        return when {
            nameLower.contains("dance") -> "bbc_radio_one_dance"
            nameLower.contains("1xtra") -> "bbc_1xtra"
            nameLower.contains("anthems") -> "bbc_radio_one_anthems"
            nameLower.contains("radio 2") -> "bbc_radio_two"
            nameLower.contains("radio 1") -> "bbc_radio_one"
            else -> null
        }
    }

    private fun parseIcyMetadata(title: String?, artist: String?, displayTitle: String?): Pair<String, String> {
        val t = title?.trim() ?: ""
        val a = artist?.trim() ?: ""
        val dt = displayTitle?.trim() ?: ""

        if (t.isNotEmpty() && a.isNotEmpty() && a != "Internet Radio") {
            return Pair(a, t)
        }

        if (t.contains(" - ")) {
            val parts = t.split(" - ", limit = 2)
            if (parts.size == 2) {
                return Pair(parts[0].trim(), parts[1].trim())
            }
        }

        if (dt.contains(" - ")) {
            val parts = dt.split(" - ", limit = 2)
            if (parts.size == 2) {
                return Pair(parts[0].trim(), parts[1].trim())
            }
        }

        if (t.isNotEmpty()) {
            val defaultArtist = if (a == "Internet Radio") "Live Broadcast" else a.ifEmpty { "Live Broadcast" }
            return Pair(defaultArtist, t)
        }

        if (dt.isNotEmpty()) {
            return Pair("Live Broadcast", dt)
        }

        val activeMediaTitle = player?.currentMediaItem?.mediaMetadata?.station?.toString()
            ?: player?.currentMediaItem?.mediaMetadata?.title?.toString()
            ?: "Unknown Station"
        return Pair("Live Broadcast", activeMediaTitle)
    }
}
