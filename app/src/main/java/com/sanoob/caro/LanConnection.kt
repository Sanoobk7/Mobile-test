package com.sanoob.caro

import android.os.Handler
import android.os.Looper
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

/**
 * One TCP connection between two phones. Messages are single text lines:
 *   HELLO <name> | MOVE <row> <col> | RESET | BYE
 * All listener callbacks run on the main thread.
 */
class LanConnection(private val listener: Listener) {
    interface Listener {
        fun onConnected(peerName: String)
        fun onMessage(line: String)
        fun onDisconnected(reason: String)
    }

    companion object {
        const val GAME_PORT = 5050
    }

    private val main = Handler(Looper.getMainLooper())
    private val sender = Executors.newSingleThreadExecutor()
    @Volatile private var server: ServerSocket? = null
    @Volatile private var socket: Socket? = null
    @Volatile private var writer: PrintWriter? = null
    @Volatile private var closed = false

    fun host(myName: String) {
        Thread {
            try {
                val ss = ServerSocket()
                ss.reuseAddress = true
                ss.bind(InetSocketAddress(GAME_PORT))
                server = ss
                val s = ss.accept()
                ss.close()
                server = null
                run(s, myName)
            } catch (e: Exception) {
                if (!closed) post { listener.onDisconnected("Không mở được phòng: ${e.message}") }
            }
        }.start()
    }

    fun join(hostIp: String, myName: String) {
        Thread {
            try {
                val s = Socket()
                s.connect(InetSocketAddress(hostIp, GAME_PORT), 5000)
                run(s, myName)
            } catch (e: Exception) {
                if (!closed) post { listener.onDisconnected("Không kết nối được $hostIp") }
            }
        }.start()
    }

    private fun run(s: Socket, myName: String) {
        socket = s
        s.tcpNoDelay = true
        val w = PrintWriter(s.getOutputStream().bufferedWriter(Charsets.UTF_8), true)
        writer = w
        send("HELLO ${myName.replace('\n', ' ')}")
        val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
        var greeted = false
        try {
            while (!closed) {
                val line = reader.readLine() ?: break
                if (line == "BYE") break
                if (!greeted && line.startsWith("HELLO")) {
                    greeted = true
                    val name = line.removePrefix("HELLO").trim().ifEmpty { "Bạn" }
                    post { listener.onConnected(name) }
                } else {
                    post { listener.onMessage(line) }
                }
            }
        } catch (_: Exception) {
        }
        val closedByUs = closed
        close()
        if (!closedByUs) main.post { listener.onDisconnected("Bạn kia đã thoát") }
    }

    fun send(line: String) {
        val w = writer ?: return
        if (closed) return
        runCatching { sender.execute { runCatching { w.println(line) } } }
    }

    fun close() {
        if (closed) return
        closed = true
        val w = writer
        sender.execute {
            runCatching { w?.println("BYE") }
            runCatching { socket?.close() }
            runCatching { server?.close() }
        }
        sender.shutdown()
    }

    private fun post(block: () -> Unit) = main.post { if (!closed) block() }
}
