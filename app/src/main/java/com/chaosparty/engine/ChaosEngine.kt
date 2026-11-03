package com.chaosparty.engine

import kotlin.random.Random

class PlayerInit(val name: String, val isBot: Boolean, val botLevel: Int = 1, val avatar: Int = 0, val gun: Int = 0)

class Player(val id: Int, val name: String, var isBot: Boolean, var botLevel: Int, val avatar: Int, val gun: Int) {
    var seat = id
    var hp = MAX_HP
    var armor = 0
    var alive = true
    val items = arrayOfNulls<Item>(SLOTS)
    val pending = ArrayDeque<Item>()
    /** Rounds left before adrenaline kills this player (0 = not active). */
    var adrenaline = 0
    var votes = 0

    fun emptySlot(): Int = items.indexOfFirst { it == null }
}

class LiveBomb(var holder: Int, var slot: Int, var turnsLeft: Int)

private enum class Glue { NONE, PENDING, ARMED }

/**
 * Full rules of Chaos Party. Runs on the host only; clients get filtered [GameView]s.
 * Every public mutator returns the [Step]s produced (events + per-player views).
 */
class ChaosEngine(inits: List<PlayerInit>, private val rng: Random = Random.Default) {
    val players = inits.mapIndexed { i, p -> Player(i, p.name, p.isBot, p.botLevel, p.avatar, p.gun) }
    var phase = Phase.OFFERS
        private set
    var round = 0
        private set
    var event = RoundEvent.NORMAL
        private set
    val magazine = ArrayDeque<Shell>()
    var turn = -1
        private set
    var chisel = false
        private set
    var rotationLeft = 0
        private set
    var winner = -1
        private set
    val bombs = mutableListOf<LiveBomb>()
    /** Who has peeked at the current shell with the magnifier. */
    private val known = HashSet<Int>()
    private var glue = Glue.NONE
    private var roundStarter = -1
    private var revealed = false

    private val events = mutableListOf<Ev>()
    private val steps = mutableListOf<Step>()

    init {
        require(players.size in 2..MAX_PLAYERS)
    }

    // ---------------------------------------------------------------- public API

    fun start(): List<Step> {
        startRound()
        return drain()
    }

    fun alivePlayers() = players.filter { it.alive }
    fun player(id: Int) = players[id]
    fun counts(s: Shell) = magazine.count { it == s }
    fun knows(p: Int): Shell? = if (p in known) magazine.firstOrNull() else null

    /** Validates and applies an action. Returns null when the action is not allowed right now. */
    fun apply(playerId: Int, action: Action): List<Step>? {
        if (phase == Phase.GAME_OVER) return null
        val p = players.getOrNull(playerId) ?: return null
        if (!p.alive) return null
        val ok = when (action) {
            is Action.ResolveOffer -> resolveOffer(p, action.keep)
            is Action.Shoot -> phase == Phase.PLAYING && turn == p.id && shoot(p, action.target)
            is Action.UseItem -> phase == Phase.PLAYING && turn == p.id && useItem(p, action.slot, action.target)
        }
        if (!ok) {
            events.clear()
            return null
        }
        return drain()
    }

    /** Why an item can't be used right now, or null if it can. Shared by the UI and the bots. */
    fun itemBlocker(p: Player, slot: Int, target: Int = -1): String? {
        val item = p.items.getOrNull(slot) ?: return "Ô trống"
        if (magazine.isEmpty()) return "Súng đang trống"
        return when (item) {
            Item.SYRINGE -> if (p.hp >= MAX_HP) "Máu đã đầy" else null
            Item.CHISEL -> if (chisel) "Đã đục rồi" else null
            Item.ARMOR -> if (p.armor >= MAX_ARMOR) "Giáp đã tối đa" else null
            Item.MAGNIFIER -> when {
                event == RoundEvent.COLORBLIND -> "Mù màu: kính lúp chỉ thấy cầu vồng"
                p.id in known -> "Bạn đã soi viên này"
                else -> null
            }
            Item.ADRENALINE -> if (p.adrenaline > 0) "Adrenaline đang chạy" else null
            Item.GLUE -> when {
                glue != Glue.NONE -> "Không dùng được lúc này"
                magazine.size < 2 -> "Cần ít nhất 2 viên trong súng"
                else -> null
            }
            Item.UNO -> if (alivePlayers().size < 3) "Cần ít nhất 3 người còn sống" else null
            Item.BOMB, Item.LIVE_BOMB -> {
                if (target == -1) {
                    if (players.none { it.alive && it.id != p.id && it.emptySlot() >= 0 }) "Không ai còn ô trống" else null
                } else {
                    val t = players.getOrNull(target)
                    when {
                        t == null || !t.alive -> "Người này không còn"
                        t.id == p.id -> "Không thể đặt bom vào chính mình"
                        t.emptySlot() < 0 -> "${t.name} không còn ô trống"
                        else -> null
                    }
                }
            }
        }
    }

    fun viewFor(id: Int): GameView {
        val counts = IntArray(3)
        magazine.forEach { counts[it.ordinal]++ }
        val blind = event == RoundEvent.COLORBLIND
        val me = players.getOrNull(id)
        return GameView(
            you = id,
            round = round,
            phase = phase,
            shownEvent = if (event == RoundEvent.WEREWOLF && !revealed) RoundEvent.NORMAL else event,
            red = if (blind) -1 else counts[Shell.RED.ordinal],
            black = if (blind) -1 else counts[Shell.BLACK.ordinal],
            pink = if (blind) -1 else counts[Shell.PINK.ordinal],
            total = magazine.size,
            turn = turn,
            players = players.map { p ->
                val mine = p.id == id
                PlayerView(
                    id = p.id, name = p.name, seat = p.seat, hp = p.hp, armor = p.armor, alive = p.alive,
                    isBot = p.isBot, avatar = p.avatar, gun = p.gun,
                    items = if (mine) p.items.toList() else p.items.map { if (it == null) null else Item.SYRINGE },
                    itemsHidden = !mine,
                    adrenaline = p.adrenaline,
                    choosing = p.pending.isNotEmpty(),
                )
            },
            offer = me?.pending?.firstOrNull(),
            known = knows(id),
            chisel = chisel,
            rotationLeft = rotationLeft,
            winner = winner,
        )
    }

    // ---------------------------------------------------------------- rounds

    private fun startRound() {
        round++
        roundStarter = if (roundStarter < 0) alivePlayers().random(rng).id else nextAlive(roundStarter)
        val slot = rng.nextInt(Wheel.SLOTS.size)
        event = Wheel.SLOTS[slot]
        revealed = false
        // Werewolf pretends to be one of the normal slots.
        val shownSlot = if (event == RoundEvent.WEREWOLF) rng.nextInt(4) else slot

        val total = rng.nextInt(3, 9)
        val pink = rng.nextInt(0, minOf(2, total - 2) + 1)
        val rest = total - pink
        val red = rng.nextInt(1, rest)
        val black = rest - red
        magazine.clear()
        repeat(red) { magazine.add(Shell.RED) }
        repeat(black) { magazine.add(Shell.BLACK) }
        repeat(pink) { magazine.add(Shell.PINK) }
        magazine.shuffle(rng)

        known.clear()
        chisel = false
        glue = Glue.NONE
        players.forEach { it.votes = 0 }
        rotationLeft = if (event == RoundEvent.ROTATION) alivePlayers().size else 0

        emit(Ev.ROUND_START, "round" to round, "slot" to shownSlot, "red" to red, "black" to black, "pink" to pink,
            "blind" to (event == RoundEvent.COLORBLIND))

        // Hand out items from the box.
        phase = Phase.OFFERS
        for (p in alivePlayers()) {
            repeat(ITEMS_PER_ROUND) { p.pending.add(drawItem()) }
        }
        flush()
        for (p in alivePlayers()) autoDiscardIfFull(p)
        maybeBeginPlay()
    }

    private fun bombsInPlay(): Int =
        players.sumOf { p -> p.items.count { it == Item.BOMB || it == Item.LIVE_BOMB } + p.pending.count { it == Item.BOMB } }

    private fun drawItem(): Item {
        val pool = if (bombsInPlay() >= MAX_BOMBS) Item.BOX - Item.BOMB else Item.BOX
        return pool.random(rng)
    }

    private fun resolveOffer(p: Player, keep: Boolean): Boolean {
        if (phase != Phase.OFFERS) return false
        val item = p.pending.firstOrNull() ?: return false
        val slot = p.emptySlot()
        if (keep && slot < 0) return false
        p.pending.removeFirst()
        if (keep) p.items[slot] = item
        emit(Ev.OFFER_DONE, "p" to p.id, "keep" to keep, "item" to item.name, only = p.id)
        emit(Ev.OFFER_DONE, "p" to p.id, "keep" to keep, except = p.id)
        autoDiscardIfFull(p)
        flush()
        maybeBeginPlay()
        return true
    }

    /** With no free slot the only choice is to throw the item away, so do it for the player. */
    private fun autoDiscardIfFull(p: Player) {
        while (p.pending.isNotEmpty() && p.emptySlot() < 0) {
            val item = p.pending.removeFirst()
            emit(Ev.OFFER_DONE, "p" to p.id, "keep" to false, "item" to item.name, "auto" to true, only = p.id)
        }
    }

    private fun maybeBeginPlay() {
        if (phase != Phase.OFFERS || players.any { it.alive && it.pending.isNotEmpty() }) return
        phase = Phase.PLAYING
        turn = if (players[roundStarter].alive) roundStarter else nextAlive(roundStarter)
        emit(Ev.TURN, "p" to turn)
        flush()
    }

    private fun endRound() {
        flush()
        emit(Ev.ROUND_END, "round" to round)
        if (event == RoundEvent.WEREWOLF) {
            revealed = true
            val alive = alivePlayers()
            val max = alive.maxOfOrNull { it.votes } ?: 0
            val votes = players.map { if (it.alive) it.votes else 0 }
            var victim = -1
            if (max > 0) {
                victim = alive.filter { it.votes == max }.random(rng).id
            }
            emit(Ev.WEREWOLF, "votes" to votes, "victim" to victim)
            if (victim >= 0) kill(players[victim], "werewolf")
            flush()
        }
        for (p in alivePlayers()) {
            if (p.adrenaline > 0) {
                p.adrenaline--
                if (p.adrenaline == 0) kill(p, "adrenaline")
            }
        }
        magazine.clear()
        known.clear()
        if (checkGameOver()) return
        flush()
        startRound()
    }

    // ---------------------------------------------------------------- shooting

    private fun shoot(shooter: Player, intended: Int): Boolean {
        val target = players.getOrNull(intended) ?: return false
        if (!target.alive || magazine.isEmpty()) return false
        val shell = magazine.removeFirst()
        known.clear()

        var actual = intended
        var rebound = false
        var madness = false
        if (glue == Glue.ARMED) {
            actual = shooter.id
            rebound = true
            glue = Glue.NONE
        } else if (event == RoundEvent.MADNESS && rng.nextFloat() < 0.4f) {
            val others = alivePlayers().filter { it.id != intended }
            if (others.isNotEmpty()) {
                actual = others.random(rng).id
                madness = true
            }
        }
        if (glue == Glue.PENDING) glue = Glue.ARMED

        val hit = players[actual]
        val mult = if (chisel || shooter.adrenaline > 0) 2 else 1
        chisel = false
        var damage = 0
        var absorbed = 0
        var healed = 0
        val endTurn: Boolean
        when (shell) {
            Shell.RED -> {
                absorbed = minOf(hit.armor, mult)
                hit.armor -= absorbed
                damage = mult - absorbed
                hit.hp -= damage
                endTurn = true
            }
            Shell.BLACK -> endTurn = actual != shooter.id
            Shell.PINK -> {
                healed = if (hit.hp < MAX_HP) 1 else 0
                hit.hp += healed
                endTurn = true
            }
        }
        if (event == RoundEvent.WEREWOLF) hit.votes++
        if (event == RoundEvent.ROTATION) rotationLeft--

        emit(
            Ev.SHOT, "p" to shooter.id, "aim" to intended, "hit" to actual, "shell" to shell.name,
            "dmg" to damage, "armor" to absorbed, "heal" to healed, "mult" to mult,
            "madness" to madness, "rebound" to rebound,
        )
        if (hit.hp <= 0) kill(hit, if (rebound) "rebound" else "shot")
        flush()
        if (checkGameOver()) return true

        val roundOver = magazine.isEmpty() || (event == RoundEvent.ROTATION && rotationLeft <= 0)
        if (endTurn || !shooter.alive) {
            val next = if (event == RoundEvent.ROTATION) {
                if (hit.alive) hit.id else nextAlive(hit.id)
            } else nextAlive(shooter.id)
            passTurn(next, roundOver)
        } else if (roundOver) {
            endRound()
        } else {
            emit(Ev.TURN, "p" to turn, "again" to true)
            flush()
        }
        return true
    }

    /** Ends the current turn: bombs tick, then the turn moves (or the round ends). */
    private fun passTurn(next: Int, roundOver: Boolean) {
        tickBombs()
        if (checkGameOver()) return
        if (roundOver) {
            endRound()
            return
        }
        turn = if (players[next].alive) next else nextAlive(next)
        emit(Ev.TURN, "p" to turn)
        flush()
    }

    private fun tickBombs() {
        val it = bombs.iterator()
        val exploded = mutableListOf<LiveBomb>()
        while (it.hasNext()) {
            val b = it.next()
            b.turnsLeft--
            if (b.turnsLeft <= 0) {
                exploded.add(b)
                it.remove()
            }
        }
        for (b in exploded) {
            val h = players[b.holder]
            h.items[b.slot] = null
            if (!h.alive) continue
            val absorbed = minOf(h.armor, 3)
            h.armor -= absorbed
            val dmg = 3 - absorbed
            h.hp -= dmg
            emit(Ev.BOMB_BOOM, "p" to h.id, "dmg" to dmg, "armor" to absorbed)
            if (h.hp <= 0) kill(h, "bomb")
        }
        if (exploded.isNotEmpty()) flush()
    }

    // ---------------------------------------------------------------- items

    private fun useItem(p: Player, slot: Int, target: Int): Boolean {
        val item = p.items.getOrNull(slot) ?: return false
        if (item.needsTarget && target < 0) return false
        if (itemBlocker(p, slot, target) != null) return false
        p.items[slot] = null

        when (item) {
            Item.SYRINGE -> {
                val ok = rng.nextFloat() >= 0.25f
                val delta = if (ok) 1 else -2
                p.hp = minOf(MAX_HP, p.hp + delta)
                publicItem(p, item)
                emit(Ev.SYRINGE, "p" to p.id, "ok" to ok, "delta" to delta)
                if (p.hp <= 0) {
                    kill(p, "syringe")
                    flush()
                    if (!checkGameOver()) passTurn(nextAlive(p.id), false)
                    return true
                }
            }
            Item.CHISEL -> {
                publicItem(p, item)
                if (magazine.first() == Shell.RED && rng.nextBoolean()) {
                    magazine.removeFirst()
                    known.clear()
                    p.hp -= 2
                    emit(Ev.CHISEL_BOOM, "p" to p.id, "dmg" to 2)
                    if (p.hp <= 0) kill(p, "chisel")
                    flush()
                    if (checkGameOver()) return true
                    passTurn(nextAlive(p.id), magazine.isEmpty())
                    return true
                }
                chisel = true
                emit(Ev.CHISEL_ON, "p" to p.id)
            }
            Item.BOMB, Item.LIVE_BOMB -> {
                val t = players[target]
                val tSlot = t.emptySlot()
                t.items[tSlot] = Item.LIVE_BOMB
                if (item == Item.BOMB) {
                    bombs.add(LiveBomb(t.id, tSlot, rng.nextInt(3, 9)))
                } else {
                    bombs.first { it.holder == p.id && it.slot == slot }.apply { holder = t.id; this.slot = tSlot }
                }
                publicItem(p, item, t.id)
                emit(Ev.BOMB_PLACED, "p" to p.id, "to" to t.id, "pass" to (item == Item.LIVE_BOMB))
            }
            Item.ARMOR -> {
                p.armor++
                publicItem(p, item)
            }
            Item.MAGNIFIER -> {
                known.add(p.id)
                publicItem(p, item)
                emit(Ev.MAGNIFIER, "p" to p.id, "shell" to magazine.first().name, only = p.id)
            }
            Item.UNO -> {
                val alive = alivePlayers()
                val seats = alive.map { it.seat }
                var shuffled = seats.shuffled(rng)
                if (shuffled == seats) shuffled = seats.drop(1) + seats.first()
                alive.forEachIndexed { i, pl -> pl.seat = shuffled[i] }
                publicItem(p, item)
                emit(Ev.UNO, "p" to p.id, "seats" to players.map { it.seat })
            }
            Item.ADRENALINE -> {
                p.hp = MAX_HP
                p.adrenaline = 2
                publicItem(p, item)
                emit(Ev.ADRENALINE, "p" to p.id)
            }
            Item.GLUE -> {
                glue = Glue.PENDING
                // Secret: only the user sees it.
                emit(Ev.GLUE, "p" to p.id, only = p.id)
            }
        }
        flush()
        return true
    }

    private fun publicItem(p: Player, item: Item, target: Int = -1) =
        emit(Ev.ITEM, "p" to p.id, "item" to item.name, "target" to target)

    // ---------------------------------------------------------------- helpers

    private fun kill(p: Player, cause: String) {
        if (!p.alive) return
        p.alive = false
        p.hp = 0
        p.armor = 0
        p.adrenaline = 0
        p.pending.clear()
        p.items.fill(null)
        bombs.removeAll { it.holder == p.id }
        known.remove(p.id)
        emit(Ev.DEATH, "p" to p.id, "cause" to cause)
    }

    private fun checkGameOver(): Boolean {
        val alive = alivePlayers()
        if (alive.size > 1) return false
        phase = Phase.GAME_OVER
        winner = alive.firstOrNull()?.id ?: -1
        turn = -1
        emit(Ev.GAME_OVER, "winner" to winner)
        flush()
        return true
    }

    /** Next living player clockwise (by seat) after the given player. */
    fun nextAlive(from: Int): Int {
        val order = players.sortedBy { it.seat }
        val start = order.indexOfFirst { it.id == from }
        for (i in 1..order.size) {
            val p = order[(start + i) % order.size]
            if (p.alive) return p.id
        }
        return from
    }

    private fun emit(type: String, vararg kv: Pair<String, Any?>, only: Int = -1, except: Int = -1) {
        events.add(Ev(type, mapOf(*kv), only, except))
    }

    private fun flush() {
        if (events.isEmpty()) return
        steps.add(Step(events.toList(), players.associate { it.id to viewFor(it.id) }))
        events.clear()
    }

    private fun drain(): List<Step> {
        flush()
        val out = steps.toList()
        steps.clear()
        return out
    }
}
