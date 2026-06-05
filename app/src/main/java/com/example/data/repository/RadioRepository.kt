package com.example.data.repository

import com.example.data.api.RadioBrowserApi
import com.example.data.api.SomaFmApi
import com.example.data.db.FavoriteDao
import com.example.data.db.SongHistoryDao
import com.example.data.db.LikedSongDao
import com.example.data.model.FavoriteStation
import com.example.data.model.RadioBrowserStation
import com.example.data.model.SomaChannel
import com.example.data.model.HistoryEntity
import com.example.data.model.LikedSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class RadioRepository(
    private val radioBrowserApi: RadioBrowserApi,
    private val somaFmApi: SomaFmApi,
    private val favoriteDao: FavoriteDao,
    private val songHistoryDao: SongHistoryDao,
    private val likedSongDao: LikedSongDao
) {
    // --- Database Liked Songs ---
    val allLikedSongs: Flow<List<LikedSong>> = likedSongDao.getAllLikedSongs()
        .flowOn(Dispatchers.IO)

    suspend fun addLikedSong(song: LikedSong) = withContext(Dispatchers.IO) {
        likedSongDao.insertLikedSong(song)
    }

    suspend fun removeLikedSong(artist: String, title: String) = withContext(Dispatchers.IO) {
        likedSongDao.deleteLikedSong(artist, title)
    }

    fun isSongLiked(artist: String, title: String): Flow<Boolean> {
        return likedSongDao.isSongLiked(artist, title).flowOn(Dispatchers.IO)
    }

    fun getLikedSong(artist: String, title: String): Flow<LikedSong?> {
        return likedSongDao.getLikedSong(artist, title).flowOn(Dispatchers.IO)
    }

    // --- Database Favorites ---
    val allFavorites: Flow<List<FavoriteStation>> = favoriteDao.getAllFavorites()
        .flowOn(Dispatchers.IO)

    // --- Song History ---
    val allHistory: Flow<List<HistoryEntity>> = songHistoryDao.getHistory()
        .flowOn(Dispatchers.IO)

    suspend fun addSongToHistory(song: HistoryEntity) = withContext(Dispatchers.IO) {
        songHistoryDao.insertAndTrimHistory(song)
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        songHistoryDao.clearHistory()
    }

    suspend fun addFavorite(station: FavoriteStation) = withContext(Dispatchers.IO) {
        favoriteDao.insertFavorite(station)
    }

    suspend fun updateFavoriteStationsOrder(newList: List<FavoriteStation>) = withContext(Dispatchers.IO) {
        favoriteDao.insertAllFavorites(newList)
    }

    suspend fun removeFavorite(station: FavoriteStation) = withContext(Dispatchers.IO) {
        favoriteDao.deleteFavorite(station)
    }

    suspend fun removeFavoriteById(id: String) = withContext(Dispatchers.IO) {
        favoriteDao.deleteFavoriteById(id)
    }

    suspend fun updateFavoriteName(stationId: String, newName: String) = withContext(Dispatchers.IO) {
        favoriteDao.updateStationName(stationId, newName)
    }

    fun isFavorite(id: String): Flow<Boolean> {
        return favoriteDao.isFavorite(id).flowOn(Dispatchers.IO)
    }

    // --- Network Radio Browser API ---
    suspend fun getCountries(): List<com.example.data.model.CountryDto> = withContext(Dispatchers.IO) {
        try {
            radioBrowserApi.getCountries()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getStationsByCountry(country: String): List<RadioBrowserStation> = withContext(Dispatchers.IO) {
        try {
            radioBrowserApi.getStationsByCountry(country)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getStationsByTag(tag: String): List<RadioBrowserStation> = withContext(Dispatchers.IO) {
        try {
            radioBrowserApi.getStationsByTag(tag, limit = 100)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getTopStations(): List<RadioBrowserStation> = withContext(Dispatchers.IO) {
        try {
            radioBrowserApi.getTopClickedStations(limit = 100)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun searchStations(query: String, countryCode: String? = null, order: String? = null): List<RadioBrowserStation> = withContext(Dispatchers.IO) {
        try {
            val reverseValue = if (order != null && order != "name") true else null
            if (query.isBlank()) {
                if (countryCode != null) {
                    radioBrowserApi.searchStations(countryCode = countryCode, limit = 100, order = order, reverse = reverseValue)
                } else {
                    if (order != null) {
                        radioBrowserApi.searchStations(limit = 100, order = order, reverse = reverseValue)
                    } else {
                        getTopStations()
                    }
                }
            } else {
                radioBrowserApi.searchStations(name = query, countryCode = countryCode, limit = 100, order = order, reverse = reverseValue)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // --- Network Soma FM API ---
    suspend fun getSomaChannels(): List<SomaChannel> = withContext(Dispatchers.IO) {
        try {
            somaFmApi.getChannels().channels ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
