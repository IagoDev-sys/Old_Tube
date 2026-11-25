package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ChannelEntity
import com.example.data.local.SavedVideoEntity
import com.example.data.model.ActivePlayerBackend
import com.example.data.model.BridgePingResult
import com.example.data.model.DeviceTelemetry
import com.example.data.model.OptimizationSettings
import com.example.data.model.PlaybackEngineMode
import com.example.data.model.QualityPreset
import com.example.data.model.ResolvedStream
import com.example.data.model.VideoItem
import com.example.data.repository.TabTubeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface FeedUiState {
    data object Loading : FeedUiState
    data class Success(val videos: List<VideoItem>) : FeedUiState
    data class Error(val message: String) : FeedUiState
}

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Success(val videos: List<VideoItem>, val query: String) : SearchUiState
    data class Error(val message: String) : SearchUiState
}

sealed interface PlayerUiState {
    data object Idle : PlayerUiState
    data class Loading(val videoId: String, val title: String) : PlayerUiState
    data class Ready(
        val stream: ResolvedStream,
        val activeBackend: ActivePlayerBackend,
        val resumePositionMs: Long
    ) : PlayerUiState
    data class Error(val message: String, val videoId: String) : PlayerUiState
}

class MainViewModel(
    private val repository: TabTubeRepository
) : ViewModel() {

    val channels: StateFlow<List<ChannelEntity>> = repository.channelsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val bookmarks: StateFlow<List<SavedVideoEntity>> = repository.bookmarksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val history: StateFlow<List<SavedVideoEntity>> = repository.historyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val bookmarkedIds: StateFlow<Set<String>> = bookmarks
        .map { list -> list.map { it.videoId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptySet())

    val followedChannelIds: StateFlow<Set<String>> = channels
        .map { list -> list.map { it.channelId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptySet())

    val settings: StateFlow<OptimizationSettings> = repository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), OptimizationSettings())

    private val _selectedChannelId = MutableStateFlow<String?>(null)
    val selectedChannelId: StateFlow<String?> = _selectedChannelId.asStateFlow()

    private val _feedState = MutableStateFlow<FeedUiState>(FeedUiState.Loading)
    val feedState: StateFlow<FeedUiState> = _feedState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val searchState: StateFlow<SearchUiState> = _searchState.asStateFlow()

    private val _playerState = MutableStateFlow<PlayerUiState>(PlayerUiState.Idle)
    val playerState: StateFlow<PlayerUiState> = _playerState.asStateFlow()

    private val _telemetry = MutableStateFlow(repository.collectTelemetry())
    val telemetry: StateFlow<DeviceTelemetry> = _telemetry.asStateFlow()

    private val _pingResults = MutableStateFlow<List<BridgePingResult>>(emptyList())
    val pingResults: StateFlow<List<BridgePingResult>> = _pingResults.asStateFlow()

    private val _isPinging = MutableStateFlow(false)
    val isPinging: StateFlow<Boolean> = _isPinging.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        refreshFeed()
    }

    fun selectChannelFilter(channelId: String?) {
        _selectedChannelId.value = channelId
        refreshFeed()
    }

    fun refreshFeed() {
        viewModelScope.launch {
            _feedState.value = FeedUiState.Loading
            runCatching {
                repository.fetchFeedForChannels(_selectedChannelId.value)
            }.onSuccess { videos ->
                if (videos.isEmpty()) {
                    _feedState.value = FeedUiState.Error(
                        "Nenhum vídeo retornado pelos feeds RSS no momento. Verifique sua conexão e tente novamente."
                    )
                } else {
                    _feedState.value = FeedUiState.Success(videos)
                }
            }.onFailure { err ->
                _feedState.value = FeedUiState.Error(
                    err.localizedMessage ?: "Falha ao carregar feeds Atom/RSS."
                )
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun executeSearch(explicitQuery: String? = null) {
        val q = (explicitQuery ?: _searchQuery.value).trim()
        if (q.isBlank()) return
        _searchQuery.value = q

        val directId = repository.extractVideoId(q)
        if (directId != null && (q.contains("http") || q.contains("youtu") || q.length == 11)) {
            openVideoById(directId, q.contains("/shorts/", ignoreCase = true))
            return
        }

        viewModelScope.launch {
            _searchState.value = SearchUiState.Loading
            runCatching {
                repository.search(q)
            }.onSuccess { results ->
                if (results.isEmpty()) {
                    _searchState.value = SearchUiState.Error("Nenhum resultado encontrado para \"$q\".")
                } else {
                    _searchState.value = SearchUiState.Success(results, q)
                }
            }.onFailure { err ->
                _searchState.value = SearchUiState.Error(
                    err.localizedMessage ?: "Erro de rede ao buscar vídeos."
                )
            }
        }
    }

    fun openVideo(video: VideoItem) {
        viewModelScope.launch {
            _playerState.value = PlayerUiState.Loading(video.id, video.title)
            runCatching {
                val resumePos = repository.recordWatchStart(video)
                val resolved = repository.resolveStream(video.id, video, video.isShort)
                resolved to resumePos
            }.onSuccess { (resolved, resumePos) ->
                val initialBackend = chooseInitialBackend(resolved, settings.value.engineMode)
                _playerState.value = PlayerUiState.Ready(
                    stream = resolved,
                    activeBackend = initialBackend,
                    resumePositionMs = resumePos
                )
            }.onFailure { err ->
                _playerState.value = PlayerUiState.Error(
                    message = err.localizedMessage ?: "Não foi possível preparar o stream.",
                    videoId = video.id
                )
            }
        }
    }

    fun extractVideoId(input: String): String? = repository.extractVideoId(input)

    fun openVideoByInput(rawInput: String): Boolean {
        val videoId = repository.extractVideoId(rawInput) ?: return false
        val isShort = rawInput.contains("/shorts/", ignoreCase = true)
        openVideoById(videoId, isShort)
        return true
    }

    fun openVideoById(videoId: String, isShort: Boolean = false) {
        viewModelScope.launch {
            _playerState.value = PlayerUiState.Loading(videoId, "Carregando vídeo...")
            runCatching {
                val resolved = repository.resolveStream(videoId, null, isShort)
                val item = VideoItem(
                    id = resolved.videoId,
                    title = resolved.title,
                    channelName = resolved.channelName,
                    channelId = resolved.channelId,
                    thumbnailUrl = resolved.thumbnailUrl,
                    description = resolved.description,
                    sourceTag = resolved.resolvedVia,
                    isShort = resolved.isShort
                )
                val resumePos = repository.recordWatchStart(item)
                resolved to resumePos
            }.onSuccess { (resolved, resumePos) ->
                val initialBackend = chooseInitialBackend(resolved, settings.value.engineMode)
                _playerState.value = PlayerUiState.Ready(
                    stream = resolved,
                    activeBackend = initialBackend,
                    resumePositionMs = resumePos
                )
            }.onFailure { err ->
                _playerState.value = PlayerUiState.Error(
                    message = err.localizedMessage ?: "Falha ao resolver vídeo $videoId.",
                    videoId = videoId
                )
            }
        }
    }

    private fun chooseInitialBackend(
        resolved: ResolvedStream,
        mode: PlaybackEngineMode
    ): ActivePlayerBackend {
        return when (mode) {
            PlaybackEngineMode.NATIVE_H264_ONLY ->
                if (resolved.directH264Url != null) ActivePlayerBackend.NATIVE_H264
                else ActivePlayerBackend.IFRAME_EMBED
            PlaybackEngineMode.LITE_EMBED_ONLY ->
                ActivePlayerBackend.IFRAME_EMBED
            PlaybackEngineMode.HYBRID_AUTO ->
                if (resolved.directH264Url != null) ActivePlayerBackend.NATIVE_H264
                else ActivePlayerBackend.IFRAME_EMBED
        }
    }

    fun switchPlayerBackend(backend: ActivePlayerBackend) {
        val current = _playerState.value
        if (current is PlayerUiState.Ready) {
            if (backend == ActivePlayerBackend.NATIVE_H264 && current.stream.directH264Url == null) {
                _statusMessage.value = "Stream MP4 H.264 direto indisponível; usando Embed Direto."
                return
            }
            _playerState.value = current.copy(activeBackend = backend)
        }
    }

    fun savePlaybackPosition(videoId: String, positionMs: Long) {
        if (positionMs <= 0L) return
        viewModelScope.launch {
            repository.updateWatchPosition(videoId, positionMs)
        }
    }

    fun toggleBookmark(video: VideoItem) {
        viewModelScope.launch {
            repository.toggleBookmark(video)
        }
    }

    fun toggleChannelSubscription(channelId: String, channelName: String) {
        if (channelId.isBlank()) return
        viewModelScope.launch {
            if (channelId in followedChannelIds.value) {
                repository.removeChannel(channelId)
                _statusMessage.value = "Inscrição removida: $channelName"
            } else {
                repository.subscribeToChannel(channelId, channelName)
                _statusMessage.value = "Inscrito em $channelName"
            }
        }
    }

    fun addChannel(rawInput: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = repository.addChannelFromInput(rawInput)
            result.onSuccess { channel ->
                onResult(true, "Canal \"${channel.name}\" salvo localmente.")
                refreshFeed()
            }.onFailure { err ->
                onResult(false, err.message ?: "Erro ao adicionar canal.")
            }
        }
    }

    fun removeChannel(channelId: String) {
        viewModelScope.launch {
            repository.removeChannel(channelId)
            if (_selectedChannelId.value == channelId) {
                _selectedChannelId.value = null
            }
            refreshFeed()
        }
    }

    fun clearWatchHistory() {
        viewModelScope.launch {
            repository.clearWatchHistory()
            _statusMessage.value = "Histórico local limpo."
        }
    }

    fun refreshTelemetry() {
        _telemetry.value = repository.collectTelemetry()
    }

    fun runBridgeDiagnostics() {
        viewModelScope.launch {
            _isPinging.value = true
            _pingResults.value = repository.pingBridges()
            _isPinging.value = false
        }
    }

    fun updateQuality(preset: QualityPreset) {
        viewModelScope.launch { repository.updateQuality(preset) }
    }

    fun updateEngine(mode: PlaybackEngineMode) {
        viewModelScope.launch { repository.updateEngine(mode) }
    }

    fun updateLowRamThumbnails(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateLowRamThumbnails(enabled)
            refreshFeed()
        }
    }

    fun updateClassicLightTheme(enabled: Boolean) {
        viewModelScope.launch { repository.updateClassicLightTheme(enabled) }
    }

    fun updateCustomBridgeUrl(url: String) {
        viewModelScope.launch {
            repository.updateCustomBridgeUrl(url)
            _statusMessage.value = "Ponte Piped/Invidious atualizada."
        }
    }

    fun consumeStatusMessage() {
        _statusMessage.value = null
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
