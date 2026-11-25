package com.example.data.remote

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.zip.GZIPInputStream
import javax.net.ssl.HttpsURLConnection

/**
 * Zero-dependency HTTP/HTTPS client using HttpsURLConnection + KitKatTls12SocketFactory.
 * Runs cleanly on Android 4.4 (API 19) through Android 16 (API 36).
 */
class KitKatHttpClient(
    private val sslFactory: KitKatTls12SocketFactory = KitKatTls12SocketFactory()
) {
    data class HttpResponse(
        val code: Int,
        val body: String
    ) {
        val isSuccessful: Boolean get() = code in 200..299
    }

    fun getText(
        rawUrl: String,
        headers: Map<String, String> = emptyMap(),
        allowPrivateLanHttp: Boolean = false,
        connectTimeoutMs: Int = 6000,
        readTimeoutMs: Int = 9000
    ): HttpResponse? {
        val conn = openSafeConnection(
            rawUrl = rawUrl,
            method = "GET",
            headers = headers,
            bodyBytes = null,
            allowPrivateLanHttp = allowPrivateLanHttp,
            connectTimeoutMs = connectTimeoutMs,
            readTimeoutMs = readTimeoutMs,
            redirectsRemaining = 4
        ) ?: return null

        return try {
            val code = conn.responseCode
            val stream = if (code in 200..399) conn.inputStream else conn.errorStream
            val text = stream?.let { readStreamText(it, conn.contentEncoding) } ?: ""
            HttpResponse(code, text)
        } finally {
            conn.disconnect()
        }
    }

    fun postJson(
        rawUrl: String,
        jsonBody: String,
        headers: Map<String, String> = emptyMap(),
        allowPrivateLanHttp: Boolean = false,
        connectTimeoutMs: Int = 6000,
        readTimeoutMs: Int = 9000
    ): HttpResponse? {
        val allHeaders = headers.toMutableMap().apply {
            put("Content-Type", "application/json; charset=utf-8")
        }
        val conn = openSafeConnection(
            rawUrl = rawUrl,
            method = "POST",
            headers = allHeaders,
            bodyBytes = jsonBody.toByteArray(Charsets.UTF_8),
            allowPrivateLanHttp = allowPrivateLanHttp,
            connectTimeoutMs = connectTimeoutMs,
            readTimeoutMs = readTimeoutMs,
            redirectsRemaining = 2
        ) ?: return null

        return try {
            val code = conn.responseCode
            val stream = if (code in 200..399) conn.inputStream else conn.errorStream
            val text = stream?.let { readStreamText(it, conn.contentEncoding) } ?: ""
            HttpResponse(code, text)
        } finally {
            conn.disconnect()
        }
    }

    fun openStream(
        rawUrl: String,
        headers: Map<String, String> = emptyMap(),
        allowPrivateLanHttp: Boolean = false,
        connectTimeoutMs: Int = 6000,
        readTimeoutMs: Int = 12000
    ): HttpURLConnection? {
        return openSafeConnection(
            rawUrl = rawUrl,
            method = "GET",
            headers = headers,
            bodyBytes = null,
            allowPrivateLanHttp = allowPrivateLanHttp,
            connectTimeoutMs = connectTimeoutMs,
            readTimeoutMs = readTimeoutMs,
            redirectsRemaining = 5
        )
    }

    private fun openSafeConnection(
        rawUrl: String,
        method: String,
        headers: Map<String, String>,
        bodyBytes: ByteArray?,
        allowPrivateLanHttp: Boolean,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
        redirectsRemaining: Int
    ): HttpURLConnection? {
        if (!isPermittedTargetUrl(rawUrl, allowPrivateLanHttp)) return null

        val url = URL(rawUrl)
        val conn = url.openConnection() as? HttpURLConnection ?: return null
        if (conn is HttpsURLConnection) {
            conn.sslSocketFactory = sslFactory
        }
        conn.requestMethod = method
        conn.connectTimeout = connectTimeoutMs
        conn.readTimeout = readTimeoutMs
        conn.instanceFollowRedirects = false
        conn.setRequestProperty("Accept-Encoding", "gzip")
        for ((k, v) in headers) {
            conn.setRequestProperty(k, v)
        }

        if (bodyBytes != null) {
            conn.doOutput = true
            conn.outputStream.use { it.write(bodyBytes) }
        }

        val code = conn.responseCode
        if (code in 301..303 || code == 307 || code == 308) {
            val location = conn.getHeaderField("Location")
            conn.disconnect()
            if (location.isNullOrBlank() || redirectsRemaining <= 0) return null
            val resolved = runCatching { URI(rawUrl).resolve(location).toString() }.getOrNull()
                ?: return null
            return openSafeConnection(
                rawUrl = resolved,
                method = if (code == 307 || code == 308) method else "GET",
                headers = headers,
                bodyBytes = if (code == 307 || code == 308) bodyBytes else null,
                allowPrivateLanHttp = allowPrivateLanHttp,
                connectTimeoutMs = connectTimeoutMs,
                readTimeoutMs = readTimeoutMs,
                redirectsRemaining = redirectsRemaining - 1
            )
        }
        return conn
    }

    private fun readStreamText(input: InputStream, contentEncoding: String?): String {
        val wrapped = if (contentEncoding.equals("gzip", ignoreCase = true)) {
            GZIPInputStream(input)
        } else {
            input
        }
        wrapped.use { stream ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (true) {
                val read = stream.read(buf)
                if (read <= 0) break
                out.write(buf, 0, read)
            }
            return out.toString("UTF-8")
        }
    }

    companion object {
        fun isPermittedTargetUrl(rawUrl: String, allowPrivateLanHttp: Boolean): Boolean {
            val uri = runCatching { URI(rawUrl.trim()) }.getOrNull() ?: return false
            if (!uri.userInfo.isNullOrEmpty()) return false
            val scheme = uri.scheme?.lowercase() ?: return false
            val host = uri.host?.lowercase() ?: return false
            if (scheme == "https") return host.isNotBlank()
            if (scheme == "http" && allowPrivateLanHttp) {
                return YouTubeLegacyNetwork.isSafeLocalLanBridgeUrl(rawUrl)
            }
            return false
        }
    }
}
