package com.example.data.remote

import android.os.Build
import java.net.InetAddress
import java.net.Socket
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Enables TLSv1.2 explicitly on Android 4.1 - 4.4 (API 16..19), where OpenSSLSocketImpl
 * supports TLS 1.2 but leaves it disabled by default on HttpsURLConnection sockets.
 */
class KitKatTls12SocketFactory(
    private val delegate: SSLSocketFactory = createBaseSocketFactory()
) : SSLSocketFactory() {

    override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites

    override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites

    override fun createSocket(s: Socket, host: String, port: Int, autoClose: Boolean): Socket =
        patch(delegate.createSocket(s, host, port, autoClose))

    override fun createSocket(host: String, port: Int): Socket =
        patch(delegate.createSocket(host, port))

    override fun createSocket(
        host: String,
        port: Int,
        localHost: InetAddress,
        localPort: Int
    ): Socket = patch(delegate.createSocket(host, port, localHost, localPort))

    override fun createSocket(host: InetAddress, port: Int): Socket =
        patch(delegate.createSocket(host, port))

    override fun createSocket(
        address: InetAddress,
        port: Int,
        localAddress: InetAddress,
        localPort: Int
    ): Socket = patch(delegate.createSocket(address, port, localAddress, localPort))

    private fun patch(socket: Socket): Socket {
        if (socket is SSLSocket && Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            val supported = socket.supportedProtocols?.toSet() ?: emptySet()
            val preferred = listOf("TLSv1.2", "TLSv1.1", "TLSv1").filter { it in supported }
            if (preferred.isNotEmpty()) {
                socket.enabledProtocols = preferred.toTypedArray()
            }
        }
        return socket
    }

    companion object {
        private fun createBaseSocketFactory(): SSLSocketFactory {
            return runCatching {
                val context = SSLContext.getInstance("TLSv1.2")
                context.init(null, null, null)
                context.socketFactory
            }.getOrElse {
                SSLContext.getDefault().socketFactory
            }
        }
    }
}
