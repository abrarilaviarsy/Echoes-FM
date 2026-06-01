package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.data.model.HistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SongHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryItem(item: HistoryEntity)

    @Query("DELETE FROM history_table WHERE trackTitle = :trackTitle AND artist = :artist")
    suspend fun deleteHistoryItem(trackTitle: String, artist: String)

    @Query("SELECT * FROM history_table ORDER BY timestamp DESC LIMIT 50")
    fun getHistory(): Flow<List<HistoryEntity>>

    @Query("DELETE FROM history_table")
    suspend fun clearHistory()

    @Query("DELETE FROM history_table WHERE id NOT IN (SELECT id FROM history_table ORDER BY timestamp DESC LIMIT 50)")
    suspend fun trimHistory()

    @Transaction
    suspend fun insertAndTrimHistory(item: HistoryEntity) {
        deleteHistoryItem(item.trackTitle, item.artist)
        insertHistoryItem(item)
        trimHistory()
    }
}
