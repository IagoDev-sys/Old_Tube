package com.example

import com.example.data.remote.YouTubeLegacyNetwork
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeLegacyNetworkTest {
    private val network = YouTubeLegacyNetwork()

    @Test
    fun extractVideoId_handlesStandardAndShortUrls() {
        assertEquals("dQw4w9WgXcQ", network.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", network.extractVideoId("https://youtu.be/dQw4w9WgXcQ?t=12"))
        assertEquals("dQw4w9WgXcQ", network.extractVideoId("https://youtube.com/shorts/dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", network.extractVideoId("dQw4w9WgXcQ"))
        assertNull(network.extractVideoId("not-a-valid-video"))
    }

    @Test
    fun buildThumbnailUrl_usesMqDefaultInLowRamMode() {
        assertEquals(
            "https://i.ytimg.com/vi/dQw4w9WgXcQ/mqdefault.jpg",
            network.buildThumbnailUrl("dQw4w9WgXcQ", lowRamThumbnails = true)
        )
        assertEquals(
            "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
            network.buildThumbnailUrl("dQw4w9WgXcQ", lowRamThumbnails = false)
        )
    }
}
