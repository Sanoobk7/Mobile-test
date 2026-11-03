package com.sanoob.caro

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException

/** Finds rooms on the same Wi-Fi / hotspot using UDP broadcast. */
object LanDiscovery {
    const val PORT = 5051
    private const val ASK = "CARO_DISCOVER"
    private const val REPLY = "CARO_HOST "

    data class Room(val name: String, val ip: String)

    /** Host side: answers discovery requests until [stop] is called. */
    class Responder(private val roomName: String) {
        @Volatile private var socket: DatagramSocket? = null

        fun start() {
            Thread {
                try {
                    val s = DatagramSocket(null)
                    s.reuseAddress = true
                    s.bind(InetSocketAddress(PORT))
                    socket = s
                    val buf = ByteArray(256)
                    while (!s.isClosed) {
                        val p = DatagramPacket(buf, buf.size)
                        s.receive(p)
                        val msg = String(p.data, 0, p.length, Charsets.UTF_8)
                        if (msg == ASK) {
                            val out = (REPLY + roomName).toByteArray(Charsets.UTF_8)
                            s.send(DatagramPacket(out, out.size, p.address, p.port))
                        }
                    }
                } catch (_: Exception) {
                }
            }.start()
        }

        fun stop() {
            runCatching { socket?.close() }
        }
    }

    /** Guest side: blocking scan, call from a background thread. */
    fun scan(timeoutMs: Int = 1500): List<Room> {
        val rooms = LinkedHashMap<String, Room>()
        DatagramSocket().use { s ->
            s.broadcast = true
            s.soTimeout = 300
            val ask = ASK.toByteArray(Charsets.UTF_8)
            val targets = broadcastAddresses() + InetAddress.getByName("255.255.255.255")
            val deadline = System.currentTimeMillis() + timeoutMs
            var nextSend = 0L
            val buf = ByteArray(256)
            while (System.currentTimeMillis() < deadline) {
                if (System.currentTimeMillis() >= nextSend) {
                    for (t in targets) runCatching { s.send(DatagramPacket(ask, ask.size, t, PORT)) }
                    nextSend = System.currentTimeMillis() + 500
                }
                try {
                    val p = DatagramPacket(buf, buf.size)
                    s.receive(p)
                    val msg = String(p.data, 0, p.length, Charsets.UTF_8)
                    if (msg.startsWith(REPLY)) {
                        val ip = p.address.hostAddress ?: continue
                        rooms[ip] = Room(msg.removePrefix(REPLY), ip)
                    }
                } catch (_: SocketTimeoutException) {
                }
            }
        }
        return rooms.values.toList()
    }

    private fun broadcastAddresses(): List<InetAddress> =
        runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.interfaceAddresses }
                .mapNotNull { it.broadcast }
        }.getOrDefault(emptyList())

    /** Local IPv4 addresses, so the host can tell friends where to connect. */
    fun localIps(): List<String> =
        runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .mapNotNull { it.hostAddress }
        }.getOrDefault(emptyList())
}
