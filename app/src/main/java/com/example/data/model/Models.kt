package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

// --- Backup & Restore Model ---
data class BackupData(
    val favoriteStations: List<FavoriteStation> = emptyList(),
    val likedSongs: List<LikedSong> = emptyList()
)

// --- Country representation ---
data class CountryDto(
    val name: String,
    val stationcount: Int
)

// --- Song playing History ---
@Entity(tableName = "history_table")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val stationName: String,
    val trackTitle: String,
    val timestamp: Long = System.currentTimeMillis(),
    val artist: String = "",
    val coverUrl: String? = null
) {
    // UI compatibility layer
    val title: String get() = trackTitle
}

// --- Liked Song ---
@Entity(tableName = "liked_songs")
data class LikedSong(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val artist: String,
    val title: String,
    val coverUrl: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

// --- Media Track Info ---
data class MediaTrackInfo(
    val title: String,
    val artist: String,
    val bitrate: Int? = null,
    val codec: String? = null
)

// --- Radio Browser API Response Model ---
data class RadioBrowserStation(
    val stationuuid: String,
    val name: String,
    val url: String,
    val url_resolved: String?,
    val favicon: String?,
    val tags: String?,
    val country: String?,
    val language: String?,
    val clickcount: Int = 0,
    val codec: String? = null,
    val bitrate: Int? = null
) {
    val highResUrl: String get() = url_resolved ?: url
    val lowResUrl: String get() = url_resolved ?: url

    fun toFavorite(): FavoriteStation {
        return FavoriteStation(
            id = stationuuid,
            name = name,
            streamUrl = url_resolved ?: url,
            faviconUrl = favicon,
            sourceType = "radio_browser",
            description = tags?.split(",")?.take(2)?.joinToString(", ") ?: "Radio Browser Station",
            genre = tags ?: "",
            bitrate = bitrate,
            codec = codec
        )
    }
}

// --- Soma FM API Response Models ---
data class SomaFmResponse(
    val channels: List<SomaChannel>?
)

data class SomaChannel(
    val id: String,
    val title: String,
    val description: String,
    val image: String?,
    val xlimage: String?,
    val dj: String?,
    val genre: String?
) {
    val highResUrl: String get() = "https://ice1.somafm.com/$id-128-mp3"
    val lowResUrl: String get() = "https://ice1.somafm.com/$id-64-aac"

    fun toFavorite(): FavoriteStation {
        return FavoriteStation(
            id = id,
            name = title,
            streamUrl = "https://ice1.somafm.com/$id-128-mp3",
            faviconUrl = xlimage ?: image,
            sourceType = "soma_fm",
            description = description,
            genre = genre ?: "",
            bitrate = 128,
            codec = "mp3"
        )
    }
}

// --- Room Database Favorite Entity ---
@Entity(tableName = "favorite_stations")
data class FavoriteStation(
    @PrimaryKey val id: String,
    val name: String,
    val streamUrl: String,
    val faviconUrl: String?,
    val sourceType: String, // "radio_browser" or "soma_fm"
    val description: String,
    val genre: String,
    val timestamp: Long = System.currentTimeMillis(),
    val bitrate: Int? = null,
    val codec: String? = null,
    val sortOrder: Int = 0
) {
    val highResUrl: String get() = if (sourceType == "soma_fm") "https://ice1.somafm.com/$id-128-mp3" else streamUrl
    val lowResUrl: String get() = if (sourceType == "soma_fm") "https://ice1.somafm.com/$id-64-aac" else streamUrl
}

// --- Dynamic Quality Fallbacks ---
data class StreamMetadata(
    val bitrate: Int?,
    val codec: String?,
    val title: String?,
    val artist: String?
)

data class SongInfo(
    val title: String?,
    val artist: String?
)
