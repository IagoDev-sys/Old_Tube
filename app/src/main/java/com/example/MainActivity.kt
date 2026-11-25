package com.example

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.Coil
import coil.ImageLoader
import coil.memory.MemoryCache
import com.example.data.local.OptimizationPreferences
import com.example.data.local.TabTubeDatabase
import com.example.data.remote.YouTubeLegacyNetwork
import com.example.data.repository.TabTubeRepository
import com.example.data.telemetry.DeviceTelemetryProvider
import com.example.ui.FeedUiState
import com.example.ui.MainViewModel
import com.example.ui.components.AddChannelDialog
import com.example.ui.components.GuideNavItem
import com.example.ui.components.OldTubeGuideSidebar
import com.example.ui.components.OldTubeTopBar
import com.example.ui.components.OpenVideoUrlDialog
import com.example.ui.screens.DiagnosticsScreen
import com.example.ui.screens.FeedScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.theme.OldTubeTheme

private const val ROUTE_FEED = "feed"
private const val ROUTE_SEARCH = "search"
private const val ROUTE_LIBRARY = "library"
private const val ROUTE_DIAGNOSTICS = "diagnostics"
private const val ROUTE_PLAYER = "player"

private data class NavDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val guideIcon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Cap Coil memory cache to 15% of heap for low-RAM devices (Galaxy Tab E 1.5GB)
        val imageLoader = ImageLoader.Builder(applicationContext)
            .memoryCache {
                MemoryCache.Builder(applicationContext)
                    .maxSizePercent(0.15)
                    .build()
            }
            .crossfade(false)
            .build()
        Coil.setImageLoader(imageLoader)

        setContent {
            val viewModel: MainViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY]) {
                            "APPLICATION_KEY missing from CreationExtras"
                        }
                        val db = TabTubeDatabase.getInstance(app)
                        val prefs = OptimizationPreferences(app)
                        val network = YouTubeLegacyNetwork()
                        val telemetry = DeviceTelemetryProvider(app)
                        val repo = TabTubeRepository(db.dao(), prefs, network, telemetry)
                        MainViewModel(repo)
                    }
                }
            )
            OldTubeMainShell(viewModel = viewModel)
        }
    }
}

@Composable
private fun OldTubeMainShell(viewModel: MainViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val selectedChannelId by viewModel.selectedChannelId.collectAsStateWithLifecycle()
    val feedState by viewModel.feedState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val bookmarkedIds by viewModel.bookmarkedIds.collectAsStateWithLifecycle()
    val followedChannelIds by viewModel.followedChannelIds.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val pingResults by viewModel.pingResults.collectAsStateWithLifecycle()
    val isPinging by viewModel.isPinging.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route ?: ROUTE_FEED

    val snackbarHostState = remember { SnackbarHostState() }
    var showOpenUrlDialog by remember { mutableStateOf(false) }
    var showAddChannelDialog by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }

    val context = LocalContext.current

    DisposableEffect(isFullscreen, currentRoute) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        if (controller != null) {
            if (isFullscreen && currentRoute == ROUTE_PLAYER) {
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    LaunchedEffect(statusMessage) {
        val msg = statusMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeStatusMessage()
        }
    }

    val destinations = remember {
        listOf(
            NavDestination(
                route = ROUTE_FEED,
                labelRes = R.string.nav_feed,
                selectedIcon = Icons.Filled.Home,
                unselectedIcon = Icons.Outlined.Home,
                guideIcon = Icons.Rounded.Home,
                testTag = "nav_feed"
            ),
            NavDestination(
                route = ROUTE_SEARCH,
                labelRes = R.string.nav_search,
                selectedIcon = Icons.Filled.Search,
                unselectedIcon = Icons.Outlined.Search,
                guideIcon = Icons.Rounded.Search,
                testTag = "nav_search"
            ),
            NavDestination(
                route = ROUTE_LIBRARY,
                labelRes = R.string.nav_library,
                selectedIcon = Icons.Filled.VideoLibrary,
                unselectedIcon = Icons.Outlined.VideoLibrary,
                guideIcon = Icons.Rounded.VideoLibrary,
                testTag = "nav_library"
            ),
            NavDestination(
                route = ROUTE_DIAGNOSTICS,
                labelRes = R.string.nav_diagnostics,
                selectedIcon = Icons.Filled.Tune,
                unselectedIcon = Icons.Outlined.Tune,
                guideIcon = Icons.Rounded.Tune,
                testTag = "nav_diagnostics"
            )
        )
    }

    val navigateTab: (String) -> Unit = { route ->
        isFullscreen = false
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val openPlayerRoute: () -> Unit = {
        isFullscreen = false
        if (currentRoute != ROUTE_PLAYER) {
            navController.navigate(ROUTE_PLAYER) {
                launchSingleTop = true
            }
        }
    }

    OldTubeTheme(classicLightTheme = settings.classicLightTheme) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useGuideSidebar = maxWidth >= 600.dp
            val showChrome = !(currentRoute == ROUTE_PLAYER && isFullscreen)

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = if (showChrome) WindowInsets.safeDrawing else WindowInsets(0),
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                topBar = {
                    if (showChrome && currentRoute != ROUTE_PLAYER) {
                        OldTubeTopBar(
                            isDarkSurface = !settings.classicLightTheme,
                            onSearchClick = { navigateTab(ROUTE_SEARCH) },
                            onPasteLinkClick = { showOpenUrlDialog = true },
                            onSettingsClick = { navigateTab(ROUTE_DIAGNOSTICS) }
                        )
                    }
                },
                bottomBar = {
                    if (showChrome && !useGuideSidebar && currentRoute != ROUTE_PLAYER) {
                        NavigationBar(
                            windowInsets = WindowInsets.navigationBars,
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            destinations.forEach { dest ->
                                val selected = currentRoute == dest.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTab(dest.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = stringResource(dest.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(dest.labelRes)) },
                                    modifier = Modifier.testTag(dest.testTag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (showChrome && useGuideSidebar && currentRoute != ROUTE_PLAYER) {
                        val guideItems = destinations.map { d ->
                            GuideNavItem(
                                route = d.route,
                                label = stringResource(d.labelRes),
                                icon = d.guideIcon,
                                testTag = d.testTag
                            )
                        }
                        OldTubeGuideSidebar(
                            navItems = guideItems,
                            currentRoute = currentRoute,
                            channels = channels,
                            selectedChannelId = selectedChannelId,
                            onNavigate = { route ->
                                if (route == ROUTE_FEED) {
                                    viewModel.selectChannelFilter(null)
                                }
                                navigateTab(route)
                            },
                            onSelectChannel = { channelId ->
                                viewModel.selectChannelFilter(channelId)
                                navigateTab(ROUTE_FEED)
                            }
                        )
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                        NavHost(
                            navController = navController,
                            startDestination = ROUTE_FEED
                        ) {
                            composable(ROUTE_FEED) {
                                FeedScreen(
                                    feedState = feedState,
                                    channels = channels,
                                    selectedChannelId = selectedChannelId,
                                    bookmarkedIds = bookmarkedIds,
                                    settings = settings,
                                    telemetry = telemetry,
                                    onSelectChannel = viewModel::selectChannelFilter,
                                    onRemoveChannel = viewModel::removeChannel,
                                    onRefresh = viewModel::refreshFeed,
                                    onOpenAddChannelDialog = { showAddChannelDialog = true },
                                    onVideoClick = { video ->
                                        viewModel.openVideo(video)
                                        openPlayerRoute()
                                    },
                                    onBookmarkToggle = viewModel::toggleBookmark,
                                    onOpenDiagnostics = { navigateTab(ROUTE_DIAGNOSTICS) }
                                )
                            }

                            composable(ROUTE_SEARCH) {
                                SearchScreen(
                                    query = searchQuery,
                                    searchState = searchState,
                                    bookmarkedIds = bookmarkedIds,
                                    settings = settings,
                                    onQueryChange = viewModel::updateSearchQuery,
                                    onSearchSubmit = { explicit ->
                                        val potentialId = viewModel.extractVideoId(explicit ?: searchQuery)
                                        val raw = (explicit ?: searchQuery).trim()
                                        if (potentialId != null && (raw.contains("http") || raw.contains("youtu") || raw.length == 11)) {
                                            viewModel.openVideoById(potentialId, raw.contains("/shorts/", ignoreCase = true))
                                            openPlayerRoute()
                                        } else {
                                            viewModel.executeSearch(explicit)
                                        }
                                    },
                                    onVideoClick = { video ->
                                        viewModel.openVideo(video)
                                        openPlayerRoute()
                                    },
                                    onBookmarkToggle = viewModel::toggleBookmark
                                )
                            }

                            composable(ROUTE_LIBRARY) {
                                LibraryScreen(
                                    bookmarks = bookmarks,
                                    history = history,
                                    bookmarkedIds = bookmarkedIds,
                                    settings = settings,
                                    onVideoClick = { video ->
                                        viewModel.openVideo(video)
                                        openPlayerRoute()
                                    },
                                    onBookmarkToggle = viewModel::toggleBookmark,
                                    onClearHistory = viewModel::clearWatchHistory
                                )
                            }

                            composable(ROUTE_DIAGNOSTICS) {
                                DiagnosticsScreen(
                                    telemetry = telemetry,
                                    settings = settings,
                                    pingResults = pingResults,
                                    isPinging = isPinging,
                                    onRefreshTelemetry = viewModel::refreshTelemetry,
                                    onQualitySelected = viewModel::updateQuality,
                                    onEngineSelected = viewModel::updateEngine,
                                    onLowRamThumbnailsChanged = viewModel::updateLowRamThumbnails,
                                    onClassicLightThemeChanged = viewModel::updateClassicLightTheme,
                                    onSaveBridgeUrl = viewModel::updateCustomBridgeUrl,
                                    onRunBridgePing = viewModel::runBridgeDiagnostics
                                )
                            }

                            composable(ROUTE_PLAYER) {
                                val related = (feedState as? FeedUiState.Success)?.videos.orEmpty()
                                PlayerScreen(
                                    playerState = playerState,
                                    relatedVideos = related,
                                    bookmarkedIds = bookmarkedIds,
                                    followedChannelIds = followedChannelIds,
                                    isFullscreen = isFullscreen,
                                    onSetFullscreen = { isFullscreen = it },
                                    onBack = {
                                        isFullscreen = false
                                        navController.popBackStack()
                                    },
                                    onSwitchBackend = viewModel::switchPlayerBackend,
                                    onPositionChanged = viewModel::savePlaybackPosition,
                                    onBookmarkToggle = viewModel::toggleBookmark,
                                    onToggleSubscribe = viewModel::toggleChannelSubscription,
                                    onSelectRelated = { video -> viewModel.openVideo(video) },
                                    onRetryVideo = { id -> viewModel.openVideoById(id) }
                                )
                            }
                        }
                    }
                }
            }

            if (showOpenUrlDialog) {
                OpenVideoUrlDialog(
                    onDismiss = { showOpenUrlDialog = false },
                    onOpenVideo = { raw ->
                        val ok = viewModel.openVideoByInput(raw)
                        if (ok) openPlayerRoute()
                        ok
                    }
                )
            }

            if (showAddChannelDialog) {
                AddChannelDialog(
                    onDismiss = { showAddChannelDialog = false },
                    onAddChannel = viewModel::addChannel
                )
            }
        }
    }
}
