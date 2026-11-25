package com.example.classic

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.graphics.Color
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.MediaController
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.VideoView
import com.example.data.model.ActivePlayerBackend
import com.example.data.model.ResolvedStream
import com.example.data.remote.WebViewSecurityPolicy

/**
 * Dalvik-safe hybrid player stage supporting:
 * 1. NATIVE_H264: Hardware Stagefright VideoView fed via LocalTlsStreamProxy (TLS 1.2 compatible on KitKat)
 * 2. IFRAME_EMBED / MOBILE_WEB: Secured WebView with strict URL allowlist and version-isolated WebViewClient
 *    (avoids Dalvik VerifyError on Android 4.4 when referencing API 21/26 WebView signatures).
 */
class SafeLegacyPlayerStage(
    context: Context,
    private val onFallbackToEmbed: () -> Unit,
    private val onFallbackToMobile: () -> Unit,
    private val onPositionSaved: (Long) -> Unit
) : FrameLayout(context) {

    private var videoView: VideoView? = null
    private var webView: WebView? = null
    private var currentKey: String = ""

    init {
        setBackgroundColor(Color.BLACK)
    }

    fun bind(stream: ResolvedStream, backend: ActivePlayerBackend, resumeMs: Long) {
        val nextKey = "${stream.videoId}|$backend|${stream.directH264Url ?: ""}"
        if (nextKey == currentKey) return
        currentKey = nextKey
        releaseMedia()

        when (backend) {
            ActivePlayerBackend.NATIVE_H264 -> {
                val url = stream.directH264Url
                if (url.isNullOrBlank()) {
                    onFallbackToEmbed()
                } else {
                    mountNativeVideoView(url, resumeMs)
                }
            }
            ActivePlayerBackend.IFRAME_EMBED -> {
                mountSecuredWebView(
                    targetUrl = stream.embedUrl,
                    desktopUa = false,
                    wrapIframeHtml = true,
                    fallbackAction = onFallbackToMobile
                )
            }
            ActivePlayerBackend.MOBILE_WEB -> {
                mountSecuredWebView(
                    targetUrl = stream.mobileWatchUrl,
                    desktopUa = false,
                    wrapIframeHtml = false,
                    fallbackAction = {}
                )
            }
        }
    }

    private fun mountNativeVideoView(streamUrl: String, resumeMs: Long) {
        val loading = ProgressBar(context).apply {
            isIndeterminate = true
        }
        val vv = VideoView(context)
        val controller = MediaController(context)
        controller.setAnchorView(vv)
        vv.setMediaController(controller)

        vv.setOnPreparedListener { mp ->
            loading.visibility = GONE
            if (resumeMs > 1000L) {
                mp.seekTo(resumeMs.toInt())
            }
            mp.setOnInfoListener { _, what, _ ->
                if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) loading.visibility = VISIBLE
                if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) loading.visibility = GONE
                false
            }
            vv.start()
        }
        vv.setOnErrorListener { _, _, _ ->
            loading.visibility = GONE
            onFallbackToEmbed()
            true
        }
        vv.setOnCompletionListener {
            onPositionSaved(0L)
        }

        addView(
            vv,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER)
        )
        addView(
            loading,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        )
        vv.setVideoURI(Uri.parse(streamUrl))
        videoView = vv
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun mountSecuredWebView(
        targetUrl: String,
        desktopUa: Boolean,
        wrapIframeHtml: Boolean,
        fallbackAction: () -> Unit
    ) {
        if (!WebViewSecurityPolicy.isAllowedNavigationUrl(targetUrl)) {
            showBlockedNotice()
            return
        }
        val loading = ProgressBar(context).apply { isIndeterminate = true }

        val wv = WebView(context).apply {
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.setGeolocationEnabled(false)
            settings.javaScriptCanOpenWindowsAutomatically = false
            @Suppress("DEPRECATION")
            settings.allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            settings.allowUniversalAccessFromFileURLs = false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            }
            settings.userAgentString = if (desktopUa) {
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            } else {
                "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}; ${Build.MODEL}) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            }

            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                cookieManager.setAcceptThirdPartyCookies(this, true)
            }

            webChromeClient = WebChromeClient()
            webViewClient = createSafeWebViewClient(
                onLoaded = { loading.visibility = GONE },
                onBlocked = { showBlockedNotice() },
                onErrorFallback = {
                    loading.visibility = GONE
                    fallbackAction()
                }
            )
        }

        addView(wv, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(
            loading,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        )

        if (wrapIframeHtml) {
            val safeSrc = targetUrl.replace("\"", "&quot;")
            val html = """
                <!DOCTYPE html>
                <html><head>
                <meta name="viewport" content="width=device-width,initial-scale=1.0,maximum-scale=1.0">
                <style>html,body{margin:0;padding:0;width:100%;height:100%;background:#000;overflow:hidden;}
                iframe{width:100%;height:100%;border:0;}</style>
                </head><body>
                <iframe src="$safeSrc" allow="autoplay; encrypted-media; fullscreen" allowfullscreen></iframe>
                </body></html>
            """.trimIndent()
            wv.loadDataWithBaseURL("https://www.youtube-nocookie.com/", html, "text/html", "UTF-8", null)
        } else {
            wv.loadUrl(targetUrl)
        }
        webView = wv
    }

    private fun showBlockedNotice() {
        removeAllViews()
        val tv = TextView(context).apply {
            text = "Navegação bloqueada pela política de segurança do OLD Tube."
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(context.dp(16), context.dp(16), context.dp(16), context.dp(16))
        }
        addView(tv, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun releaseMedia() {
        videoView?.let { vv ->
            runCatching {
                if (vv.isPlaying) {
                    onPositionSaved(vv.currentPosition.toLong())
                }
                vv.stopPlayback()
            }
        }
        videoView = null

        webView?.let { wv ->
            runCatching {
                wv.stopLoading()
                wv.loadUrl("about:blank")
                wv.clearHistory()
                (wv.parent as? ViewGroup)?.removeView(wv)
                wv.destroy()
            }
        }
        webView = null
        removeAllViews()
    }

    private fun createSafeWebViewClient(
        onLoaded: () -> Unit,
        onBlocked: () -> Unit,
        onErrorFallback: () -> Unit
    ): WebViewClient {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ->
                OreoSafeWebViewClient(onLoaded, onBlocked, onErrorFallback)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ->
                LollipopSafeWebViewClient(onLoaded, onBlocked, onErrorFallback)
            else ->
                KitKatSafeWebViewClient(onLoaded, onBlocked, onErrorFallback)
        }
    }
}

private open class KitKatSafeWebViewClient(
    private val onLoaded: () -> Unit,
    protected val onBlocked: () -> Unit,
    private val onErrorFallback: () -> Unit
) : WebViewClient() {

    @Suppress("DEPRECATION")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        val allowed = WebViewSecurityPolicy.isAllowedNavigationUrl(url)
        if (!allowed) onBlocked()
        return !allowed
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        onLoaded()
    }

    @Suppress("DEPRECATION")
    override fun onReceivedError(
        view: WebView?,
        errorCode: Int,
        description: String?,
        failingUrl: String?
    ) {
        super.onReceivedError(view, errorCode, description, failingUrl)
        onErrorFallback()
    }
}

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
private open class LollipopSafeWebViewClient(
    onLoaded: () -> Unit,
    onBlocked: () -> Unit,
    onErrorFallback: () -> Unit
) : KitKatSafeWebViewClient(onLoaded, onBlocked, onErrorFallback) {

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString()
        val allowed = WebViewSecurityPolicy.isAllowedNavigationUrl(url)
        if (!allowed) onBlocked()
        return !allowed
    }
}

@TargetApi(Build.VERSION_CODES.O)
private class OreoSafeWebViewClient(
    onLoaded: () -> Unit,
    onBlocked: () -> Unit,
    private val onErrorFallback: () -> Unit
) : LollipopSafeWebViewClient(onLoaded, onBlocked, onErrorFallback) {

    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
        onErrorFallback()
        return true
    }
}
