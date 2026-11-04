package com.chaosparty.session

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.chaosparty.engine.Action
import com.chaosparty.engine.BotBrain
import com.chaosparty.engine.ChaosEngine
import com.chaosparty.engine.MAX_PLAYERS
import com.chaosparty.engine.Phase
import com.chaosparty.engine.PlayerInit
import com.chaosparty.engine.Step
import com.chaosparty.net.Codec
import com.chaosparty.net.Conn
import com.chaosparty.net.LanServer
import com.chaosparty.net.Link
import com.chaosparty.net.RelayHost
import org.json.JSONArray
import org.json.JSONObject
import java.net.Socket

val BOT_NAMES = listOf("Bé Tí", "Lão Hạc", "Su Su", "Mèo Mập", "Chú Tư", "Bông", "Tèo", "Xoài", "Bánh Bao", "Ốc")

/**
 * Runs the game. With [online] it opens a room: on the LAN, or through the online relay when
 * [relayUrl] is set (friends then join with a room code). Otherwise it's single player with bots.
 * Everything runs on the main thread.
 */
class HostSession(
    private val myName: String,
    private val myAvatar: Int,
    private val myGun: Int,
    override val online: Boolean,
    private val relayUrl: String? = null,
) : Session() {
    override val isHost = true

    private class Seat(
        var name: String, val bot: Boolean, val level: Int, val avatar: Int, val gun: Int,
        val link: Conn?, val host: Boolean = false,
    ) {
        var gone = false
    }

    private val handler = Handler(Looper.getMainLooper())
    private val seats = mutableListOf(Seat(myName, false, 1, myAvatar, myGun, null, host = true))
    private val waiting = mutableSetOf<Conn>()
    private var server: LanServer? = null
    private var relay: RelayHost? = null
    /** Room code for online rooms, once the relay has given one. */
    var roomCode: String? = null
        private set
    private var engine: ChaosEngine? = null
    /** Per engine player id, the seat that controls it. */
    private var gameSeats: List<Seat> = emptyList()
    private var busyUntil = 0L
    private var closed = false
    private var turnToken = 0

    fun open(onError: (String) -> Unit) {
        if (!online) return
        val url = relayUrl
        if (url != null) {
            relay = RelayHost(url,
                onCode = { code -> roomCode = code; dispatch { it.onRoomCode(code) } },
                onPeer = { peer -> waiting.add(peer) },
                onPeerLine = ::onLine,
                onPeerClosed = ::onLinkClosed,
                onLost = { msg ->
                    if (roomCode == null) onError(msg) else dispatch { it.onRejected(msg) }
                })
            return
        }
        server = LanServer({ myName }) { socket -> accept(socket) }.also { it.start(onError) }
    }

    val started get() = engine != null
    val seatCount get() = seats.size

    // ---------------------------------------------------------------- lobby

    fun addBot(level: Int) {
        if (seats.size >= MAX_PLAYERS || started) return
        val used = seats.map { it.name }.toSet()
        val name = BOT_NAMES.shuffled().firstOrNull { it !in used } ?: "Bot ${seats.size}"
        seats.add(Seat(name, true, level, (0..9).random(), (0..5).random(), null))
        broadcastLobby()
    }

    fun removeSeat(index: Int) {
        if (index <= 0 || index >= seats.size || started) return
        val s = seats.removeAt(index)
        s.link?.let {
            it.send(JSONObject().put("t", "err").put("msg", "Bạn đã bị mời ra khỏi phòng"))
            handler.postDelayed({ it.close() }, 300)
        }
        broadcastLobby()
    }

    private fun accept(socket: Socket) {
        val link = Link(socket, ::onLine, ::onLinkClosed)
        waiting.add(link)
    }

    private fun onLine(link: Conn, o: JSONObject) {
        when (o.optString("t")) {
            "hello" -> {
                waiting.remove(link)
                val error = when {
                    o.optInt("v") != Codec.VERSION -> "Phiên bản game khác nhau, hãy cập nhật"
                    started -> "Phòng đã bắt đầu chơi"
                    seats.size >= MAX_PLAYERS -> "Phòng đã đủ 6 người"
                    else -> null
                }
                if (error != null) {
                    link.send(JSONObject().put("t", "err").put("msg", error))
                    handler.postDelayed({ link.close() }, 300)
                    return
                }
                var name = o.optString("name").trim().take(16).ifEmpty { "Khách" }
                if (seats.any { it.name == name }) name = "$name ${seats.size + 1}"
                seats.add(Seat(name, false, 1, o.optInt("av"), o.optInt("gun"), link))
                broadcastLobby()
            }
            "act" -> {
                val id = gameSeats.indexOfFirst { it.link === link }
                val a = Codec.action(o) ?: return
                if (id >= 0) act(id, a)
            }
            "emote" -> {
                val id = gameSeats.indexOfFirst { it.link === link }
                if (id >= 0) emoteFrom(id, o.optInt("e"))
            }
        }
    }

    private fun onLinkClosed(link: Conn) {
        waiting.remove(link)
        if (closed) return
        val seat = seats.firstOrNull { it.link === link } ?: return
        val e = engine
        if (e == null) {
            seats.remove(seat)
            broadcastLobby()
        } else {
            seat.gone = true
            seats.remove(seat)
            val id = gameSeats.indexOf(seat)
            if (id >= 0 && e.player(id).alive) {
                e.player(id).isBot = true
                e.player(id).botLevel = 1
                schedulePump(300)
            }
        }
    }

    fun lobbySeats(): List<LobbySeat> = seats.map { LobbySeat(it.name, it.bot, it.level, it.avatar, it.gun, it.host) }

    private fun broadcastLobby() {
        val list = lobbySeats()
        dispatch { it.onLobby(list, 0) }
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("name", it.name).put("bot", it.bot).put("level", it.level)
                .put("av", it.avatar).put("gun", it.gun).put("host", it.host))
        }
        seats.forEachIndexed { i, s -> s.link?.send(JSONObject().put("t", "lobby").put("seats", arr).put("me", i)) }
    }

    // ---------------------------------------------------------------- game

    fun startGame() {
        if (seats.size < 2) return
        gameSeats = seats.toList()
        val e = ChaosEngine(gameSeats.map { PlayerInit(it.name, it.bot, it.level, it.avatar, it.gun) })
        engine = e
        busyUntil = 0
        myId = 0
        dispatch { it.onStart(0) }
        gameSeats.forEachIndexed { i, s -> s.link?.send(JSONObject().put("t", "start").put("you", i)) }
        publish(e.start())
    }

    fun backToLobby() {
        engine = null
        turnToken++
        handler.removeCallbacksAndMessages(null)
        dispatch { it.onBackToLobby() }
        seats.forEach { it.link?.send(JSONObject().put("t", "tolobby")) }
        broadcastLobby()
    }

    override fun act(a: Action) = act(myId, a)

    private fun act(id: Int, a: Action) {
        val e = engine ?: return
        val reason = if (a is Action.UseItem) e.itemBlocker(e.player(id), a.slot, a.target) else null
        val steps = e.apply(id, a)
        if (steps == null) {
            val msg = reason ?: "Chưa thể làm vậy lúc này"
            if (id == myId) dispatch { it.onRejected(msg) }
            else gameSeats.getOrNull(id)?.link?.send(JSONObject().put("t", "nope").put("msg", msg))
            return
        }
        publish(steps)
    }

    override fun emote(e: Int) = emoteFrom(myId, e)

    private fun emoteFrom(id: Int, emote: Int) {
        if (emote !in 0..7) return
        dispatch { it.onEmote(id, emote) }
        val msg = JSONObject().put("t", "emote").put("p", id).put("e", emote)
        gameSeats.forEach { it.link?.send(msg) }
    }

    private fun publish(steps: List<Step>) {
        if (steps.isEmpty()) return
        val now = SystemClock.uptimeMillis()
        var dur = 0L
        for (step in steps) {
            gameSeats.forEachIndexed { id, seat ->
                val evs = step.events.filter { it.visibleTo(id) }
                val view = step.views.getValue(id)
                if (seat.host) {
                    dispatch { it.onStep(evs, view) }
                } else if (seat.link != null && !seat.gone) {
                    seat.link.send(JSONObject().put("t", "step")
                        .put("ev", JSONArray().apply { evs.forEach { put(Codec.event(it)) } })
                        .put("view", Codec.view(view)))
                }
            }
            dur += Timing.of(step.events)
        }
        busyUntil = maxOf(busyUntil, now) + dur
        schedulePump(busyUntil - now + 600)
    }

    private fun schedulePump(delay: Long) {
        val token = ++turnToken
        handler.postDelayed({ if (token == turnToken) pump() }, delay.coerceAtLeast(0))
    }

    /** Lets bots (and timed-out humans) move. */
    private fun pump() {
        val e = engine ?: return
        if (closed || e.phase == Phase.GAME_OVER) return
        if (e.phase == Phase.OFFERS) {
            val bot = e.alivePlayers().firstOrNull { it.isBot && it.pending.isNotEmpty() }
            if (bot != null) {
                BotBrain.decide(e, bot.id)?.let { act(bot.id, it) } ?: schedulePump(200)
                return
            }
            // Humans get 30 s to choose (online only), then the choice is made for them.
            if (online) armTimeout(30_000) {
                e.alivePlayers().firstOrNull { it.pending.isNotEmpty() }?.let { p ->
                    BotBrain.decide(e, p.id)?.let { act(p.id, it) }
                }
            }
            return
        }
        val p = e.player(e.turn)
        if (p.isBot) {
            val a = BotBrain.decide(e, p.id)
            if (a != null) act(p.id, a)
            return
        }
        if (online) armTimeout(45_000) {
            if (e.turn == p.id) BotBrain.decide(e, p.id)?.let { act(p.id, it) }
        }
    }

    private fun armTimeout(ms: Long, block: () -> Unit) {
        val token = ++turnToken
        handler.postDelayed({ if (token == turnToken && !closed) block() }, ms)
    }

    override fun leave() {
        if (closed) return
        closed = true
        handler.removeCallbacksAndMessages(null)
        server?.stop()
        relay?.stop()
        val bye = JSONObject().put("t", "err").put("msg", "Chủ phòng đã đóng phòng")
        (seats.mapNotNull { it.link } + waiting).forEach { l ->
            l.send(bye)
            handler.postDelayed({ l.close() }, 200)
        }
        Handler(Looper.getMainLooper()).postDelayed({ (seats.mapNotNull { it.link } + waiting).forEach { it.close() } }, 400)
    }
}
