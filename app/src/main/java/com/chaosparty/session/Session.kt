package com.chaosparty.session

import com.chaosparty.engine.Action
import com.chaosparty.engine.Ev
import com.chaosparty.engine.GameView

data class LobbySeat(
    val name: String,
    val bot: Boolean,
    val level: Int,
    val avatar: Int,
    val gun: Int,
    val host: Boolean,
)

interface SessionListener {
    fun onLobby(seats: List<LobbySeat>, me: Int) {}
    fun onStart(you: Int) {}
    fun onStep(events: List<Ev>, view: GameView) {}
    fun onEmote(player: Int, emote: Int) {}
    fun onBackToLobby() {}
    fun onRejected(msg: String) {}
    fun onClosed(reason: String) {}
}

/** A running game, local or over LAN. Lives across activities in [Sessions.current]. */
abstract class Session {
    abstract val isHost: Boolean
    abstract val online: Boolean
    var myId = 0
        protected set
    /** Latest lobby snapshot, so a screen that attaches later can show it. */
    var lastLobby: Pair<List<LobbySeat>, Int>? = null
        protected set

    private var listener: SessionListener? = null
    private val queue = ArrayList<(SessionListener) -> Unit>()

    fun attach(l: SessionListener) {
        listener = l
        val pending = queue.toList()
        queue.clear()
        pending.forEach { it(l) }
    }

    fun detach(l: SessionListener) {
        if (listener === l) listener = null
    }

    protected fun dispatch(f: (SessionListener) -> Unit) {
        val l = listener
        if (l != null) f(l) else queue.add(f)
    }

    abstract fun act(a: Action)
    abstract fun emote(e: Int)
    abstract fun leave()
}

object Sessions {
    var current: Session? = null
        set(value) {
            if (field !== value) field?.leave()
            field = value
        }
}

/** How long the UI spends animating each event; the host uses it to pace bots. */
object Timing {
    fun of(e: Ev): Long = when (e.type) {
        Ev.ROUND_START -> 4600
        Ev.SHOT -> if (e.bool("rebound") || e.bool("madness")) 2100 else 1500
        Ev.TURN -> 250
        Ev.ITEM -> 900
        Ev.SYRINGE, Ev.CHISEL_ON, Ev.ADRENALINE, Ev.MAGNIFIER, Ev.GLUE -> 700
        Ev.CHISEL_BOOM, Ev.BOMB_BOOM -> 1400
        Ev.BOMB_PLACED -> 700
        Ev.UNO -> 1300
        Ev.DEATH -> 1100
        Ev.WEREWOLF -> 3200
        Ev.ROUND_END -> 500
        Ev.GAME_OVER -> 800
        else -> 0
    }

    fun of(events: List<Ev>): Long = events.sumOf { of(it) }
}
