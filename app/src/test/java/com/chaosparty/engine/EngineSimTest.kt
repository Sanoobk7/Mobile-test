package com.chaosparty.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class EngineSimTest {
    private fun run(seed: Int, n: Int): ChaosEngine {
        val rng = Random(seed)
        val e = ChaosEngine((0 until n).map { PlayerInit("B$it", true, it % 3) }, rng)
        e.start()
        var guard = 0
        while (e.phase != Phase.GAME_OVER) {
            check(++guard < 20_000) { "game did not finish (seed $seed)" }
            val actor = if (e.phase == Phase.OFFERS) e.alivePlayers().first { it.pending.isNotEmpty() } else e.player(e.turn)
            val a = BotBrain.decide(e, actor.id, rng) ?: error("bot had no move (seed $seed, phase ${e.phase})")
            checkNotNull(e.apply(actor.id, a)) { "illegal bot action $a (seed $seed)" }
            for (p in e.players) {
                assertTrue(p.hp <= MAX_HP)
                assertTrue(p.armor in 0..MAX_ARMOR)
                if (!p.alive) assertTrue(p.items.all { it == null })
            }
            assertTrue(e.bombs.size <= MAX_BOMBS)
            val bombItems = e.players.sumOf { p -> p.items.count { it == Item.BOMB || it == Item.LIVE_BOMB } }
            assertTrue("too many bombs", bombItems <= MAX_BOMBS)
            if (e.phase == Phase.PLAYING) {
                assertTrue(e.magazine.isNotEmpty())
                assertTrue(e.player(e.turn).alive)
            }
        }
        return e
    }

    @Test
    fun thousandsOfBotGamesFinish() {
        var draws = 0
        for (seed in 0 until 3000) {
            val e = run(seed, 2 + seed % 5)
            if (e.winner == -1) draws++ else assertTrue(e.player(e.winner).alive)
        }
        assertTrue("draws $draws", draws < 600)
    }

    @Test
    fun redHurtsBlackOnSelfKeepsTurn() {
        val e = ChaosEngine(listOf(PlayerInit("A", false), PlayerInit("B", false)), Random(1))
        e.start()
        while (e.phase == Phase.OFFERS) {
            val p = e.alivePlayers().first { it.pending.isNotEmpty() }
            e.apply(p.id, Action.ResolveOffer(false))
        }
        val shooter = e.turn
        val other = 1 - shooter
        val shell = e.magazine.first()
        val before = e.player(other).hp
        e.apply(shooter, Action.Shoot(if (shell == Shell.BLACK) shooter else other))
        when (shell) {
            Shell.BLACK -> assertEquals(shooter, e.turn)
            Shell.RED -> assertEquals(before - 1, e.player(other).hp)
            Shell.PINK -> assertEquals(minOf(MAX_HP, before + 1), e.player(other).hp)
        }
    }
}
