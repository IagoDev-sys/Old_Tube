package com.example.classic

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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class FeedUiState {
    object Loading : FeedUiState()
    data class Success(val videos: List<VideoItem>) : FeedUiState()
    data class Error(val message: String) : FeedUiState()
}

sealed class SearchUiState {
    object Idle : SearchUiState()
    object Loading : SearchUiState()
    data class Success(val videos: List<VideoItem>) : SearchUiState()
    data class Error(val message: String) : SearchUiState()
}

sealed class PlayerUiState {
    object Idle : PlayerUiState()
    data class Loading(val videoId: String, val title: String) : PlayerUiState()
    data class Ready(
        val stream: ResolvedStream,
        val activeBackend: ActivePlayerBackend,
        val resumePositionMs: Long
    ) : PlayerUiState()
    data class Error(val videoId: String, val message: String) : PlayerUiState()
}

class OldTubeController(
    private val repository: TabTubeRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settings: StateFlow<OptimizationSettings> = repository.settingsFlow
        .stateIn(scope, SharingStarted.Eagerly, OptimizationSettings())

    val channels: StateFlow<List<ChannelEntity>> = repository.channelsFlow
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val bookmarks: StateFlow<List<SavedVideoEntity>> = repository.bookmarksFlow
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val history: StateFlow<List<SavedVideoEntity>> = repository.historyFlow
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val bookmarkedIds: StateFlow<Set<String>> = bookmarks
        .map { list -> list.map { it.videoId }.toSet() }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    val followedChannelIds: StateFlow<Set<String>> = channels
        .map { list -> list.map { it.channelId }.toSet() }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

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

    private var feedJob: Job? = null
    private var searchJob: Job? = null
    private var playerJob: Job? = null

    init {
        scope.launch {
            withContext(Dispatchers.IO) { repository.ensureSeedChannels() }
            refreshFeed()
        }
    }

    fun consumeStatusMessage() {
        _statusMessage.value = null
    }

    fun refreshTelemetry() {
        _telemetry.value = repository.collectTelemetry()
    }

    fun selectChannelFilter(channelId: String?) {
        _selectedChannelId.value = channelId
        refreshFeed()
    }

    fun refreshFeed() {
        feedJob?.cancel()
        feedJob = scope.launch {
            _feedState.value = FeedUiState.Loading
            val filter = _selectedChannelId.value
            val result = runCatching { repository.fetchFeedForChannels(filter) }
            _feedState.value = result.fold(
                onSuccess = { videos ->
                    if (videos.isEmpty()) {
                        FeedUiState.Error("Nenhum vídeo carregado. Verifique a conexão Wi-Fi ou adicione um canal.")
                    } else {
                        FeedUiState.Success(videos)
                    }
                },
                onFailure = { err ->
                    FeedUiState.Error(err.message ?: "Erro ao consultar feeds RSS do YouTube.")
                }
            )
        }
    }

    fun addChannel(rawInput: String, onDone: (Boolean, String) -> Unit) {
        scope.launch {
            val res = withContext(Dispatchers.IO) { repository.addChannelFromInput(rawInput) }
            res.fold(
                onSuccess = { channel ->
                    _statusMessage.value = "Canal \"${channel.name}\" adicionado ao seu feed."
                    onDone(true, channel.name)
                    refreshFeed()
                },
                onFailure = { err ->
                    onDone(false, err.message ?: "Canal não encontrado.")
                }
            )
        }
    }

    fun removeChannel(channelId: String) {
        scope.launch {
            withContext(Dispatchers.IO) { repository.removeChannel(channelId) }
            if (_selectedChannelId.value == channelId) {
                _selectedChannelId.value = null
            }
            refreshFeed()
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun executeSearch(explicitQuery: String? = null) {
        val q = (explicitQuery ?: _searchQuery.value).trim()
        _searchQuery.value = q
        if (q.isBlank()) {
            _searchState.value = SearchUiState.Idle
            return
        }
        searchJob?.cancel()
        searchJob = scope.launch {
            _searchState.value = SearchUiState.Loading
            val result = runCatching { repository.search(q) }
            _searchState.value = result.fold(
                onSuccess = { videos ->
                    if (videos.isEmpty()) {
                        SearchUiState.Error("Sem resultados para \"$q\".")
                    } else {
                        SearchUiState.Success(videos)
                    }
                },
                onFailure = { err ->
                    SearchUiState.Error(err.message ?: "Falha na busca.")
                }
            )
        }
    }

    fun extractVideoId(input: String): String? = repository.extractVideoId(input)

    fun openVideoByInput(rawInput: String): Boolean {
        val id = repository.extractVideoId(rawInput)
        if (id == null) {
            _statusMessage.value = "Link inválido. Cole um link youtube.com, shorts, youtu.be ou ID de 11 caracteres."
            return false
        }
        val isShort = rawInput.contains("/shorts/", ignoreCase = true)
        openVideo(
            VideoItem(
                id = id,
                title = if (isShort) "Short $id" else "Vídeo $id",
                channelName = "YouTube",
                thumbnailUrl = "https://i.ytimg.com/vi/$id/mqdefault.jpg",
                sourceTag = if (isShort) "Short" else "Link Direto",
                isShort = isShort
            )
        )
        return true
    }

    fun openVideoById(videoId: String, isShort: Boolean = false) {
        openVideo(
            VideoItem(
                id = videoId,
                title = if (isShort) "Short $videoId" else "Vídeo $videoId",
                channelName = "YouTube",
                thumbnailUrl = "https://i.ytimg.com/vi/$videoId/mqdefault.jpg",
                sourceTag = if (isShort) "Short" else "YouTube",
                isShort = isShort
            )
        )
    }

    fun openVideo(video: VideoItem) {
        playerJob?.cancel()
        playerJob = scope.launch {
            _playerState.value = PlayerUiState.Loading(video.id, video.title)
            val resumeMs = withContext(Dispatchers.IO) { repository.recordWatchStart(video) }
            val result = runCatching {
                repository.resolveStream(video.id, video, video.isShort)
            }
            _playerState.value = result.fold(
                onSuccess = { stream ->
                    val mode = settings.value.engineMode
                    val backend = when {
                        mode == PlaybackEngineMode.LITE_EMBED_ONLY -> ActivePlayerBackend.IFRAME_EMBED
                        mode == PlaybackEngineMode.NATIVE_H264_ONLY && stream.directH264Url != null ->
                            ActivePlayerBackend.NATIVE_H264
                        stream.directH264Url != null -> ActivePlayerBackend.NATIVE_H264
                        else -> ActivePlayerBackend.IFRAME_EMBED
                    }
                    PlayerUiState.Ready(
                        stream = stream,
                        activeBackend = backend,
                        resumePositionMs = resumeMs
                    )
                },
                onFailure = { err ->
                    PlayerUiState.Error(video.id, err.message ?: "Erro ao preparar reprodução.")
                }
            )
        }
    }

    fun switchPlayerBackend(target: ActivePlayerBackend) {
        val current = _playerState.value as? PlayerUiState.Ready ?: return
        if (target == ActivePlayerBackend.NATIVE_H264 && current.stream.directH264Url == null) {
            _statusMessage.value = "Fluxo H.264 direto indisponível; usando Embed Direto."
            return
        }
        _playerState.value = current.copy(activeBackend = target)
    }

    fun savePlaybackPosition(videoId: String, positionMs: Long) {
        scope.launch(Dispatchers.IO) {
            repository.updateWatchPosition(videoId, positionMs)
        }
    }

    fun toggleBookmark(video: VideoItem) {
        scope.launch(Dispatchers.IO) {
            repository.toggleBookmark(video)
        }
    }

    fun toggleChannelSubscription(channelId: String, channelName: String) {
        if (channelId.isBlank()) return
        scope.launch {
            val alreadyFollowed = channelId in followedChannelIds.value
            withContext(Dispatchers.IO) {
                if (alreadyFollowed) {
                    repository.removeChannel(channelId)
                } else {
                    repository.subscribeToChannel(channelId, channelName)
                }
            }
            _statusMessage.value = if (alreadyFollowed) {
                "Inscrição removida."
            } else {
                "Inscrito em $channelName."
            }
        }
    }

    fun clearWatchHistory() {
        scope.launch {
            withContext(Dispatchers.IO) { repository.clearWatchHistory() }
            _statusMessage.value = "Histórico limpo."
        }
    }

    fun updateQuality(preset: QualityPreset) {
        repository.updateQuality(preset)
    }

    fun updateEngine(mode: PlaybackEngineMode) {
        repository.updateEngine(mode)
    }

    fun updateLowRamThumbnails(enabled: Boolean) {
        repository.updateLowRamThumbnails(enabled)
    }

    fun updateClassicLightTheme(enabled: Boolean) {
        repository.updateClassicLightTheme(enabled)
    }

    fun updateAllowLanBridge(enabled: Boolean) {
        repository.updateAllowLanBridge(enabled)
    }

    fun updateCustomBridgeUrl(url: String) {
        val saved = repository.updateCustomBridgeUrl(url)
        _statusMessage.value = if (saved) {
            "URL da ponte salva."
        } else {
            "Use https:// público válido (ou habilite Ponte LAN para 192.168.x.x)."
        }
    }

    fun runBridgeDiagnostics() {
        scope.launch {
            _isPinging.value = true
            _pingResults.value = repository.pingBridges()
            _isPinging.value = false
        }
    }

    fun release() {
        repository.releaseProxy()
        scope.cancel()
    }
}
