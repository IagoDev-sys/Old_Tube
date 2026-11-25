package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ChannelEntity
import com.example.data.local.SavedVideoEntity
import com.example.data.local.TabTubeDatabase
import com.example.data.model.ActivePlayerBackend
import com.example.data.model.DeviceTelemetry
import com.example.data.model.OptimizationSettings
import com.example.data.model.ResolvedStream
import com.example.data.model.VideoItem
import com.example.data.remote.YouTubeLegacyNetwork
import com.example.data.telemetry.DeviceTelemetryProvider
import com.example.ui.FeedUiState
import com.example.ui.PlayerUiState
import com.example.ui.screens.FeedScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.theme.OldTubeTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CompatibilityAndSafetyRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun manifest_enforcesLeastPrivilegePermissionsAndHonestMinSdk24() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pkgInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val perms = pkgInfo.requestedPermissions?.toSet() ?: emptySet()

        // Only install-time network permissions should be requested (no camera/storage/location)
        assertTrue(Manifest.permission.INTERNET in perms)
        assertTrue(Manifest.permission.ACCESS_NETWORK_STATE in perms)
        assertFalse(Manifest.permission.READ_EXTERNAL_STORAGE in perms)
        assertFalse(Manifest.permission.WRITE_EXTERNAL_STORAGE in perms)
        assertFalse(Manifest.permission.ACCESS_FINE_LOCATION in perms)
        assertFalse(Manifest.permission.CAMERA in perms)
        assertFalse(Manifest.permission.RECORD_AUDIO in perms)

        // Confirm Compose build enforces minSdk 24 (Android 7.0+), not Android 4.4 KitKat (API 19)
        assertEquals(24, context.applicationInfo.minSdkVersion)
    }

    @Test
    fun telemetryProvider_collectsDeviceMetricsSafely() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val telemetry = DeviceTelemetryProvider(context).collect()
        assertTrue(telemetry.apiLevel >= 24)
        assertTrue(telemetry.screenWidthDp > 0)
        assertTrue(telemetry.screenHeightDp > 0)
        assertTrue(telemetry.totalRamMb >= telemetry.availableRamMb)
    }

    @Test
    fun atomFeedParser_detectsStandardVideosAndShortsSafely() {
        val sampleXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns:yt="http://www.youtube.com/xml/schemas/2015" xmlns:media="http://search.yahoo.com/mrss/">
              <entry>
                <yt:videoId>dQw4w9WgXcQ</yt:videoId>
                <yt:channelId>UCuAXFkgsw1L7xaCfnd5JJOw</yt:channelId>
                <title>Classic Video</title>
                <link rel="alternate" href="https://www.youtube.com/watch?v=dQw4w9WgXcQ"/>
                <author><name>Demo Channel</name></author>
                <published>2025-02-10T12:00:00+00:00</published>
              </entry>
              <entry>
                <yt:videoId>abcDEF12345</yt:videoId>
                <yt:channelId>UCuAXFkgsw1L7xaCfnd5JJOw</yt:channelId>
                <title>Vertical Short</title>
                <link rel="alternate" href="https://www.youtube.com/shorts/abcDEF12345"/>
                <author><name>Demo Channel</name></author>
                <published>2025-02-11T12:00:00+00:00</published>
              </entry>
            </feed>
        """.trimIndent()

        val parsed = YouTubeLegacyNetwork().parseAtomFeed(sampleXml, lowRamThumbnails = true)
        assertEquals(2, parsed.size)
        assertFalse(parsed[0].isShort)
        assertEquals("Atom RSS", parsed[0].sourceTag)
        assertTrue(parsed[0].thumbnailUrl.endsWith("/mqdefault.jpg"))

        assertTrue(parsed[1].isShort)
        assertEquals("Short RSS", parsed[1].sourceTag)
    }

    @Test
    fun roomLocalPersistence_savesChannelsBookmarksAndClearsHistorySafely() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, TabTubeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.dao()
            dao.insertChannels(TabTubeDatabase.DEFAULT_CHANNELS)
            assertEquals(TabTubeDatabase.DEFAULT_CHANNELS.size, dao.getChannelsOnce().size)

            dao.upsertSavedVideo(
                SavedVideoEntity(
                    videoId = "vidBookmark1",
                    title = "Saved Tutorial",
                    channelName = "Channel A",
                    channelId = "UC123",
                    thumbnailUrl = "https://i.ytimg.com/vi/vidBookmark1/mqdefault.jpg",
                    durationText = "5:00",
                    publishedAt = "10/02/2025",
                    isBookmarked = true,
                    lastPlayedAt = 1000L
                )
            )
            dao.upsertSavedVideo(
                SavedVideoEntity(
                    videoId = "vidHistory01",
                    title = "Watched Only",
                    channelName = "Channel B",
                    channelId = "UC456",
                    thumbnailUrl = "https://i.ytimg.com/vi/vidHistory01/mqdefault.jpg",
                    durationText = "3:20",
                    publishedAt = "11/02/2025",
                    isBookmarked = false,
                    lastPlayedAt = 2000L
                )
            )

            dao.clearWatchHistory()
            val bookmarks = dao.observeBookmarks().first()
            val history = dao.observeHistory().first()
            assertEquals(1, bookmarks.size)
            assertEquals("vidBookmark1", bookmarks.first().videoId)
            assertEquals(1, history.size)
        } finally {
            db.close()
        }
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp")
    fun feedScreen_filtersShortsAndVideosCorrectly() {
        val videos = listOf(
            VideoItem(
                id = "vidRegular01",
                title = "Long Documentary",
                channelName = "Science",
                thumbnailUrl = "https://i.ytimg.com/vi/vidRegular01/mqdefault.jpg",
                isShort = false
            ),
            VideoItem(
                id = "vidShort0001",
                title = "Quick Experiment",
                channelName = "Science",
                thumbnailUrl = "https://i.ytimg.com/vi/vidShort0001/mqdefault.jpg",
                sourceTag = "Short RSS",
                isShort = true
            )
        )
        composeRule.setContent {
            OldTubeTheme(classicLightTheme = true) {
                FeedScreen(
                    feedState = FeedUiState.Success(videos),
                    channels = listOf(ChannelEntity("UC1", "Science", "Ciência")),
                    selectedChannelId = null,
                    bookmarkedIds = emptySet(),
                    settings = OptimizationSettings(),
                    telemetry = sampleTelemetry(),
                    onSelectChannel = {},
                    onRemoveChannel = {},
                    onRefresh = {},
                    onOpenAddChannelDialog = {},
                    onVideoClick = {},
                    onBookmarkToggle = {},
                    onOpenDiagnostics = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("video_card_vidRegular01").assertIsDisplayed()
        composeRule.onNodeWithTag("video_card_vidShort0001").assertIsDisplayed()

        composeRule.onNodeWithTag("filter_shorts_only").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("video_card_vidShort0001").assertIsDisplayed()
        composeRule.onNodeWithTag("video_card_vidRegular01").assertDoesNotExist()
    }

    @Test
    fun playerScreen_showsShortControlsAndDisablesUnavailableH264Safely() {
        val shortStream = ResolvedStream(
            videoId = "abcDEF12345",
            title = "Retro Hardware Short",
            channelName = "RetroLab",
            channelId = "UCretro123",
            thumbnailUrl = "https://i.ytimg.com/vi/abcDEF12345/mqdefault.jpg",
            description = "Teste de Short vertical",
            directH264Url = null,
            qualityLabel = "480p SD",
            codecLabel = "Embed Direto",
            embedUrl = "https://www.youtube-nocookie.com/embed/abcDEF12345",
            mobileWatchUrl = "https://m.youtube.com/shorts/abcDEF12345",
            resolvedVia = "Shorts Direto",
            isShort = true
        )

        composeRule.setContent {
            OldTubeTheme(classicLightTheme = true) {
                PlayerScreen(
                    playerState = PlayerUiState.Ready(
                        stream = shortStream,
                        activeBackend = ActivePlayerBackend.IFRAME_EMBED,
                        resumePositionMs = 0L
                    ),
                    relatedVideos = listOf(
                        VideoItem(
                            id = "nextShort11",
                            title = "Próximo Short",
                            channelName = "RetroLab",
                            thumbnailUrl = "https://i.ytimg.com/vi/nextShort11/mqdefault.jpg",
                            isShort = true
                        )
                    ),
                    bookmarkedIds = emptySet(),
                    followedChannelIds = emptySet(),
                    isFullscreen = false,
                    onSetFullscreen = {},
                    onBack = {},
                    onSwitchBackend = {},
                    onPositionChanged = { _, _ -> },
                    onBookmarkToggle = {},
                    onToggleSubscribe = { _, _ -> },
                    onSelectRelated = {},
                    onRetryVideo = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("player_lazy_column")
            .performScrollToNode(hasTestTag("chip_native_engine"))
        composeRule.onNodeWithTag("chip_native_engine").assertIsNotEnabled()
        composeRule.onNodeWithTag("chip_iframe_engine").assertIsDisplayed()

        composeRule.onNodeWithTag("toggle_short_aspect_button")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("next_short_button")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun libraryScreen_displaysBookmarksAndEmptyStatesWithoutLogin() {
        composeRule.setContent {
            OldTubeTheme(classicLightTheme = true) {
                LibraryScreen(
                    bookmarks = emptyList(),
                    history = emptyList(),
                    bookmarkedIds = emptySet(),
                    settings = OptimizationSettings(),
                    onVideoClick = {},
                    onBookmarkToggle = {},
                    onClearHistory = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Biblioteca Local").assertIsDisplayed()
        composeRule.onNodeWithTag("tab_bookmarks").assertIsDisplayed()
        composeRule.onNodeWithTag("tab_history").assertIsDisplayed()
    }

    private fun sampleTelemetry() = DeviceTelemetry(
        deviceModel = "SM-T560",
        manufacturer = "Samsung",
        androidVersion = "7.1.2",
        apiLevel = 25,
        supportedAbis = "armeabi-v7a",
        totalRamMb = 1500L,
        availableRamMb = 620L,
        isLowRamDevice = true,
        hasHardwareAvc = true,
        hasHardwareVp9 = false,
        hasHardwareAv1 = false,
        avcDecoderNames = listOf("OMX.sprd.avc.decoder"),
        screenWidthDp = 800,
        screenHeightDp = 1280
    )
}
