package com.chaosparty.net

import android.os.Handler
import android.os.Looper
import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.security.SecureRandom
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/** A connection that carries JSON messages: a LAN [Link] or an online [WsConn]. */
interface Conn {
    val closed: Boolean
    fun send(o: JSONObject)
    /** Must be called on the main thread. */
    fun close()
}

/**
 * Minimal WebSocket client (RFC 6455, text frames only) so the app needs no extra libraries.
 * Callbacks run on the main thread.
 */
class WsConn private constructor(
    private val onOpen: (WsConn) -> Unit,
    private val onLine: (WsConn, JSONObject) -> Unit,
    private val onClose: (WsConn, String?) -> Unit,
) : Conn {
    private val main = Handler(Looper.getMainLooper())
    private val sender = Executors.newSingleThreadScheduledExecutor()
    private val rng = SecureRandom()
    @Volatile private var socket: Socket? = null
    @Volatile private var out: OutputStream? = null
    @Volatile override var closed = false
        private set

    companion object {
        /** Connects in the background; [onClose] gets an error message if it never opened. */
        fun open(
            url: String,
            onOpen: (WsConn) -> Unit,
            onLine: (WsConn, JSONObject) -> Unit,
            onClose: (WsConn, String?) -> Unit,
        ): WsConn = WsConn(onOpen, onLine, onClose).also { it.start(url) }
    }

    private fun start(url: String) {
        Thread {
            try {
                val uri = URI(url)
                val secure = uri.scheme == "wss" || uri.scheme == "https"
                val host = uri.host ?: error("bad url")
                val port = if (uri.port > 0) uri.port else if (secure) 443 else 80
                val raw = Socket()
                raw.connect(InetSocketAddress(host, port), 8000)
                val s = if (secure) {
                    (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(raw, host, port, true).also { ssl ->
                        ssl as SSLSocket
                        runCatching { ssl.sslParameters = ssl.sslParameters.apply { serverNames = listOf(SNIHostName(host)) } }
                        ssl.startHandshake()
                    }
                } else raw
                s.tcpNoDelay = true
                socket = s
                val o = s.getOutputStream()
                val input = s.getInputStream()
                val key = Base64.encodeToString(ByteArray(16).also { rng.nextBytes(it) }, Base64.NO_WRAP)
                val path = (uri.rawPath ?: "/").ifEmpty { "/" } + (uri.rawQuery?.let { "?$it" } ?: "")
                val hostHeader = if (uri.port > 0) "$host:${uri.port}" else host
                o.write(("GET $path HTTP/1.1\r\nHost: $hostHeader\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                    "Sec-WebSocket-Key: $key\r\nSec-WebSocket-Version: 13\r\nUser-Agent: ChaosParty\r\n\r\n").toByteArray())
                o.flush()
                val status = readHeaders(input)
                if (!status.contains(" 101")) error("Máy chủ từ chối kết nối")
                out = o
                main.post { if (!closed) onOpen(this) }
                sender.scheduleWithFixedDelay({ runCatching { writeFrame(0x9, ByteArray(0)) } }, 20, 20, TimeUnit.SECONDS)
                readLoop(input)
                finish(null)
            } catch (e: Exception) {
                finish(if (out == null) "Không kết nối được máy chủ online" else null)
            }
        }.apply { isDaemon = true }.start()
    }

    private fun readHeaders(input: InputStream): String {
        val buf = ByteArrayOutputStream()
        var last4 = 0
        while (true) {
            val b = input.read()
            if (b < 0) error("closed")
            buf.write(b)
            last4 = (last4 shl 8) or b
            if (last4 == 0x0D0A0D0A) break
            if (buf.size() > 16_384) error("header too long")
        }
        return buf.toString("UTF-8").lineSequence().first()
    }

    private fun readLoop(input: InputStream) {
        val message = ByteArrayOutputStream()
        while (!closed) {
            val b0 = input.read()
            if (b0 < 0) return
            val b1 = input.read()
            if (b1 < 0) return
            val fin = b0 and 0x80 != 0
            val opcode = b0 and 0x0F
            var len = (b1 and 0x7F).toLong()
            if (len == 126L) len = ((input.read() shl 8) or input.read()).toLong()
            else if (len == 127L) {
                len = 0
                repeat(8) { len = (len shl 8) or input.read().toLong() }
            }
            if (len > 4_000_000) return
            val mask = if (b1 and 0x80 != 0) ByteArray(4).also { readFully(input, it) } else null
            val payload = ByteArray(len.toInt())
            readFully(input, payload)
            if (mask != null) for (i in payload.indices) payload[i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
            when (opcode) {
                0x0, 0x1 -> {
                    message.write(payload)
                    if (fin) {
                        val text = message.toString("UTF-8")
                        message.reset()
                        val obj = runCatching { JSONObject(text) }.getOrNull()
                        if (obj != null) main.post { if (!closed) onLine(this, obj) }
                    }
                }
                0x8 -> return
                0x9 -> runCatching { writeFrame(0xA, payload) }
                else -> Unit
            }
        }
    }

    private fun readFully(input: InputStream, b: ByteArray) {
        var off = 0
        while (off < b.size) {
            val n = input.read(b, off, b.size - off)
            if (n < 0) error("closed")
            off += n
        }
    }

    @Synchronized
    private fun writeFrame(opcode: Int, payload: ByteArray) {
        val o = out ?: return
        val header = ByteArrayOutputStream()
        header.write(0x80 or opcode)
        val n = payload.size
        when {
            n < 126 -> header.write(0x80 or n)
            n < 65536 -> { header.write(0x80 or 126); header.write(n shr 8); header.write(n and 0xFF) }
            else -> { header.write(0x80 or 127); for (i in 7 downTo 0) header.write(((n.toLong() shr (8 * i)) and 0xFF).toInt()) }
        }
        val mask = ByteArray(4).also { rng.nextBytes(it) }
        header.write(mask)
        val masked = ByteArray(n) { (payload[it].toInt() xor mask[it % 4].toInt()).toByte() }
        o.write(header.toByteArray())
        o.write(masked)
        o.flush()
    }

    override fun send(o: JSONObject) {
        if (closed) return
        val bytes = o.toString().toByteArray(Charsets.UTF_8)
        runCatching {
            sender.execute {
                try {
                    writeFrame(0x1, bytes)
                } catch (_: Exception) {
                    finish(null)
                }
            }
        }
    }

    private fun finish(error: String?) {
        main.post {
            if (!closed) {
                closed = true
                cleanup()
                onClose(this, error)
            }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { sender.execute { runCatching { writeFrame(0x8, ByteArray(0)) }; cleanup() } }
        main.post { onClose(this, null) }
    }

    private fun cleanup() {
        runCatching { socket?.close() }
        sender.shutdown()
    }
}

/** Host side of an online room: one WebSocket to the relay, one virtual [Conn] per guest. */
class RelayHost(
    url: String,
    private val onCode: (String) -> Unit,
    private val onPeer: (Conn) -> Unit,
    private val onPeerLine: (Conn, JSONObject) -> Unit,
    private val onPeerClosed: (Conn) -> Unit,
    private val onLost: (String) -> Unit,
) {
    private val peers = HashMap<Int, Peer>()
    private var stopped = false
    private val ws: WsConn = WsConn.open(url.trimEnd('/') + "/host", {}, ::onLine) { _, err ->
        val list = peers.values.toList()
        peers.clear()
        list.forEach { it.drop() }
        if (!stopped) onLost(err ?: "Mất kết nối tới máy chủ online")
    }

    private inner class Peer(val id: Int) : Conn {
        override var closed = false
            private set

        override fun send(o: JSONObject) {
            if (!closed) ws.send(JSONObject().put("t", "to").put("id", id).put("m", o))
        }

        override fun close() {
            if (closed) return
            ws.send(JSONObject().put("t", "kick").put("id", id))
            peers.remove(id)
            drop()
        }

        fun drop() {
            if (closed) return
            closed = true
            onPeerClosed(this)
        }
    }

    private fun onLine(c: WsConn, o: JSONObject) {
        when (o.optString("t")) {
            "room" -> onCode(o.getString("code"))
            "peer" -> {
                val p = Peer(o.getInt("id"))
                peers[p.id] = p
                onPeer(p)
            }
            "from" -> peers[o.optInt("id")]?.let { p -> o.optJSONObject("m")?.let { onPeerLine(p, it) } }
            "left" -> peers.remove(o.optInt("id"))?.drop()
        }
    }

    fun stop() {
        stopped = true
        ws.close()
    }
}
