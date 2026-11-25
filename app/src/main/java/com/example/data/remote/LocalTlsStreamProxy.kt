package com.example.data.remote

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Local loopback HTTP->HTTPS(TLS 1.2) relay for Android 4.4 VideoView / Stagefright.
 * KitKat's native Stagefright C++ player often fails TLS 1.2/SNI handshakes directly;
 * routing via 127.0.0.1 with a random per-session token lets KitKatHw codecs play HTTPS streams.
 */
class LocalTlsStreamProxy(
    private val httpClient: KitKatHttpClient = KitKatHttpClient()
) {
    private val executor: ExecutorService = Executors.newCachedThreadPool()
    private val slots = ConcurrentHashMap<String, String>()
    private val counter = AtomicInteger(0)
    private val sessionToken: String = generateToken()

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var localPort: Int = -1

    @Synchronized
    fun ensureStarted(): Int {
        if (serverSocket != null && !serverSocket!!.isClosed && localPort > 0) {
            return localPort
        }
        val socket = ServerSocket(0, 6, InetAddress.getByName("127.0.0.1"))
        serverSocket = socket
        localPort = socket.localPort
        executor.execute { acceptLoop(socket) }
        return localPort
    }

    fun registerStreamUrl(upstreamUrl: String): String {
        val port = ensureStarted()
        val id = counter.incrementAndGet().toString()
        slots[id] = upstreamUrl
        return "http://127.0.0.1:$port/stream?id=$id&token=$sessionToken"
    }

    fun stop() {
        runCatching { serverSocket?.close() }
        serverSocket = null
        localPort = -1
        slots.clear()
    }

    private fun acceptLoop(server: ServerSocket) {
        while (!server.isClosed) {
            val client = runCatching { server.accept() }.getOrNull() ?: break
            executor.execute { handleClient(client) }
        }
    }

    private fun handleClient(socket: Socket) {
        socket.use { client ->
            runCatching {
                client.soTimeout = 10000
                val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) {
                    writeSimpleResponse(client.getOutputStream(), "400 Bad Request")
                    return
                }
                val path = parts[1]
                val headers = mutableMapOf<String, String>()
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) break
                    val colon = line.indexOf(':')
                    if (colon > 0) {
                        headers[line.substring(0, colon).trim().lowercase()] =
                            line.substring(colon + 1).trim()
                    }
                }

                val params = parseQueryParams(path)
                val token = params["token"]
                val id = params["id"]
                if (token != sessionToken || id.isNullOrBlank()) {
                    writeSimpleResponse(client.getOutputStream(), "403 Forbidden")
                    return
                }

                val upstream = slots[id]
                if (upstream.isNullOrBlank()) {
                    writeSimpleResponse(client.getOutputStream(), "404 Not Found")
                    return
                }

                val upstreamHeaders = mutableMapOf(
                    "User-Agent" to "Mozilla/5.0 (Linux; Android 4.4.4; SM-T560) AppleWebKit/537.36"
                )
                headers["range"]?.let { upstreamHeaders["Range"] = it }

                val allowLan = YouTubeLegacyNetwork.isSafeLocalLanBridgeUrl(upstream)
                val conn = httpClient.openStream(upstream, upstreamHeaders, allowPrivateLanHttp = allowLan)
                if (conn == null) {
                    writeSimpleResponse(client.getOutputStream(), "502 Bad Gateway")
                    return
                }

                try {
                    val code = conn.responseCode
                    val statusText = conn.responseMessage ?: "OK"
                    val out = client.getOutputStream()
                    val headerSb = StringBuilder()
                    headerSb.append("HTTP/1.1 $code $statusText\r\n")
                    conn.getHeaderField("Content-Type")?.let { headerSb.append("Content-Type: $it\r\n") }
                    conn.getHeaderField("Content-Length")?.let { headerSb.append("Content-Length: $it\r\n") }
                    conn.getHeaderField("Content-Range")?.let { headerSb.append("Content-Range: $it\r\n") }
                    conn.getHeaderField("Accept-Ranges")?.let { headerSb.append("Accept-Ranges: $it\r\n") }
                    headerSb.append("Connection: close\r\n\r\n")
                    out.write(headerSb.toString().toByteArray(Charsets.US_ASCII))

                    val input = if (code in 200..399) conn.inputStream else conn.errorStream
                    if (input != null) {
                        input.use { stream ->
                            val buf = ByteArray(16384)
                            while (true) {
                                val n = stream.read(buf)
                                if (n <= 0) break
                                out.write(buf, 0, n)
                            }
                        }
                    }
                    out.flush()
                } finally {
                    conn.disconnect()
                }
            }
        }
    }

    private fun parseQueryParams(path: String): Map<String, String> {
        val qIdx = path.indexOf('?')
        if (qIdx == -1 || qIdx == path.length - 1) return emptyMap()
        val map = mutableMapOf<String, String>()
        for (pair in path.substring(qIdx + 1).split("&")) {
            val eq = pair.indexOf('=')
            if (eq > 0) {
                map[pair.substring(0, eq)] = pair.substring(eq + 1)
            }
        }
        return map
    }

    private fun writeSimpleResponse(out: OutputStream, status: String) {
        out.write("HTTP/1.1 $status\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
        out.flush()
    }

    private fun generateToken(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
