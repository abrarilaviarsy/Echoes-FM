package com.example.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.MutableStateFlow

class RadioPlaybackConnection(context: Context) {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    var mediaController: MediaController? = null
        private set

    val isPlaying = MutableStateFlow(false)
    val nowPlayingItem = MutableStateFlow<MediaItem?>(null)
    val isPreparing = MutableStateFlow(false)
    val streamMetadata = MutableStateFlow<com.example.data.model.MediaTrackInfo?>(null)
    private var lastMediaId: String? = null

    init {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                val controller = controllerFuture?.get()
                mediaController = controller
                if (controller != null) {
                    controller.addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(isPlayingChanged: Boolean) {
                            isPlaying.value = isPlayingChanged
                        }

                        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                            nowPlayingItem.value = mediaItem
                            if (mediaItem != null) {
                                val currentMediaId = mediaItem.mediaId
                                if (currentMediaId != lastMediaId) {
                                    lastMediaId = currentMediaId
                                }
                                syncMetadata(mediaItem)
                            } else {
                                lastMediaId = null
                                streamMetadata.value = null
                            }
                        }

                        override fun onPlaybackStateChanged(playbackState: Int) {
                            isPreparing.value = (playbackState == Player.STATE_BUFFERING)
                        }

                        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                            val currentItem = controller.currentMediaItem
                            if (currentItem != null) {
                                syncMetadata(currentItem)
                            }
                        }

                        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                            val currentItem = controller.currentMediaItem
                            if (currentItem != null) {
                                syncMetadata(currentItem)
                            }
                        }
                    })
                    // Sync initial states
                    isPlaying.value = controller.isPlaying
                    nowPlayingItem.value = controller.currentMediaItem
                    val currentItem = controller.currentMediaItem
                    if (currentItem != null) {
                        lastMediaId = currentItem.mediaId
                        syncMetadata(currentItem)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
    }

    private fun extractTrackFormatInfo(player: Player?): Pair<Int?, String?> {
        val p = player ?: return Pair(null, null)
        try {
            val tracks = p.currentTracks
            for (group in tracks.groups) {
                if (group.type == androidx.media3.common.C.TRACK_TYPE_AUDIO && group.isSelected) {
                    for (i in 0 until group.length) {
                        if (group.isTrackSelected(i)) {
                            val format = group.getTrackFormat(i)
                            val mimeType = format.sampleMimeType
                            val bitrateBps = format.bitrate
                            
                            val codec = mapMimeTypeToCodec(mimeType)
                            val bitrateKbps = if (bitrateBps > 0) bitrateBps / 1000 else null
                            
                            return Pair(bitrateKbps, codec)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("RadioPlaybackConnection", "Error extracting track format details", e)
        }
        return Pair(null, null)
    }

    private fun mapMimeTypeToCodec(mimeType: String?): String {
        if (mimeType == null) return "UNKNOWN"
        return when {
            mimeType.contains("mpeg", ignoreCase = true) || mimeType.contains("mp3", ignoreCase = true) -> "MP3"
            mimeType.contains("mp4a", ignoreCase = true) || mimeType.contains("aac", ignoreCase = true) -> "AAC"
            mimeType.contains("ac3", ignoreCase = true) -> "AC3"
            mimeType.contains("eac3", ignoreCase = true) -> "E-AC3"
            mimeType.contains("vorbis", ignoreCase = true) || mimeType.contains("ogg", ignoreCase = true) -> "Vorbis"
            mimeType.contains("opus", ignoreCase = true) -> "Opus"
            mimeType.contains("flac", ignoreCase = true) -> "FLAC"
            mimeType.contains("wav", ignoreCase = true) -> "WAV"
            mimeType.contains("webm", ignoreCase = true) -> "WebM"
            else -> "UNKNOWN"
        }
    }

    private fun syncMetadata(mediaItem: MediaItem) {
        val title = mediaItem.mediaMetadata.title?.toString()
        val artist = mediaItem.mediaMetadata.artist?.toString()
        val station = mediaItem.mediaMetadata.station?.toString() ?: title ?: "Unknown Station"
        val resolvedArtist = if (artist.isNullOrBlank() || artist == "Internet Radio") "Live Broadcast" else artist
        val resolvedTitle = if (title.isNullOrBlank() || title == station) station else title

        val extras = mediaItem.mediaMetadata.extras
        var bitrate = if (extras != null && extras.containsKey("bitrate")) extras.getInt("bitrate") else null
        var codec = extras?.getString("codec")

        if (bitrate == null || codec == null || codec == "UNKNOWN") {
            val (extractedBitrate, extractedCodec) = extractTrackFormatInfo(mediaController)
            if (bitrate == null && extractedBitrate != null) {
                bitrate = extractedBitrate
            }
            if ((codec == null || codec == "UNKNOWN") && extractedCodec != null && extractedCodec != "UNKNOWN") {
                codec = extractedCodec
            }
        }

        streamMetadata.value = com.example.data.model.MediaTrackInfo(
            title = resolvedTitle,
            artist = resolvedArtist,
            bitrate = bitrate,
            codec = codec
        )
    }

    fun playStream(url: String, name: String, imageUrl: String?, bitrate: Int? = null, codec: String? = null, playWhenReady: Boolean = true) {
        val controller = mediaController ?: return
        
        val extras = android.os.Bundle().apply {
            if (bitrate != null) putInt("bitrate", bitrate)
            if (codec != null) putString("codec", codec)
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(name)
            .setArtist("Internet Radio")
            .setStation(name)
            .setArtworkUri(imageUrl?.let { android.net.Uri.parse(it) })
            .setExtras(extras)
            .build()

        val mediaItem = MediaItem.Builder()
            .setMediaId(url)
            .setUri(url)
            .setMediaMetadata(metadata)
            .build()

        controller.setMediaItem(mediaItem)
        controller.prepare()
        if (playWhenReady) {
            controller.play()
        }
    }

    fun play() {
        mediaController?.play()
    }

    fun pause() {
        mediaController?.pause()
    }

    fun stop() {
        mediaController?.stop()
    }

    fun release() {
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
    }
}
