package com.example.data.repository

import com.example.data.local.ChannelEntity
import com.example.data.local.OptimizationPreferences
import com.example.data.local.SavedVideoEntity
import com.example.data.local.TabTubeDao
import com.example.data.local.TabTubeDatabase
import com.example.data.model.BridgePingResult
import com.example.data.model.DeviceTelemetry
import com.example.data.model.OptimizationSettings
import com.example.data.model.PlaybackEngineMode
import com.example.data.model.QualityPreset
import com.example.data.model.ResolvedStream
import com.example.data.model.VideoItem
import com.example.data.remote.YouTubeLegacyNetwork
import com.example.data.telemetry.DeviceTelemetryProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class TabTubeRepository(
    private val dao: TabTubeDao,
    private val preferences: OptimizationPreferences,
    private val network: YouTubeLegacyNetwork,
    private val telemetryProvider: DeviceTelemetryProvider
) {
    val channelsFlow: Flow<List<ChannelEntity>> = dao.observeChannels()
    val bookmarksFlow: Flow<List<SavedVideoEntity>> = dao.observeBookmarks()
    val historyFlow: Flow<List<SavedVideoEntity>> = dao.observeHistory()
    val settingsFlow: Flow<OptimizationSettings> = preferences.settingsFlow

    suspend fun ensureSeedChannels() {
        if (dao.getChannelsOnce().isEmpty()) {
            dao.insertChannels(TabTubeDatabase.DEFAULT_CHANNELS)
        }
    }

    suspend fun fetchFeed(channelFilterId: String? = null): List<VideoItem> {
        ensureSeedChannels()
        val settings = settingsFlow.first()
        val channels = dao.getChannelsOnce()
        val targetIds = if (channelFilterId != null) {
            listOf(channelFilterId)
        } else {
            channels.map { it.channelId }
        }
        return network.fetchChannelFeeds(targetIds, settings.lowRamThumbnails)
    }

    suspend fun search(query: String): List<VideoItem> {
        val settings = settingsFlow.first()
        return network.searchVideos(query, settings.lowRamThumbnails, settings.customBridgeUrl)
    }

    fun extractVideoId(input: String): String? = network.extractVideoId(input)

    suspend fun addChannelFromInput(rawInput: String): Result<ChannelEntity> {
        val resolved = network.resolveChannelFromInput(rawInput)
            ?: return Result.failure(IllegalArgumentException("Não foi possível localizar o canal. Use @handle, link do canal ou ID UC..."))
        val entity = ChannelEntity(
            channelId = resolved.first,
            name = resolved.second,
            category = "Personalizado"
        )
        dao.insertChannel(entity)
        return Result.success(entity)
    }

    suspend fun removeChannel(channelId: String) {
        dao.deleteChannel(channelId)
    }

    suspend fun resolveStream(videoId: String, knownVideo: VideoItem?): ResolvedStream {
        val settings = settingsFlow.first()
        return network.resolvePlayableStream(videoId, knownVideo, settings)
    }

    suspend fun recordWatchStart(video: VideoItem) {
        val existing = dao.getSavedVideo(video.id)
        dao.upsertSavedVideo(
            SavedVideoEntity(
                videoId = video.id,
                title = video.title,
                channelName = video.channelName,
                channelId = video.channelId,
                thumbnailUrl = video.thumbnailUrl,
                durationText = video.durationText,
                publishedAt = video.publishedAt,
                isBookmarked = existing?.isBookmarked ?: false,
                lastPlayedAt = System.currentTimeMillis(),
                lastPositionMs = existing?.lastPositionMs ?: 0L
            )
        )
    }

    suspend fun updateWatchPosition(videoId: String, positionMs: Long) {
        val existing = dao.getSavedVideo(videoId) ?: return
        dao.upsertSavedVideo(existing.copy(lastPositionMs = positionMs, lastPlayedAt = System.currentTimeMillis()))
    }

    suspend fun toggleBookmark(video: VideoItem) {
        val existing = dao.getSavedVideo(video.id)
        if (existing != null) {
            dao.upsertSavedVideo(existing.copy(isBookmarked = !existing.isBookmarked))
        } else {
            dao.upsertSavedVideo(
                SavedVideoEntity(
                    videoId = video.id,
                    title = video.title,
                    channelName = video.channelName,
                    channelId = video.channelId,
                    thumbnailUrl = video.thumbnailUrl,
                    durationText = video.durationText,
                    publishedAt = video.publishedAt,
                    isBookmarked = true,
                    lastPlayedAt = 0L,
                    lastPositionMs = 0L
                )
            )
        }
    }

    suspend fun clearWatchHistory() {
        dao.clearWatchHistory()
    }

    fun collectTelemetry(): DeviceTelemetry = telemetryProvider.collect()

    suspend fun pingBridges(): List<BridgePingResult> {
        val settings = settingsFlow.first()
        return network.pingBridgeInstances(settings.customBridgeUrl)
    }

    suspend fun updateQuality(preset: QualityPreset) = preferences.updateQuality(preset)
    suspend fun updateEngine(mode: PlaybackEngineMode) = preferences.updateEngine(mode)
    suspend fun updateLowRamThumbnails(enabled: Boolean) = preferences.updateLowRamThumbnails(enabled)
    suspend fun updateDarkTheme(enabled: Boolean) = preferences.updateDarkTheme(enabled)
    suspend fun updateCustomBridgeUrl(url: String) = preferences.updateCustomBridgeUrl(url)
}
