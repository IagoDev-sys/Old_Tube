package com.example.data.repository

import com.example.data.local.ChannelEntity
import com.example.data.local.OptimizationPreferences
import com.example.data.local.SavedVideoEntity
import com.example.data.local.TabTubeSqliteStore
import com.example.data.model.BridgePingResult
import com.example.data.model.DeviceTelemetry
import com.example.data.model.OptimizationSettings
import com.example.data.model.PlaybackEngineMode
import com.example.data.model.QualityPreset
import com.example.data.model.ResolvedStream
import com.example.data.model.VideoItem
import com.example.data.remote.LocalTlsStreamProxy
import com.example.data.remote.YouTubeLegacyNetwork
import com.example.data.telemetry.DeviceTelemetryProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class TabTubeRepository(
    private val store: TabTubeSqliteStore,
    private val preferences: OptimizationPreferences,
    private val network: YouTubeLegacyNetwork,
    private val telemetryProvider: DeviceTelemetryProvider,
    private val localProxy: LocalTlsStreamProxy = LocalTlsStreamProxy()
) {
    val settingsFlow: Flow<OptimizationSettings> = preferences.settingsFlow
    val channelsFlow: Flow<List<ChannelEntity>> = store.observeChannels()
    val bookmarksFlow: Flow<List<SavedVideoEntity>> = store.observeBookmarks()
    val historyFlow: Flow<List<SavedVideoEntity>> = store.observeHistory()

    fun ensureSeedChannels() {
        if (store.getChannelsOnce().isEmpty()) {
            store.insertChannels(TabTubeSqliteStore.DEFAULT_CHANNELS)
        }
    }

    suspend fun fetchFeedForChannels(channelFilterId: String? = null): List<VideoItem> {
        ensureSeedChannels()
        val settings = settingsFlow.first()
        val ids = if (channelFilterId != null) {
            listOf(channelFilterId)
        } else {
            store.getChannelsOnce().map { it.channelId }
        }
        return network.fetchChannelFeeds(ids, settings.lowRamThumbnails)
    }

    suspend fun search(query: String): List<VideoItem> {
        val settings = settingsFlow.first()
        return network.searchVideos(
            query = query,
            lowRamThumbnails = settings.lowRamThumbnails,
            customBridgeUrl = settings.customBridgeUrl,
            allowLanBridge = settings.allowLanBridge
        )
    }

    fun extractVideoId(input: String): String? = network.extractVideoId(input)

    suspend fun addChannelFromInput(rawInput: String): Result<ChannelEntity> {
        val resolved = network.resolveChannelFromInput(rawInput)
            ?: return Result.failure(
                IllegalArgumentException("Não foi possível localizar o canal. Use @handle, link do canal ou ID UC...")
            )
        val entity = ChannelEntity(
            channelId = resolved.first,
            name = resolved.second,
            category = "Personalizado"
        )
        store.insertChannel(entity)
        return Result.success(entity)
    }

    fun subscribeToChannel(channelId: String, channelName: String) {
        if (channelId.isBlank()) return
        store.insertChannel(
            ChannelEntity(
                channelId = channelId,
                name = channelName.ifBlank { "Canal" },
                category = "Inscrito"
            )
        )
    }

    fun removeChannel(channelId: String) {
        store.deleteChannel(channelId)
    }

    suspend fun resolveStream(
        videoId: String,
        knownVideo: VideoItem?,
        isShort: Boolean = false
    ): ResolvedStream {
        val settings = settingsFlow.first()
        val resolved = network.resolvePlayableStream(videoId, knownVideo, settings, isShort)
        val direct = resolved.directH264Url
        return if (!direct.isNullOrBlank()) {
            val proxied = localProxy.registerStreamUrl(direct)
            resolved.copy(directH264Url = proxied)
        } else {
            resolved
        }
    }

    fun recordWatchStart(video: VideoItem): Long {
        val existing = store.getSavedVideo(video.id)
        val now = System.currentTimeMillis()
        val thumb = video.thumbnailUrl.ifBlank {
            "https://i.ytimg.com/vi/${video.id}/hqdefault.jpg"
        }
        store.upsertSavedVideo(
            SavedVideoEntity(
                videoId = video.id,
                title = video.title,
                channelName = video.channelName,
                channelId = video.channelId,
                thumbnailUrl = thumb,
                durationText = video.durationText,
                publishedAt = video.publishedAt,
                isBookmarked = existing?.isBookmarked ?: false,
                lastPlayedAt = now,
                lastPositionMs = existing?.lastPositionMs ?: 0L
            )
        )
        return existing?.lastPositionMs ?: 0L
    }

    fun updateWatchPosition(videoId: String, positionMs: Long) {
        val existing = store.getSavedVideo(videoId) ?: return
        store.upsertSavedVideo(
            existing.copy(
                lastPlayedAt = System.currentTimeMillis(),
                lastPositionMs = positionMs.coerceAtLeast(0L)
            )
        )
    }

    fun toggleBookmark(video: VideoItem) {
        val existing = store.getSavedVideo(video.id)
        val thumb = video.thumbnailUrl.ifBlank {
            "https://i.ytimg.com/vi/${video.id}/hqdefault.jpg"
        }
        if (existing != null) {
            store.upsertSavedVideo(existing.copy(isBookmarked = !existing.isBookmarked))
        } else {
            store.upsertSavedVideo(
                SavedVideoEntity(
                    videoId = video.id,
                    title = video.title,
                    channelName = video.channelName,
                    channelId = video.channelId,
                    thumbnailUrl = thumb,
                    durationText = video.durationText,
                    publishedAt = video.publishedAt,
                    isBookmarked = true,
                    lastPlayedAt = 0L,
                    lastPositionMs = 0L
                )
            )
        }
    }

    fun clearWatchHistory() = store.clearWatchHistory()

    fun collectTelemetry(): DeviceTelemetry = telemetryProvider.collect()

    suspend fun pingBridges(): List<BridgePingResult> {
        val settings = settingsFlow.first()
        return network.pingBridgeInstances(settings.customBridgeUrl, settings.allowLanBridge)
    }

    fun updateQuality(preset: QualityPreset) = preferences.updateQuality(preset)
    fun updateEngine(mode: PlaybackEngineMode) = preferences.updateEngine(mode)
    fun updateLowRamThumbnails(enabled: Boolean) = preferences.updateLowRamThumbnails(enabled)
    fun updateClassicLightTheme(enabled: Boolean) = preferences.updateClassicLightTheme(enabled)
    fun updateAllowLanBridge(enabled: Boolean) = preferences.updateAllowLanBridge(enabled)
    fun updateCustomBridgeUrl(url: String): Boolean = preferences.updateCustomBridgeUrl(url)

    fun releaseProxy() = localProxy.stop()
}
