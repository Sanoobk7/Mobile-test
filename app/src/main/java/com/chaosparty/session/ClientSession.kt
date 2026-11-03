package com.chaosparty.session

import com.chaosparty.engine.Action
import com.chaosparty.net.Codec
import com.chaosparty.net.Link
import com.chaosparty.net.connectAsync
import org.json.JSONObject

/** A guest connected to someone else's room. */
class ClientSession(
    private val name: String,
    private val avatar: Int,
    private val gun: Int,
) : Session() {
    override val isHost = false
    override val online = true

    private var link: Link? = null
    private var closed = false
    private var closeReason = "Mất kết nối với chủ phòng"

    fun connect(ip: String, onOk: () -> Unit, onFail: (String) -> Unit) {
        connectAsync(ip, { socket ->
            if (closed) {
                runCatching { socket.close() }
                return@connectAsync
            }
            val l = Link(socket, ::onLine) { if (!closed) { closed = true; dispatch { it.onClosed(closeReason) } } }
            link = l
            l.send(JSONObject().put("t", "hello").put("name", name).put("av", avatar).put("gun", gun).put("v", Codec.VERSION))
            onOk()
        }, onFail)
    }

    private fun onLine(l: Link, o: JSONObject) {
        when (o.optString("t")) {
            "lobby" -> {
                val arr = o.getJSONArray("seats")
                val seats = (0 until arr.length()).map {
                    val s = arr.getJSONObject(it)
                    LobbySeat(s.getString("name"), s.getBoolean("bot"), s.optInt("level"), s.optInt("av"), s.optInt("gun"), s.optBoolean("host"))
                }
                val me = o.getInt("me")
                lastLobby = seats to me
                dispatch { it.onLobby(seats, me) }
            }
            "start" -> {
                myId = o.getInt("you")
                dispatch { it.onStart(myId) }
            }
            "step" -> {
                val evArr = o.getJSONArray("ev")
                val events = (0 until evArr.length()).map { Codec.event(evArr.getJSONObject(it)) }
                val view = Codec.view(o.getJSONObject("view"))
                dispatch { it.onStep(events, view) }
            }
            "emote" -> {
                val p = o.getInt("p")
                val e = o.getInt("e")
                dispatch { it.onEmote(p, e) }
            }
            "tolobby" -> dispatch { it.onBackToLobby() }
            "nope" -> {
                val msg = o.optString("msg")
                dispatch { it.onRejected(msg) }
            }
            "err" -> closeReason = o.optString("msg", closeReason)
        }
    }

    override fun act(a: Action) {
        link?.send(Codec.action(a))
    }

    override fun emote(e: Int) {
        link?.send(JSONObject().put("t", "emote").put("e", e))
    }

    override fun leave() {
        if (closed) return
        closed = true
        link?.close()
    }
}
