package com.example

import android.app.Activity
import android.app.AlertDialog
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.example.classic.ClassicIconType
import com.example.classic.ClassicVectorIcon
import com.example.classic.DiagnosticsPanel
import com.example.classic.FeedPanel
import com.example.classic.LibraryPanel
import com.example.classic.OldTubeController
import com.example.classic.OldTubePalette
import com.example.classic.PlayerPanel
import com.example.classic.SearchPanel
import com.example.classic.dp
import com.example.classic.makeRoundedCardDrawable
import com.example.data.local.OptimizationPreferences
import com.example.data.local.TabTubeSqliteStore
import com.example.data.model.VideoItem
import com.example.data.remote.YouTubeLegacyNetwork
import com.example.data.repository.TabTubeRepository
import com.example.data.telemetry.DeviceTelemetryProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

private enum class AppScreen {
    FEED, SEARCH, LIBRARY, DIAGNOSTICS, PLAYER
}

class MainActivity : Activity() {

    private lateinit var controller: OldTubeController
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var currentScreen = AppScreen.FEED
    private var previousTab = AppScreen.FEED
    private var isFullscreen = false

    private lateinit var rootLayout: LinearLayout
    private lateinit var topBar: LinearLayout
    private lateinit var contentRow: LinearLayout
    private lateinit var guideSidebar: ScrollView
    private lateinit var guideColumn: LinearLayout
    private lateinit var screenContainer: FrameLayout
    private lateinit var bottomNav: LinearLayout

    private lateinit var feedPanel: FeedPanel
    private lateinit var searchPanel: SearchPanel
    private lateinit var libraryPanel: LibraryPanel
    private lateinit var diagnosticsPanel: DiagnosticsPanel
    private lateinit var playerPanel: PlayerPanel

    private lateinit var topLinkBtn: TextView
    private lateinit var topSearchBtn: ImageView
    private lateinit var topSettingsBtn: ImageView
    private lateinit var topTitle: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val store = TabTubeSqliteStore.getInstance(applicationContext)
        val prefs = OptimizationPreferences(applicationContext)
        val network = YouTubeLegacyNetwork()
        val telemetry = DeviceTelemetryProvider(applicationContext)
        val repository = TabTubeRepository(store, prefs, network, telemetry)
        controller = OldTubeController(repository)

        buildShellUi()
        setContentView(rootLayout)
        bindControllerFlows()
    }

    private fun buildShellUi() {
        val isWideTablet = resources.configuration.screenWidthDp >= 600

        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Classic 2013 YouTube Top Bar
        topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(6), dp(8), dp(6))
        }
        val playBadge = ImageView(this).apply {
            setImageDrawable(ClassicVectorIcon(ClassicIconType.PLAY_BADGE, Color.WHITE, dp(18)))
            background = makeRoundedCardDrawable(
                Color.parseColor("#CC181E"),
                Color.parseColor("#990000"),
                dp(4).toFloat(),
                0
            )
            scaleType = ImageView.ScaleType.CENTER
        }
        topBar.addView(playBadge, LinearLayout.LayoutParams(dp(34), dp(24)))

        topTitle = TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(8), 0, 0, 0)
        }
        topBar.addView(topTitle, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        topLinkBtn = TextView(this).apply {
            text = getString(R.string.action_paste_link)
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setOnClickListener { showOpenLinkDialog() }
        }
        topBar.addView(topLinkBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(40)))

        topSearchBtn = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER
            contentDescription = getString(R.string.nav_search)
            setOnClickListener { navigateTo(AppScreen.SEARCH) }
        }
        topBar.addView(topSearchBtn, LinearLayout.LayoutParams(dp(48), dp(48)))

        topSettingsBtn = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER
            contentDescription = getString(R.string.nav_diagnostics)
            setOnClickListener { navigateTo(AppScreen.DIAGNOSTICS) }
        }
        topBar.addView(topSettingsBtn, LinearLayout.LayoutParams(dp(48), dp(48)))

        rootLayout.addView(
            topBar,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52))
        )

        contentRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        guideColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(10), dp(8), dp(10))
        }
        guideSidebar = ScrollView(this).apply {
            addView(guideColumn)
            visibility = if (isWideTablet) View.VISIBLE else View.GONE
        }
        contentRow.addView(
            guideSidebar,
            LinearLayout.LayoutParams(dp(205), LinearLayout.LayoutParams.MATCH_PARENT)
        )

        screenContainer = FrameLayout(this)
        contentRow.addView(
            screenContainer,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        )

        rootLayout.addView(
            contentRow,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        bottomNav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = if (isWideTablet) View.GONE else View.VISIBLE
        }
        rootLayout.addView(
            bottomNav,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56))
        )

        feedPanel = FeedPanel(
            context = this,
            isWideTablet = isWideTablet,
            onSelectChannel = controller::selectChannelFilter,
            onRemoveChannel = controller::removeChannel,
            onRefresh = controller::refreshFeed,
            onOpenAddChannel = { showAddChannelDialog() },
            onVideoClick = { openVideoInPlayer(it) },
            onBookmarkToggle = controller::toggleBookmark,
            onOpenDiagnostics = { navigateTo(AppScreen.DIAGNOSTICS) }
        )
        searchPanel = SearchPanel(
            context = this,
            onSubmitSearch = { raw ->
                val id = controller.extractVideoId(raw)
                val trimmed = raw.trim()
                if (id != null && (trimmed.contains("http") || trimmed.contains("youtu") || trimmed.length == 11)) {
                    controller.openVideoById(id, trimmed.contains("/shorts/", ignoreCase = true))
                    navigateTo(AppScreen.PLAYER)
                } else {
                    controller.executeSearch(raw)
                }
            },
            onVideoClick = { openVideoInPlayer(it) },
            onBookmarkToggle = controller::toggleBookmark
        )
        libraryPanel = LibraryPanel(
            context = this,
            onVideoClick = { openVideoInPlayer(it) },
            onBookmarkToggle = controller::toggleBookmark,
            onClearHistory = controller::clearWatchHistory
        )
        diagnosticsPanel = DiagnosticsPanel(
            context = this,
            onRefreshTelemetry = controller::refreshTelemetry,
            onQualitySelected = controller::updateQuality,
            onEngineSelected = controller::updateEngine,
            onLowRamChanged = controller::updateLowRamThumbnails,
            onClassicThemeChanged = controller::updateClassicLightTheme,
            onAllowLanBridgeChanged = controller::updateAllowLanBridge,
            onSaveBridgeUrl = controller::updateCustomBridgeUrl,
            onRunBridgePing = controller::runBridgeDiagnostics
        )
        playerPanel = PlayerPanel(
            context = this,
            onBack = { handleBackNavigation() },
            onSetFullscreen = { fs ->
                isFullscreen = fs
                applyFullscreenWindowFlags(fs)
                renderCurrentState()
            },
            onSwitchBackend = controller::switchPlayerBackend,
            onPositionChanged = controller::savePlaybackPosition,
            onBookmarkToggle = controller::toggleBookmark,
            onToggleSubscribe = controller::toggleChannelSubscription,
            onSelectRelated = { video -> controller.openVideo(video) },
            onRetryVideo = { id -> controller.openVideoById(id) }
        )

        screenContainer.addView(feedPanel.root)
        screenContainer.addView(searchPanel.root)
        screenContainer.addView(libraryPanel.root)
        screenContainer.addView(diagnosticsPanel.root)
        screenContainer.addView(playerPanel.root)
        updateScreenVisibility()
    }

    private fun bindControllerFlows() {
        uiScope.launch {
            controller.statusMessage.collect { msg ->
                if (msg != null) {
                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                    controller.consumeStatusMessage()
                }
            }
        }

        uiScope.launch {
            combine(
                controller.settings,
                controller.channels,
                controller.selectedChannelId,
                controller.feedState,
                controller.bookmarkedIds
            ) { _, _, _, _, _ -> Unit }.collect {
                renderCurrentState()
            }
        }

        uiScope.launch {
            combine(
                controller.searchQuery,
                controller.searchState,
                controller.bookmarks,
                controller.history,
                controller.playerState
            ) { _, _, _, _, _ -> Unit }.collect {
                renderCurrentState()
            }
        }

        uiScope.launch {
            combine(
                controller.telemetry,
                controller.pingResults,
                controller.isPinging,
                controller.followedChannelIds
            ) { _, _, _, _ -> Unit }.collect {
                renderCurrentState()
            }
        }
    }

    private fun renderCurrentState() {
        val settings = controller.settings.value
        val channels = controller.channels.value
        val selectedChannelId = controller.selectedChannelId.value
        val bookmarkedIds = controller.bookmarkedIds.value
        val followedIds = controller.followedChannelIds.value
        val palette = OldTubePalette.from(settings.classicLightTheme)

        val showChrome = !(currentScreen == AppScreen.PLAYER && isFullscreen)
        val isWideTablet = resources.configuration.screenWidthDp >= 600
        topBar.visibility = if (showChrome && currentScreen != AppScreen.PLAYER) View.VISIBLE else View.GONE
        guideSidebar.visibility = if (showChrome && isWideTablet && currentScreen != AppScreen.PLAYER) View.VISIBLE else View.GONE
        bottomNav.visibility = if (showChrome && !isWideTablet && currentScreen != AppScreen.PLAYER) View.VISIBLE else View.GONE

        topBar.setBackgroundColor(palette.surface)
        topTitle.setTextColor(palette.textPrimary)
        topLinkBtn.setTextColor(palette.textPrimary)
        topLinkBtn.background = makeRoundedCardDrawable(palette.surfaceVariant, palette.border, dp(4).toFloat())
        topSearchBtn.setImageDrawable(ClassicVectorIcon(ClassicIconType.SEARCH, palette.textPrimary, dp(20)))
        topSettingsBtn.setImageDrawable(ClassicVectorIcon(ClassicIconType.TUNE, palette.textPrimary, dp(20)))

        rebuildNavigationChrome(palette, channels, selectedChannelId)

        feedPanel.render(
            feedState = controller.feedState.value,
            channels = channels,
            selectedChannelId = selectedChannelId,
            bookmarkedIds = bookmarkedIds,
            settings = settings,
            telemetry = controller.telemetry.value
        )
        searchPanel.render(
            query = controller.searchQuery.value,
            searchState = controller.searchState.value,
            bookmarkedIds = bookmarkedIds,
            settings = settings
        )
        libraryPanel.render(
            bookmarks = controller.bookmarks.value,
            history = controller.history.value,
            bookmarkedIds = bookmarkedIds,
            settings = settings
        )
        diagnosticsPanel.render(
            telemetry = controller.telemetry.value,
            settings = settings,
            pingResults = controller.pingResults.value,
            isPinging = controller.isPinging.value
        )
        val related = (controller.feedState.value as? com.example.classic.FeedUiState.Success)?.videos.orEmpty()
        playerPanel.render(
            playerState = controller.playerState.value,
            relatedVideos = related,
            bookmarkedIds = bookmarkedIds,
            followedChannelIds = followedIds,
            isFullscreen = isFullscreen,
            settings = settings
        )
    }

    private fun rebuildNavigationChrome(
        palette: OldTubePalette,
        channels: List<com.example.data.local.ChannelEntity>,
        selectedChannelId: String?
    ) {
        val navItems = listOf(
            Triple(AppScreen.FEED, getString(R.string.nav_feed), ClassicIconType.HOME),
            Triple(AppScreen.SEARCH, getString(R.string.nav_search), ClassicIconType.SEARCH),
            Triple(AppScreen.LIBRARY, getString(R.string.nav_library), ClassicIconType.LIBRARY),
            Triple(AppScreen.DIAGNOSTICS, getString(R.string.nav_diagnostics), ClassicIconType.TUNE)
        )

        bottomNav.setBackgroundColor(palette.surface)
        bottomNav.removeAllViews()
        for ((screen, label, icon) in navItems) {
            val active = currentScreen == screen
            val tab = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(6), dp(4), dp(6))
                setOnClickListener { navigateTo(screen) }
            }
            val iv = ImageView(this).apply {
                setImageDrawable(
                    ClassicVectorIcon(
                        icon,
                        if (active) palette.crimson else palette.textSecondary,
                        dp(20)
                    )
                )
            }
            val tv = TextView(this).apply {
                text = label
                textSize = 11f
                maxLines = 1
                if (active) setTypeface(typeface, Typeface.BOLD)
                setTextColor(if (active) palette.crimson else palette.textSecondary)
            }
            tab.addView(iv, LinearLayout.LayoutParams(dp(24), dp(24)))
            tab.addView(tv)
            bottomNav.addView(tab, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        }

        guideSidebar.setBackgroundColor(palette.surface)
        guideColumn.removeAllViews()
        for ((screen, label, icon) in navItems) {
            val active = currentScreen == screen
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = makeRoundedCardDrawable(
                    if (active) palette.crimson else palette.surface,
                    if (active) palette.crimsonDark else Color.TRANSPARENT,
                    dp(3).toFloat(),
                    0
                )
                setPadding(dp(10), dp(10), dp(10), dp(10))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(4) }
                setOnClickListener {
                    if (screen == AppScreen.FEED) controller.selectChannelFilter(null)
                    navigateTo(screen)
                }
            }
            row.addView(
                ImageView(this).apply {
                    setImageDrawable(
                        ClassicVectorIcon(icon, if (active) Color.WHITE else palette.textSecondary, dp(18))
                    )
                },
                LinearLayout.LayoutParams(dp(20), dp(20)).apply { rightMargin = dp(10) }
            )
            row.addView(
                TextView(this).apply {
                    text = label
                    textSize = 13f
                    if (active) setTypeface(typeface, Typeface.BOLD)
                    setTextColor(if (active) Color.WHITE else palette.textPrimary)
                }
            )
            guideColumn.addView(row)
        }

        val chTitle = TextView(this).apply {
            text = "MEUS CANAIS (RSS)"
            textSize = 11f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.crimson)
            setPadding(dp(8), dp(14), dp(8), dp(6))
        }
        guideColumn.addView(chTitle)

        for (channel in channels) {
            val active = selectedChannelId == channel.channelId && currentScreen == AppScreen.FEED
            val chRow = TextView(this).apply {
                text = "• ${channel.name}"
                textSize = 13f
                if (active) setTypeface(typeface, Typeface.BOLD)
                setTextColor(if (active) palette.crimson else palette.textPrimary)
                setPadding(dp(10), dp(8), dp(8), dp(8))
                setOnClickListener {
                    controller.selectChannelFilter(channel.channelId)
                    navigateTo(AppScreen.FEED)
                }
            }
            guideColumn.addView(chRow)
        }
    }

    private fun openVideoInPlayer(video: VideoItem) {
        controller.openVideo(video)
        navigateTo(AppScreen.PLAYER)
    }

    private fun navigateTo(target: AppScreen) {
        if (currentScreen == AppScreen.PLAYER && target != AppScreen.PLAYER) {
            playerPanel.release()
            isFullscreen = false
            applyFullscreenWindowFlags(false)
        }
        if (target != AppScreen.PLAYER) {
            previousTab = target
        }
        currentScreen = target
        updateScreenVisibility()
        renderCurrentState()
    }

    private fun updateScreenVisibility() {
        feedPanel.root.visibility = if (currentScreen == AppScreen.FEED) View.VISIBLE else View.GONE
        searchPanel.root.visibility = if (currentScreen == AppScreen.SEARCH) View.VISIBLE else View.GONE
        libraryPanel.root.visibility = if (currentScreen == AppScreen.LIBRARY) View.VISIBLE else View.GONE
        diagnosticsPanel.root.visibility = if (currentScreen == AppScreen.DIAGNOSTICS) View.VISIBLE else View.GONE
        playerPanel.root.visibility = if (currentScreen == AppScreen.PLAYER) View.VISIBLE else View.GONE
    }

    private fun handleBackNavigation(): Boolean {
        if (currentScreen == AppScreen.PLAYER) {
            if (isFullscreen) {
                isFullscreen = false
                applyFullscreenWindowFlags(false)
                renderCurrentState()
                return true
            }
            navigateTo(previousTab)
            return true
        }
        if (currentScreen != AppScreen.FEED) {
            navigateTo(AppScreen.FEED)
            return true
        }
        return false
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (!handleBackNavigation()) {
            super.onBackPressed()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        renderCurrentState()
    }

    override fun onDestroy() {
        playerPanel.release()
        controller.release()
        uiScope.cancel()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun applyFullscreenWindowFlags(fullscreen: Boolean) {
        if (fullscreen) {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    private fun showOpenLinkDialog() {
        val input = EditText(this).apply {
            hint = "https://youtube.com/watch?v=... ou Shorts"
            isSingleLine = true
        }
        AlertDialog.Builder(this)
            .setTitle("Abrir vídeo por link ou ID")
            .setView(input)
            .setPositiveButton("Assistir") { _, _ ->
                if (controller.openVideoByInput(input.text.toString())) {
                    navigateTo(AppScreen.PLAYER)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showAddChannelDialog() {
        val input = EditText(this).apply {
            hint = "@canal, link do YouTube ou ID UC..."
            isSingleLine = true
        }
        AlertDialog.Builder(this)
            .setTitle("Adicionar canal ao feed RSS")
            .setView(input)
            .setPositiveButton("Adicionar") { _, _ ->
                controller.addChannel(input.text.toString()) { ok, msg ->
                    if (!ok) {
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
