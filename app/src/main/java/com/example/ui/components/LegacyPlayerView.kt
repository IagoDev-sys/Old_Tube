package com.example.ui.components

import android.annotation.SuppressLint
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.data.model.ActivePlayerBackend
import com.example.data.model.ResolvedStream
import com.example.ui.theme.ClassicTubeRedBright
import kotlinx.coroutines.delay

@Composable
fun LegacyPlayerView(
    stream: ResolvedStream,
    activeBackend: ActivePlayerBackend,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onSwitchBackend: (ActivePlayerBackend) -> Unit,
    onPositionChanged: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(Color.Black)
    ) {
        when (activeBackend) {
            ActivePlayerBackend.NATIVE_H264 -> {
                if (stream.directH264Url != null) {
                    NativeHardwareVideoPlayer(
                        streamUrl = stream.directH264Url,
                        qualityLabel = stream.qualityLabel,
                        codecLabel = stream.codecLabel,
                        isFullscreen = isFullscreen,
                        onToggleFullscreen = onToggleFullscreen,
                        onFallback = { onSwitchBackend(ActivePlayerBackend.IFRAME_EMBED) },
                        onSwitchToMobile = { onSwitchBackend(ActivePlayerBackend.MOBILE_WEB) },
                        onPositionChanged = onPositionChanged,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    DirectEmbedWebPlayer(
                        embedUrl = stream.embedUrl,
                        isFullscreen = isFullscreen,
                        onToggleFullscreen = onToggleFullscreen,
                        onSwitchToMobile = { onSwitchBackend(ActivePlayerBackend.MOBILE_WEB) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            ActivePlayerBackend.IFRAME_EMBED -> {
                DirectEmbedWebPlayer(
                    embedUrl = stream.embedUrl,
                    isFullscreen = isFullscreen,
                    onToggleFullscreen = onToggleFullscreen,
                    onSwitchToMobile = { onSwitchBackend(ActivePlayerBackend.MOBILE_WEB) },
                    modifier = Modifier.fillMaxSize()
                )
            }

            ActivePlayerBackend.MOBILE_WEB -> {
                MobileWatchWebPlayer(
                    mobileWatchUrl = stream.mobileWatchUrl,
                    isFullscreen = isFullscreen,
                    onToggleFullscreen = onToggleFullscreen,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun NativeHardwareVideoPlayer(
    streamUrl: String,
    qualityLabel: String,
    codecLabel: String,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onFallback: () -> Unit,
    onSwitchToMobile: () -> Unit,
    onPositionChanged: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var isPrepared by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var durationMs by remember { mutableIntStateOf(0) }
    var positionMs by remember { mutableIntStateOf(0) }
    var showControls by remember { mutableStateOf(true) }
    var isScrubbing by remember { mutableStateOf(false) }

    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(3600)
            showControls = false
        }
    }

    LaunchedEffect(isPrepared, isPlaying, isScrubbing) {
        while (isPrepared && isPlaying && !isScrubbing) {
            val view = videoViewRef
            if (view != null) {
                val pos = runCatching { view.currentPosition }.getOrDefault(0)
                positionMs = pos
                onPositionChanged(pos.toLong())
            }
            delay(500)
        }
    }

    DisposableEffect(streamUrl) {
        onDispose {
            runCatching { videoViewRef?.stopPlayback() }
        }
    }

    Box(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { showControls = !showControls }
    ) {
        key(streamUrl) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                FrameLayout(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    val vv = VideoView(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.Gravity.CENTER
                        )
                        setOnPreparedListener { mp ->
                            isPrepared = true
                            isBuffering = false
                            durationMs = mp.duration.coerceAtLeast(0)
                            mp.setOnInfoListener { _, what, _ ->
                                when (what) {
                                    MediaPlayer.MEDIA_INFO_BUFFERING_START -> isBuffering = true
                                    MediaPlayer.MEDIA_INFO_BUFFERING_END -> isBuffering = false
                                }
                                false
                            }
                            start()
                            isPlaying = true
                        }
                        setOnCompletionListener {
                            isPlaying = false
                            showControls = true
                        }
                        setOnErrorListener { _, _, _ ->
                            isBuffering = false
                            onFallback()
                            true
                        }
                        setVideoURI(Uri.parse(streamUrl))
                    }
                    videoViewRef = vv
                    addView(vv)
                }
            }
        )
        }

        if (isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = ClassicTubeRedBright
            )
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color.Black.copy(alpha = 0.72f)
                    ) {
                        Text(
                            text = "$codecLabel • $qualityLabel",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF33B5E5),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable(onClick = onSwitchToMobile)
                                .testTag("switch_to_mobile_button"),
                            shape = RoundedCornerShape(4.dp),
                            color = Color.Black.copy(alpha = 0.72f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SwapHoriz,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Mobile",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = onToggleFullscreen,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("fullscreen_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (isFullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                                contentDescription = stringResource(
                                    if (isFullscreen) R.string.action_exit_fullscreen else R.string.action_fullscreen
                                ),
                                tint = Color.White
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val vv = videoViewRef ?: return@IconButton
                            val target = (positionMs - 10_000).coerceAtLeast(0)
                            vv.seekTo(target)
                            positionMs = target
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .testTag("seek_back_10s")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Replay10,
                            contentDescription = "Voltar 10 segundos",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            val vv = videoViewRef ?: return@IconButton
                            if (vv.isPlaying) {
                                vv.pause()
                                isPlaying = false
                            } else {
                                vv.start()
                                isPlaying = true
                            }
                        },
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(ClassicTubeRedBright)
                            .testTag("play_pause_button")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Pausar" else "Reproduzir",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            val vv = videoViewRef ?: return@IconButton
                            val target = (positionMs + 10_000).coerceAtMost(durationMs)
                            vv.seekTo(target)
                            positionMs = target
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .testTag("seek_forward_10s")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Forward10,
                            contentDescription = "Avançar 10 segundos",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                if (durationMs > 0) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatMs(positionMs),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White
                            )
                            Text(
                                text = formatMs(durationMs),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                        Slider(
                            value = positionMs.toFloat().coerceIn(0f, durationMs.toFloat()),
                            onValueChange = { newValue ->
                                isScrubbing = true
                                positionMs = newValue.toInt()
                            },
                            onValueChangeFinished = {
                                videoViewRef?.seekTo(positionMs)
                                isScrubbing = false
                            },
                            valueRange = 0f..durationMs.toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = ClassicTubeRedBright,
                                activeTrackColor = ClassicTubeRedBright,
                                inactiveTrackColor = Color.White.copy(alpha = 0.35f)
                            )
                        )
                    }
                }
            }
        }
    }
}

private fun configureWebViewCookies(webView: WebView) {
    runCatching {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)
        cookieManager.setCookie("https://www.youtube.com", "CONSENT=YES+cb.20210328-17-p0.en+FX+999; path=/")
        cookieManager.setCookie("https://m.youtube.com", "CONSENT=YES+cb.20210328-17-p0.en+FX+999; path=/")
        cookieManager.setCookie("https://www.youtube-nocookie.com", "CONSENT=YES+cb.20210328-17-p0.en+FX+999; path=/")
        cookieManager.flush()
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun DirectEmbedWebPlayer(
    embedUrl: String,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onSwitchToMobile: () -> Unit,
    modifier: Modifier = Modifier
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    DisposableEffect(embedUrl) {
        onDispose {
            runCatching {
                webViewRef?.apply {
                    stopLoading()
                    loadUrl("about:blank")
                    clearHistory()
                    removeAllViews()
                    destroy()
                }
            }
        }
    }

    Box(modifier = modifier) {
        key(embedUrl) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setBackgroundColor(android.graphics.Color.BLACK)
                        configureWebViewCookies(this)
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            mediaPlaybackRequiresUserGesture = false
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            cacheMode = WebSettings.LOAD_DEFAULT
                            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            userAgentString =
                                "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                        }
                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val url = request?.url ?: return false
                                val scheme = url.scheme?.lowercase() ?: ""
                                if (scheme != "http" && scheme != "https") {
                                    return true
                                }
                                return false
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                            }

                            override fun onRenderProcessGone(
                                view: WebView?,
                                detail: RenderProcessGoneDetail?
                            ): Boolean {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    isLoading = false
                                    return true
                                }
                                return super.onRenderProcessGone(view, detail)
                            }
                        }
                        loadUrl(embedUrl, mapOf("Referer" to "https://www.youtube.com/"))
                        webViewRef = this
                    }
                }
            )
        }

        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = ClassicTubeRedBright
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onSwitchToMobile)
                    .testTag("switch_iframe_to_mobile_button"),
                shape = RoundedCornerShape(4.dp),
                color = Color.Black.copy(alpha = 0.7f)
            ) {
                Text(
                    text = "Mobile Direto",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
                onClick = onToggleFullscreen,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f))
                    .minimumInteractiveComponentSize()
                    .testTag("lite_fullscreen_button")
            ) {
                Icon(
                    imageVector = if (isFullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                    contentDescription = stringResource(
                        if (isFullscreen) R.string.action_exit_fullscreen else R.string.action_fullscreen
                    ),
                    tint = Color.White
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MobileWatchWebPlayer(
    mobileWatchUrl: String,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    DisposableEffect(mobileWatchUrl) {
        onDispose {
            runCatching {
                webViewRef?.apply {
                    stopLoading()
                    loadUrl("about:blank")
                    clearHistory()
                    removeAllViews()
                    destroy()
                }
            }
        }
    }

    Box(modifier = modifier) {
        key(mobileWatchUrl) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setBackgroundColor(android.graphics.Color.BLACK)
                        configureWebViewCookies(this)
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            javaScriptCanOpenWindowsAutomatically = true
                            mediaPlaybackRequiresUserGesture = false
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            cacheMode = WebSettings.LOAD_DEFAULT
                            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            userAgentString =
                                "Mozilla/5.0 (Linux; Android 11; SM-T560) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                        }
                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val url = request?.url ?: return false
                                val scheme = url.scheme?.lowercase() ?: ""
                                if (scheme != "http" && scheme != "https") {
                                    return true
                                }
                                return false
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                                runCatching { CookieManager.getInstance().flush() }
                                view?.evaluateJavascript(
                                    """
                                    (function(){
                                        try {
                                            var style = document.createElement('style');
                                            style.innerHTML = 'ytm-mobile-topbar-renderer, .mobile-topbar-header, ytm-promoted-sparkles-web-renderer, ytm-companion-ad-renderer, .upsell-dialog-wiz, a[href*="app_install"] { display: none !important; } #player-container-id { top: 0 !important; }';
                                            document.head.appendChild(style);
                                        } catch(e) {}
                                    })();
                                    """.trimIndent(),
                                    null
                                )
                            }

                            override fun onRenderProcessGone(
                                view: WebView?,
                                detail: RenderProcessGoneDetail?
                            ): Boolean {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    isLoading = false
                                    return true
                                }
                                return super.onRenderProcessGone(view, detail)
                            }
                        }
                        loadUrl(mobileWatchUrl)
                        webViewRef = this
                    }
                }
            )
        }

        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = ClassicTubeRedBright
            )
        }

        IconButton(
            onClick = onToggleFullscreen,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.65f))
                .minimumInteractiveComponentSize()
                .testTag("mobile_fullscreen_button")
        ) {
            Icon(
                imageVector = if (isFullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                contentDescription = stringResource(
                    if (isFullscreen) R.string.action_exit_fullscreen else R.string.action_fullscreen
                ),
                tint = Color.White
            )
        }
    }
}

private fun formatMs(ms: Int): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
    else String.format("%d:%02d", m, s)
}
