package com.example.data.remote

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.lang.ref.WeakReference
import java.util.concurrent.Executors

/**
 * Lightweight, zero-dependency async thumbnail loader designed for Android 4.4 low-RAM devices.
 * Caps memory cache to ~12% of Dalvik heap and decodes with RGB_565 when Low-RAM mode is enabled.
 */
object ThumbnailCacheLoader {
    private val maxKb = ((Runtime.getRuntime().maxMemory() / 1024L) / 8L).toInt().coerceAtLeast(2048)
    private val cache = object : LruCache<String, Bitmap>(maxKb) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.byteCount / 1024).coerceAtLeast(1)
    }
    private val executor = Executors.newFixedThreadPool(3)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val http = KitKatHttpClient()

    fun load(imageView: ImageView, url: String, lowRamRgb565: Boolean) {
        imageView.tag = url
        if (url.isBlank()) {
            imageView.setImageDrawable(null)
            return
        }
        val cacheKey = "$url|$lowRamRgb565"
        val cached = cache.get(cacheKey)
        if (cached != null && !cached.isRecycled) {
            imageView.setImageBitmap(cached)
            return
        }

        imageView.setImageDrawable(null)
        val viewRef = WeakReference(imageView)
        executor.execute {
            val bitmap = runCatching { fetchBitmap(url, lowRamRgb565) }.getOrNull() ?: return@execute
            cache.put(cacheKey, bitmap)
            mainHandler.post {
                val target = viewRef.get() ?: return@post
                if (target.tag == url) {
                    target.setImageBitmap(bitmap)
                }
            }
        }
    }

    fun clear() {
        cache.evictAll()
    }

    private fun fetchBitmap(url: String, lowRamRgb565: Boolean): Bitmap? {
        val conn = http.openStream(url, allowPrivateLanHttp = false) ?: return null
        return try {
            if (conn.responseCode !in 200..299) return null
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = if (lowRamRgb565) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
                inDither = lowRamRgb565
            }
            conn.inputStream.use { input ->
                BitmapFactory.decodeStream(input, null, opts)
            }
        } finally {
            conn.disconnect()
        }
    }
}
