package tech.devline.scropy_ui.adb

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Plain TCP transport for ADB (pre-Android 11 devices that don't support STLS/TLS).
 */
class TcpAdbTransport private constructor(private val socket: Socket) : AdbTransport {

    companion object {
        fun connect(host: String, port: Int, timeoutMs: Int = 10_000): TcpAdbTransport {
            val socket = Socket()
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            socket.tcpNoDelay = true
            return TcpAdbTransport(socket)
        }
    }

    override fun read(len: Int): ByteArray? {
        return try {
            val buf = ByteArray(len)
            var off = 0
            val inp = socket.getInputStream()
            while (off < len) {
                val r = inp.read(buf, off, len - off)
                if (r < 0) return null
                off += r
            }
            buf
        } catch (_: IOException) {
            null
        }
    }

    override fun write(data: ByteArray): Boolean {
        return try {
            socket.getOutputStream().write(data)
            socket.getOutputStream().flush()
            true
        } catch (_: IOException) {
            false
        }
    }

    override fun shutdown() {
        try { socket.shutdownInput() } catch (_: IOException) {}
    }

    override fun close() {
        try { socket.close() } catch (_: IOException) {}
    }
}
