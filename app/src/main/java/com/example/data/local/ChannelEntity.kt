package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey val channelId: String,
    val name: String,
    val category: String,
    val addedAt: Long = System.currentTimeMillis()
)
