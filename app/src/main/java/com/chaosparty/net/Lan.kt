package com.chaosparty.net

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.Executors

const val GAME_PORT = 47650
const val DISCOVERY_PORT = 47651

private val mainHandler = Handler(Looper.getMainLooper())

/** One TCP link carrying JSON lines. Callbacks run on the main thread. */
class Link(private val socket: Socket, private val onLine: (Link, JSONObject) -> Unit, private val onClose: (Link) -> Unit) {
    private val out = PrintWriter(socket.getOutputStream().bufferedWriter(Charsets.UTF_8), false)
    private val sender = Executors.newSingleThreadExecutor()
    @Volatile var closed = false
        private set

    init {
        socket.tcpNoDelay = true
        Thread {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                while (!closed) {
                    val line = reader.readLine() ?: break
                    val obj = runCatching { JSONObject(line) }.getOrNull() ?: continue
                    mainHandler.post { if (!closed) onLine(this, obj) }
                }
            } catch (_: Exception) {
            }
            mainHandler.post { close() }
        }.apply { isDaemon = true }.start()
    }

    fun send(o: JSONObject) {
        if (closed) return
        val s = o.toString()
        runCatching {
            sender.execute {
                try {
                    out.println(s)
                    out.flush()
                    if (out.checkError()) mainHandler.post { close() }
                } catch (_: Exception) {
                    mainHandler.post { close() }
                }
            }
        }
    }

    /** Must be called on the main thread. */
    fun close() {
        if (closed) return
        closed = true
        runCatching { sender.execute { runCatching { socket.close() } } }
        sender.shutdown()
        onClose(this)
    }
}

/** Accepts players on [GAME_PORT] and answers LAN discovery broadcasts. */
class LanServer(private val roomName: () -> String, private val onLink: (Socket) -> Unit) {
    @Volatile private var server: ServerSocket? = null
    @Volatile private var udp: DatagramSocket? = null
    @Volatile private var stopped = false

    fun start(onError: (String) -> Unit) {
        Thread {
            try {
                val ss = ServerSocket()
                ss.reuseAddress = true
                ss.bind(InetSocketAddress(GAME_PORT))
                server = ss
                while (!stopped) {
                    val s = ss.accept()
                    mainHandler.post { if (stopped) runCatching { s.close() } else onLink(s) }
                }
            } catch (e: Exception) {
                if (!stopped) mainHandler.post { onError("Không mở được phòng (${e.message})") }
            }
        }.apply { isDaemon = true }.start()
        Thread {
            try {
                val s = DatagramSocket(null)
                s.reuseAddress = true
                s.broadcast = true
                s.bind(InetSocketAddress(DISCOVERY_PORT))
                udp = s
                val buf = ByteArray(512)
                while (!stopped) {
                    val p = DatagramPacket(buf, buf.size)
                    s.receive(p)
                    if (String(p.data, 0, p.length, Charsets.UTF_8) == Discovery.ASK) {
                        val out = JSONObject().put("name", roomName()).put("v", Codec.VERSION).toString()
                        val bytes = (Discovery.REPLY + out).toByteArray(Charsets.UTF_8)
                        s.send(DatagramPacket(bytes, bytes.size, p.address, p.port))
                    }
                }
            } catch (_: Exception) {
            }
        }.apply { isDaemon = true }.start()
    }

    fun stop() {
        stopped = true
        runCatching { server?.close() }
        runCatching { udp?.close() }
    }
}

object Discovery {
    const val ASK = "CHAOS_PARTY_FIND"
    const val REPLY = "CHAOS_PARTY_ROOM "

    data class Room(val name: String, val ip: String, val players: Int)

    /** Blocking scan; call from a background thread. */
    fun scan(timeoutMs: Long = 1800): List<Room> {
        val rooms = LinkedHashMap<String, Room>()
        DatagramSocket().use { s ->
            s.broadcast = true
            s.soTimeout = 250
            val ask = ASK.toByteArray(Charsets.UTF_8)
            val targets = (broadcastAddresses() + InetAddress.getByName("255.255.255.255")).distinct()
            val deadline = System.currentTimeMillis() + timeoutMs
            var nextSend = 0L
            val buf = ByteArray(512)
            while (System.currentTimeMillis() < deadline) {
                if (System.currentTimeMillis() >= nextSend) {
                    for (t in targets) runCatching { s.send(DatagramPacket(ask, ask.size, t, DISCOVERY_PORT)) }
                    nextSend = System.currentTimeMillis() + 600
                }
                try {
                    val p = DatagramPacket(buf, buf.size)
                    s.receive(p)
                    val msg = String(p.data, 0, p.length, Charsets.UTF_8)
                    if (!msg.startsWith(REPLY)) continue
                    val ip = p.address.hostAddress ?: continue
                    val o = runCatching { JSONObject(msg.removePrefix(REPLY)) }.getOrNull() ?: continue
                    rooms[ip] = Room(o.optString("name", "Phòng"), ip, o.optInt("n", 0))
                } catch (_: SocketTimeoutException) {
                }
            }
        }
        return rooms.values.toList()
    }

    private fun broadcastAddresses(): List<InetAddress> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.interfaceAddresses }
            .mapNotNull { it.broadcast }
    }.getOrDefault(emptyList())

    /** This phone's LAN IPv4 addresses, shown to the host so friends can type them in. */
    fun localIps(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .mapNotNull { it.hostAddress }
            .sortedBy { if (it.startsWith("192.168.")) 0 else 1 }
    }.getOrDefault(emptyList())
}

fun connectAsync(ip: String, onOk: (Socket) -> Unit, onFail: (String) -> Unit) {
    Thread {
        try {
            val s = Socket()
            s.connect(InetSocketAddress(ip, GAME_PORT), 5000)
            mainHandler.post { onOk(s) }
        } catch (e: Exception) {
            mainHandler.post { onFail("Không kết nối được tới $ip") }
        }
    }.apply { isDaemon = true }.start()
}
