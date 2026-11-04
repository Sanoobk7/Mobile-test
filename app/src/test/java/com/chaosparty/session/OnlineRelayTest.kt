package com.chaosparty.session

import android.os.Looper
import com.chaosparty.engine.Action
import com.chaosparty.engine.Ev
import com.chaosparty.engine.GameView
import com.chaosparty.engine.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

/** Host and guest play a full game through the online relay. Run with -PtestRelay=ws://127.0.0.1:8787 (wrangler dev). */
@RunWith(RobolectricTestRunner::class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35])
class OnlineRelayTest {
    private fun pump(ms: Long, until: () -> Boolean = { false }) {
        val looper = shadowOf(Looper.getMainLooper())
        var waited = 0L
        while (waited < ms && !until()) {
            Thread.sleep(5)
            looper.idleFor(Duration.ofMillis(200))
            waited += 5
        }
    }

    @Test
    fun hostAndGuestPlayThroughRelay() {
        val relay = System.getProperty("testRelay")?.takeIf { it.isNotEmpty() } ?: return
        val host = HostSession("Host", 0, 0, online = true, relayUrl = relay)
        var code: String? = null
        var hostLobby = emptyList<LobbySeat>()
        var hostView: GameView? = null
        host.attach(object : SessionListener {
            override fun onLobby(seats: List<LobbySeat>, me: Int) { hostLobby = seats }
            override fun onRoomCode(c: String) { code = c }
            override fun onStep(events: List<Ev>, view: GameView) { hostView = view }
        })
        var openError: String? = null
        host.open { openError = it }
        pump(5000) { code != null }
        assertEquals(null, openError)
        assertEquals(6, code?.length)

        val guest = ClientSession("Guest", 3, 2)
        var guestLobby = emptyList<LobbySeat>()
        var guestId = -1
        var guestView: GameView? = null
        var guestEvents = 0
        var connected = false
        guest.attach(object : SessionListener {
            override fun onLobby(seats: List<LobbySeat>, me: Int) { guestLobby = seats }
            override fun onStart(you: Int) { guestId = you }
            override fun onStep(events: List<Ev>, view: GameView) { guestView = view; guestEvents += events.size }
        })
        guest.connectOnline(relay, code!!.lowercase(), { connected = true }, { error(it) })
        pump(5000) { hostLobby.size == 2 && guestLobby.size == 2 }
        assertTrue(connected)
        assertEquals(listOf("Host", "Guest"), guestLobby.map { it.name })

        host.addBot(2)
        pump(2000) { guestLobby.size == 3 }
        assertEquals(3, guestLobby.size)

        host.startGame()
        pump(3000) { guestId >= 0 && guestView != null }
        assertEquals(1, guestId)

        // Both humans play like bots would, until someone wins.
        var guard = 0
        while (guard++ < 4000) {
            val hv = hostView ?: break
            if (hv.phase == Phase.GAME_OVER) break
            val gv = guestView
            if (hv.phase == Phase.OFFERS && hv.offer != null) host.act(Action.ResolveOffer(hv.players[0].items.any { it == null }))
            if (gv != null && gv.phase == Phase.OFFERS && gv.offer != null) guest.act(Action.ResolveOffer(gv.players[1].items.any { it == null }))
            if (hv.phase == Phase.PLAYING && hv.turn == 0) host.act(Action.Shoot(hv.players.first { it.alive && it.id != 0 }.id))
            if (gv != null && gv.phase == Phase.PLAYING && gv.turn == 1 && gv.round == hv.round) guest.act(Action.Shoot(1))
            // Let animations-paced bots move: advance the fake clock a lot.
            pump(60)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(8))
        }
        pump(2000) { guestView?.phase == Phase.GAME_OVER }
        assertEquals(Phase.GAME_OVER, hostView?.phase)
        assertEquals(Phase.GAME_OVER, guestView?.phase)
        assertEquals(hostView?.winner, guestView?.winner)
        assertTrue(guestEvents > 10)
        guest.leave()
        host.leave()
        pump(300)
    }
}
