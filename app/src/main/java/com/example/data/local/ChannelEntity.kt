package com.example.data.local

data class ChannelEntity(
    val channelId: String,
    val name: String,
    val category: String,
    val addedAt: Long = System.currentTimeMillis()
)
