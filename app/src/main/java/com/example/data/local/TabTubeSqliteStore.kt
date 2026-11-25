package com.example.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class TabTubeSqliteStore(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    private val lock = Any()
    private val _channelsFlow = MutableStateFlow<List<ChannelEntity>>(emptyList())
    private val _bookmarksFlow = MutableStateFlow<List<SavedVideoEntity>>(emptyList())
    private val _historyFlow = MutableStateFlow<List<SavedVideoEntity>>(emptyList())

    init {
        refreshFlowsLocked()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS channels (
                channelId TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                category TEXT NOT NULL,
                addedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS saved_videos (
                videoId TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                channelName TEXT NOT NULL,
                channelId TEXT NOT NULL,
                thumbnailUrl TEXT NOT NULL,
                durationText TEXT NOT NULL,
                publishedAt TEXT NOT NULL,
                isBookmarked INTEGER NOT NULL DEFAULT 0,
                lastPlayedAt INTEGER NOT NULL DEFAULT 0,
                lastPositionMs INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Schema v1
    }

    fun observeChannels(): Flow<List<ChannelEntity>> = _channelsFlow.asStateFlow()
    fun observeBookmarks(): Flow<List<SavedVideoEntity>> = _bookmarksFlow.asStateFlow()
    fun observeHistory(): Flow<List<SavedVideoEntity>> = _historyFlow.asStateFlow()

    fun getChannelsOnce(): List<ChannelEntity> = synchronized(lock) {
        queryChannelsLocked(readableDatabase)
    }

    fun insertChannels(channels: List<ChannelEntity>) = synchronized(lock) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (c in channels) {
                db.insertWithOnConflict("channels", null, c.toContentValues(), SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        refreshFlowsLocked()
    }

    fun insertChannel(channel: ChannelEntity) = synchronized(lock) {
        writableDatabase.insertWithOnConflict(
            "channels",
            null,
            channel.toContentValues(),
            SQLiteDatabase.CONFLICT_REPLACE
        )
        refreshFlowsLocked()
    }

    fun deleteChannel(channelId: String) = synchronized(lock) {
        writableDatabase.delete("channels", "channelId = ?", arrayOf(channelId))
        refreshFlowsLocked()
    }

    fun getSavedVideo(videoId: String): SavedVideoEntity? = synchronized(lock) {
        readableDatabase.rawQuery(
            "SELECT * FROM saved_videos WHERE videoId = ? LIMIT 1",
            arrayOf(videoId)
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toSavedVideo() else null
        }
    }

    fun upsertSavedVideo(video: SavedVideoEntity) = synchronized(lock) {
        writableDatabase.insertWithOnConflict(
            "saved_videos",
            null,
            video.toContentValues(),
            SQLiteDatabase.CONFLICT_REPLACE
        )
        refreshFlowsLocked()
    }

    fun clearWatchHistory() = synchronized(lock) {
        writableDatabase.delete("saved_videos", "lastPlayedAt > 0 AND isBookmarked = 0", null)
        refreshFlowsLocked()
    }

    private fun refreshFlowsLocked() {
        val db = readableDatabase
        _channelsFlow.value = queryChannelsLocked(db)
        _bookmarksFlow.value = querySavedVideosLocked(
            db,
            "SELECT * FROM saved_videos WHERE isBookmarked = 1 ORDER BY lastPlayedAt DESC, videoId ASC"
        )
        _historyFlow.value = querySavedVideosLocked(
            db,
            "SELECT * FROM saved_videos WHERE lastPlayedAt > 0 ORDER BY lastPlayedAt DESC LIMIT 40"
        )
    }

    private fun queryChannelsLocked(db: SQLiteDatabase): List<ChannelEntity> {
        val list = mutableListOf<ChannelEntity>()
        db.rawQuery("SELECT * FROM channels ORDER BY addedAt ASC", null).use { c ->
            val idIdx = c.getColumnIndexOrThrow("channelId")
            val nameIdx = c.getColumnIndexOrThrow("name")
            val catIdx = c.getColumnIndexOrThrow("category")
            val addedIdx = c.getColumnIndexOrThrow("addedAt")
            while (c.moveToNext()) {
                list.add(
                    ChannelEntity(
                        channelId = c.getString(idIdx),
                        name = c.getString(nameIdx),
                        category = c.getString(catIdx),
                        addedAt = c.getLong(addedIdx)
                    )
                )
            }
        }
        return list
    }

    private fun querySavedVideosLocked(db: SQLiteDatabase, sql: String): List<SavedVideoEntity> {
        val list = mutableListOf<SavedVideoEntity>()
        db.rawQuery(sql, null).use { c ->
            while (c.moveToNext()) {
                list.add(c.toSavedVideo())
            }
        }
        return list
    }

    private fun android.database.Cursor.toSavedVideo(): SavedVideoEntity {
        return SavedVideoEntity(
            videoId = getString(getColumnIndexOrThrow("videoId")),
            title = getString(getColumnIndexOrThrow("title")),
            channelName = getString(getColumnIndexOrThrow("channelName")),
            channelId = getString(getColumnIndexOrThrow("channelId")),
            thumbnailUrl = getString(getColumnIndexOrThrow("thumbnailUrl")),
            durationText = getString(getColumnIndexOrThrow("durationText")),
            publishedAt = getString(getColumnIndexOrThrow("publishedAt")),
            isBookmarked = getInt(getColumnIndexOrThrow("isBookmarked")) == 1,
            lastPlayedAt = getLong(getColumnIndexOrThrow("lastPlayedAt")),
            lastPositionMs = getLong(getColumnIndexOrThrow("lastPositionMs"))
        )
    }

    private fun ChannelEntity.toContentValues() = ContentValues().apply {
        put("channelId", channelId)
        put("name", name)
        put("category", category)
        put("addedAt", addedAt)
    }

    private fun SavedVideoEntity.toContentValues() = ContentValues().apply {
        put("videoId", videoId)
        put("title", title)
        put("channelName", channelName)
        put("channelId", channelId)
        put("thumbnailUrl", thumbnailUrl)
        put("durationText", durationText)
        put("publishedAt", publishedAt)
        put("isBookmarked", if (isBookmarked) 1 else 0)
        put("lastPlayedAt", lastPlayedAt)
        put("lastPositionMs", lastPositionMs)
    }

    companion object {
        private const val DATABASE_NAME = "tabtube_local.db"
        private const val DATABASE_VERSION = 1

        @Volatile
        private var INSTANCE: TabTubeSqliteStore? = null

        val DEFAULT_CHANNELS = listOf(
            ChannelEntity("UCKHhA5hN2UohhFDfNXB_cvQ", "Manual do Mundo", "Ciência", 1L),
            ChannelEntity("UCsXVk37bltHxD1rDPwtNM8Q", "Kurzgesagt", "Ciência", 2L),
            ChannelEntity("UCsooa4yRKGN_zEE8iknghZA", "TED-Ed", "Educação", 3L),
            ChannelEntity("UC9-y-6csu5WGm29I7JiwpnA", "Computerphile", "Tecnologia", 4L),
            ChannelEntity("UCLA_DiR1FfKNvjuUpBHmylQ", "NASA", "Espaço", 5L),
            ChannelEntity("UC_x5XG1OV2P6uZZ5FSM9Ttw", "Android Developers", "Tecnologia", 6L)
        )

        fun getInstance(context: Context): TabTubeSqliteStore {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TabTubeSqliteStore(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
