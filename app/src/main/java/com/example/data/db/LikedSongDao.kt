package com.example.data.db

import androidx.room.*
import com.example.data.model.LikedSong
import kotlinx.coroutines.flow.Flow

@Dao
interface LikedSongDao {
    @Query("SELECT * FROM liked_songs ORDER BY timestamp DESC")
    fun getAllLikedSongs(): Flow<List<LikedSong>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLikedSong(song: LikedSong)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllLikedSongs(songs: List<LikedSong>)

    @Query("DELETE FROM liked_songs WHERE artist = :artist AND title = :title")
    suspend fun deleteLikedSong(artist: String, title: String)

    @Query("SELECT EXISTS(SELECT 1 FROM liked_songs WHERE artist = :artist AND title = :title)")
    fun isSongLiked(artist: String, title: String): Flow<Boolean>

    @Query("SELECT * FROM liked_songs WHERE artist = :artist AND title = :title LIMIT 1")
    fun getLikedSong(artist: String, title: String): Flow<LikedSong?>
}
