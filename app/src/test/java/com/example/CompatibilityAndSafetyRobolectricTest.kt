package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.view.View
import android.widget.Button
import androidx.test.core.app.ApplicationProvider
import com.example.classic.FeedPanel
import com.example.classic.FeedUiState
import com.example.classic.LibraryPanel
import com.example.classic.PlayerPanel
import com.example.classic.PlayerUiState
import com.example.data.local.ChannelEntity
import com.example.data.local.OptimizationPreferences
import com.example.data.local.SavedVideoEntity
import com.example.data.local.TabTubeSqliteStore
import com.example.data.model.ActivePlayerBackend
import com.example.data.model.DeviceTelemetry
import com.example.data.model.OptimizationSettings
import com.example.data.model.QualityPreset
import com.example.data.model.ResolvedStream
import com.example.data.model.VideoItem
import com.example.data.remote.LocalTlsStreamProxy
import com.example.data.remote.YouTubeLegacyNetwork
import com.example.data.telemetry.DeviceTelemetryProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.HttpURLConnection
import java.net.URL

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CompatibilityAndSafetyRobolectricTest {

    @Test
    fun manifest_enforcesLeastPrivilegePermissionsAndAndroid44KitKatMinSdk19() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pkgInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val perms = pkgInfo.requestedPermissions?.toSet() ?: emptySet()

        assertTrue(Manifest.permission.INTERNET in perms)
        assertTrue(Manifest.permission.ACCESS_NETWORK_STATE in perms)
        assertFalse(Manifest.permission.READ_EXTERNAL_STORAGE in perms)
        assertFalse(Manifest.permission.WRITE_EXTERNAL_STORAGE in perms)
        assertFalse(Manifest.permission.ACCESS_FINE_LOCATION in perms)
        assertFalse(Manifest.permission.CAMERA in perms)
        assertFalse(Manifest.permission.RECORD_AUDIO in perms)

        // Android 4.4 KitKat target compatibility verified!
        assertEquals(19, context.applicationInfo.minSdkVersion)
    }

    @Test
    fun telemetryProvider_collectsDeviceMetricsSafely() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val telemetry = DeviceTelemetryProvider(context).collect()
        assertTrue(telemetry.apiLevel >= 19)
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
    fun sqliteStore_savesChannelsBookmarksAndClearsHistorySafely() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = TabTubeSqliteStore(context)
        try {
            store.insertChannels(TabTubeSqliteStore.DEFAULT_CHANNELS)
            assertEquals(TabTubeSqliteStore.DEFAULT_CHANNELS.size, store.getChannelsOnce().size)

            store.upsertSavedVideo(
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
            store.upsertSavedVideo(
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

            store.clearWatchHistory()
            val bookmarks = store.observeBookmarks().first()
            val history = store.observeHistory().first()
            assertEquals(1, bookmarks.size)
            assertEquals("vidBookmark1", bookmarks.first().videoId)
            assertEquals(1, history.size)
        } finally {
            store.close()
        }
    }

    @Test
    fun preferences_enforcesBridgeSecurityAndPersistsSettings() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = OptimizationPreferences(context)
        prefs.updateQuality(QualityPreset.ECO_360P)
        assertFalse(prefs.updateCustomBridgeUrl("http://evil.com"))
        assertFalse(prefs.updateCustomBridgeUrl("http://192.168.1.40:8787"))
        prefs.updateAllowLanBridge(true)
        assertTrue(prefs.updateCustomBridgeUrl("http://192.168.1.40:8787"))

        val loaded = prefs.settingsFlow.first()
        assertEquals(QualityPreset.ECO_360P, loaded.qualityPreset)
        assertEquals("http://192.168.1.40:8787", loaded.customBridgeUrl)
    }

    @Test
    fun localTlsStreamProxy_rejectsUnauthorizedRequestsWithoutToken() {
        val proxy = LocalTlsStreamProxy()
        try {
            val localUrl = proxy.registerStreamUrl("https://example.com/video.mp4")
            assertTrue(localUrl.startsWith("http://127.0.0.1:"))
            val port = localUrl.substringAfter("127.0.0.1:").substringBefore("/").toInt()

            val conn = URL("http://127.0.0.1:$port/stream?id=1&token=wrongtoken")
                .openConnection() as HttpURLConnection
            assertEquals(403, conn.responseCode)
            conn.disconnect()
        } finally {
            proxy.stop()
        }
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp")
    fun mainActivity_launchesClassicTabletShellWithoutCrash() {
        val activity = Robolectric.buildActivity(MainActivity::class.java)
            .create()
            .start()
            .resume()
            .get()
        assertNotNull(activity.window.decorView)
        activity.finish()
    }

    @Test
    fun legacyPanels_renderShortsAndDisableUnavailableH264Safely() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val feedPanel = FeedPanel(
            context = context,
            isWideTablet = true,
            onSelectChannel = {},
            onRemoveChannel = {},
            onRefresh = {},
            onOpenAddChannel = {},
            onVideoClick = {},
            onBookmarkToggle = {},
            onOpenDiagnostics = {}
        )
        feedPanel.render(
            feedState = FeedUiState.Success(
                listOf(
                    VideoItem("vidRegular01", "Doc", "Sci", thumbnailUrl = "https://i.ytimg.com/vi/vidRegular01/mqdefault.jpg"),
                    VideoItem("vidShort0001", "Short", "Sci", thumbnailUrl = "https://i.ytimg.com/vi/vidShort0001/mqdefault.jpg", isShort = true)
                )
            ),
            channels = listOf(ChannelEntity("UC1", "Sci", "Ciência")),
            selectedChannelId = null,
            bookmarkedIds = emptySet(),
            settings = OptimizationSettings(),
            telemetry = sampleTelemetry()
        )
        assertEquals(View.VISIBLE, feedPanel.root.visibility)

        val playerPanel = PlayerPanel(
            context = context,
            onBack = {},
            onSetFullscreen = {},
            onSwitchBackend = {},
            onPositionChanged = { _, _ -> },
            onBookmarkToggle = {},
            onToggleSubscribe = { _, _ -> },
            onSelectRelated = {},
            onRetryVideo = {}
        )
        playerPanel.render(
            playerState = PlayerUiState.Ready(
                stream = ResolvedStream(
                    videoId = "abcDEF12345",
                    title = "Retro Hardware Short",
                    channelName = "RetroLab",
                    channelId = "UCretro123",
                    thumbnailUrl = "https://i.ytimg.com/vi/abcDEF12345/mqdefault.jpg",
                    description = "Short vertical",
                    directH264Url = null,
                    qualityLabel = "480p SD",
                    codecLabel = "Embed Direto",
                    embedUrl = "https://www.youtube-nocookie.com/embed/abcDEF12345",
                    mobileWatchUrl = "https://m.youtube.com/shorts/abcDEF12345",
                    resolvedVia = "Shorts Direto",
                    isShort = true
                ),
                activeBackend = ActivePlayerBackend.IFRAME_EMBED,
                resumePositionMs = 0L
            ),
            relatedVideos = listOf(
                VideoItem("nextShort11", "Próximo Short", "RetroLab", thumbnailUrl = "https://i.ytimg.com/vi/nextShort11/mqdefault.jpg", isShort = true)
            ),
            bookmarkedIds = emptySet(),
            followedChannelIds = emptySet(),
            isFullscreen = false,
            settings = OptimizationSettings()
        )

        val h264Btn = findViewByContentDesc(playerPanel.root, "chip_native_engine") as Button
        val aspectBtn = findViewByContentDesc(playerPanel.root, "toggle_short_aspect_button") as Button
        val nextShortBtn = findViewByContentDesc(playerPanel.root, "next_short_button") as Button
        assertFalse(h264Btn.isEnabled)
        assertEquals(View.VISIBLE, aspectBtn.visibility)
        assertEquals(View.VISIBLE, nextShortBtn.visibility)
        playerPanel.release()

        val libPanel = LibraryPanel(context, {}, {}, {})
        libPanel.render(emptyList(), emptyList(), emptySet(), OptimizationSettings())
        assertEquals(View.VISIBLE, libPanel.root.visibility)
    }

    private fun findViewByContentDesc(root: View, desc: String): View? {
        if (root.contentDescription?.toString() == desc) return root
        if (root is android.view.ViewGroup) {
            for (i in 0 until root.childCount) {
                val found = findViewByContentDesc(root.getChildAt(i), desc)
                if (found != null) return found
            }
        }
        return null
    }

    private fun sampleTelemetry() = DeviceTelemetry(
        deviceModel = "SM-T560",
        manufacturer = "Samsung",
        androidVersion = "4.4.4",
        apiLevel = 19,
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
