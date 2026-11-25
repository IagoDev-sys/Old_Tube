package com.example.data.local

data class SavedVideoEntity(
    val videoId: String,
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
