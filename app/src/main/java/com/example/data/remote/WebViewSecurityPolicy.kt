package com.example.data.remote

import java.net.URI

object WebViewSecurityPolicy {

    private val allowedHostSuffixes = listOf(
        "youtube.com",
        "youtube-nocookie.com",
        "youtu.be",
        "googlevideo.com",
        "ytimg.com",
        "ggpht.com",
        "gstatic.com",
        "googleusercontent.com",
        "accounts.google.com",
        "consent.google.com"
    )

    fun isAllowedNavigationUrl(rawUrl: String?): Boolean {
        if (rawUrl.isNullOrBlank()) return false
        val trimmed = rawUrl.trim()
        if (trimmed == "about:blank") return true

        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "https") return false
        if (!uri.userInfo.isNullOrEmpty()) return false

        val host = uri.host?.lowercase()?.trimEnd('.') ?: return false
        if (host.isBlank()) return false

        return allowedHostSuffixes.any { suffix ->
            host == suffix || host.endsWith(".$suffix")
        }
    }
}
