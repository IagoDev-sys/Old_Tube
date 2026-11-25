package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_videos")
data class SavedVideoEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val channelName: String,
    val channelId: String,
    val thumbnailUrl: String,
    val durationText: String,
    val publishedAt: String,
    val isBookmarked: Boolean = false,
    val lastPlayedAt: Long = 0L,
    val lastPositionMs: Long = 0L
)
