package com.example

import com.example.data.remote.WebViewSecurityPolicy
import com.example.data.remote.YouTubeLegacyNetwork
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityAndInputValidationTest {

    private val network = YouTubeLegacyNetwork()

    @Test
    fun webViewPolicy_allowsTrustedYoutubeHttpsUrls() {
        assertTrue(WebViewSecurityPolicy.isAllowedNavigationUrl("https://www.youtube.com/embed/dQw4w9WgXcQ"))
        assertTrue(WebViewSecurityPolicy.isAllowedNavigationUrl("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ?autoplay=1"))
        assertTrue(WebViewSecurityPolicy.isAllowedNavigationUrl("https://m.youtube.com/shorts/abc123DEF45"))
        assertTrue(WebViewSecurityPolicy.isAllowedNavigationUrl("https://consent.youtube.com/m?continue=https://m.youtube.com"))
        assertTrue(WebViewSecurityPolicy.isAllowedNavigationUrl("https://accounts.google.com/ServiceLogin"))
        assertTrue(WebViewSecurityPolicy.isAllowedNavigationUrl("about:blank"))
    }

    @Test
    fun webViewPolicy_blocksDangerousSchemesCleartextAndPhishingDomains() {
        assertFalse(WebViewSecurityPolicy.isAllowedNavigationUrl(null))
        assertFalse(WebViewSecurityPolicy.isAllowedNavigationUrl(""))
        assertFalse(WebViewSecurityPolicy.isAllowedNavigationUrl("javascript:alert(document.cookie)"))
        assertFalse(WebViewSecurityPolicy.isAllowedNavigationUrl("file:///etc/passwd"))
        assertFalse(WebViewSecurityPolicy.isAllowedNavigationUrl("content://com.example.provider/secret"))
        assertFalse(WebViewSecurityPolicy.isAllowedNavigationUrl("intent://watch?v=dQw4w9WgXcQ#Intent;end"))
        assertFalse(WebViewSecurityPolicy.isAllowedNavigationUrl("vnd.youtube:dQw4w9WgXcQ"))
        assertFalse(WebViewSecurityPolicy.isAllowedNavigationUrl("http://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertFalse(WebViewSecurityPolicy.isAllowedNavigationUrl("https://evil.example.com/phish"))
        assertFalse(WebViewSecurityPolicy.isAllowedNavigationUrl("https://youtube.com.evil.com/watch"))
        assertFalse(WebViewSecurityPolicy.isAllowedNavigationUrl("https://attacker@www.youtube.com/watch"))
    }

    @Test
    fun extractVideoId_acceptsValidAndRejectsInjectionInputs() {
        assertEquals("dQw4w9WgXcQ", network.extractVideoId("https://www.youtube.com/shorts/dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", network.extractVideoId("https://www.youtube.com/live/dQw4w9WgXcQ"))
        assertNull(network.extractVideoId("../../etc/passwd"))
        assertNull(network.extractVideoId("javascript:alert(1)"))
        assertNull(network.extractVideoId("dQw4w9WgXcQ<script>"))
        assertNull(network.extractVideoId(""))
    }

    @Test
    fun bridgeUrlValidation_enforcesHttpsPublicHostsOnly() {
        assertTrue(YouTubeLegacyNetwork.isSafeBridgeUrl("https://inv.nadeko.net"))
        assertTrue(YouTubeLegacyNetwork.isSafeBridgeUrl("https://pipedapi.kavin.rocks/"))
        assertFalse(YouTubeLegacyNetwork.isSafeBridgeUrl("http://inv.nadeko.net"))
        assertFalse(YouTubeLegacyNetwork.isSafeBridgeUrl("https://localhost:8080"))
        assertFalse(YouTubeLegacyNetwork.isSafeBridgeUrl("https://127.0.0.1"))
        assertFalse(YouTubeLegacyNetwork.isSafeBridgeUrl("https://192.168.1.50"))
        assertFalse(YouTubeLegacyNetwork.isSafeBridgeUrl("javascript:alert(1)"))
        assertFalse(YouTubeLegacyNetwork.isSafeBridgeUrl("https://user:pass@inv.nadeko.net"))
    }

    @Test
    fun channelTargetNormalization_allowsYoutubeOrHandlesAndBlocksArbitraryHosts() {
        assertEquals(
            "https://www.youtube.com/@ManualdoMundo",
            YouTubeLegacyNetwork.normalizeChannelTargetUrl("@ManualdoMundo")
        )
        assertEquals(
            "https://www.youtube.com/@Kurzgesagt",
            YouTubeLegacyNetwork.normalizeChannelTargetUrl("Kurzgesagt")
        )
        assertEquals(
            "https://www.youtube.com/channel/UCKHhA5hN2UohhFDfNXB_cvQ",
            YouTubeLegacyNetwork.normalizeChannelTargetUrl("https://www.youtube.com/channel/UCKHhA5hN2UohhFDfNXB_cvQ")
        )
        assertNull(YouTubeLegacyNetwork.normalizeChannelTargetUrl("http://www.youtube.com/@ManualdoMundo"))
        assertNull(YouTubeLegacyNetwork.normalizeChannelTargetUrl("https://evil.com/@ManualdoMundo"))
        assertNull(YouTubeLegacyNetwork.normalizeChannelTargetUrl("bad handle with spaces"))
    }
}
