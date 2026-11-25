package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TabTubeDao {
    @Query("SELECT * FROM channels ORDER BY addedAt ASC")
    fun observeChannels(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels ORDER BY addedAt ASC")
    suspend fun getChannelsOnce(): List<ChannelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<ChannelEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannel(channel: ChannelEntity)

    @Query("DELETE FROM channels WHERE channelId = :channelId")
    suspend fun deleteChannel(channelId: String)

    @Query("SELECT * FROM saved_videos WHERE isBookmarked = 1 ORDER BY lastPlayedAt DESC, videoId ASC")
    fun observeBookmarks(): Flow<List<SavedVideoEntity>>

    @Query("SELECT * FROM saved_videos WHERE lastPlayedAt > 0 ORDER BY lastPlayedAt DESC LIMIT 40")
    fun observeHistory(): Flow<List<SavedVideoEntity>>

    @Query("SELECT * FROM saved_videos WHERE videoId = :videoId LIMIT 1")
    suspend fun getSavedVideo(videoId: String): SavedVideoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSavedVideo(video: SavedVideoEntity)

    @Query("DELETE FROM saved_videos WHERE lastPlayedAt > 0 AND isBookmarked = 0")
    suspend fun clearWatchHistory()
}
