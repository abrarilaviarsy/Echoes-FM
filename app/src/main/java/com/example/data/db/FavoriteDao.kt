package com.example.data.db

import androidx.room.*
import com.example.data.model.FavoriteStation
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorite_stations ORDER BY sortOrder ASC, timestamp DESC")
    fun getAllFavorites(): Flow<List<FavoriteStation>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(station: FavoriteStation)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllFavorites(stations: List<FavoriteStation>)

    @Delete
    suspend fun deleteFavorite(station: FavoriteStation)

    @Query("DELETE FROM favorite_stations WHERE id = :id")
    suspend fun deleteFavoriteById(id: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_stations WHERE id = :id)")
    fun isFavorite(id: String): Flow<Boolean>

    @Query("UPDATE favorite_stations SET name = :newName WHERE id = :stationId")
    suspend fun updateStationName(stationId: String, newName: String)
}
