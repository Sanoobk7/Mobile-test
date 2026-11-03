package com.chaosparty.engine

import kotlin.random.Random

/**
 * Bot decisions. Bots only use information a human in their seat would have:
 * the public shell counts, their own items and their own magnifier peeks.
 * Levels: 0 = easy, 1 = normal, 2 = hard.
 */
object BotBrain {
    fun decide(e: ChaosEngine, id: Int, rng: Random = Random.Default): Action? {
        val me = e.player(id)
        if (!me.alive) return null
        if (e.phase == Phase.OFFERS) {
            if (me.pending.isEmpty()) return null
            val keep = me.emptySlot() >= 0 && !(me.botLevel == 0 && rng.nextFloat() < 0.15f)
            return Action.ResolveOffer(keep)
        }
        if (e.phase != Phase.PLAYING || e.turn != id || e.magazine.isEmpty()) return null

        val level = me.botLevel
        val blind = e.event == RoundEvent.COLORBLIND
        val total = e.magazine.size.toFloat()
        val known = e.knows(id)
        val pRed: Float
        val pPink: Float
        val pBlack: Float
        if (known != null) {
            pRed = if (known == Shell.RED) 1f else 0f
            pPink = if (known == Shell.PINK) 1f else 0f
            pBlack = if (known == Shell.BLACK) 1f else 0f
        } else if (blind) {
            pRed = 0.45f; pPink = 0.1f; pBlack = 0.45f
        } else {
            pRed = e.counts(Shell.RED) / total
            pPink = e.counts(Shell.PINK) / total
            pBlack = e.counts(Shell.BLACK) / total
        }
        val enemies = e.alivePlayers().filter { it.id != id }

        // Easy bots sometimes just do something random.
        if (level == 0 && rng.nextFloat() < 0.3f) {
            return Action.Shoot(if (rng.nextBoolean()) id else enemies.random(rng).id)
        }

        itemChoice(e, me, level, pRed, known, enemies, rng)?.let { return it }

        val mult = if (e.chisel || me.adrenaline > 0) 2 else 1
        val heal = if (me.hp < MAX_HP) 1f else 0f
        val selfValue = -pRed * mult * (if (me.armor >= mult) 0.3f else 1f) + pPink * heal + pBlack * 0.6f
        val enemyValue = pRed * mult * 0.9f - pPink * 0.8f + pBlack * 0f
        val target = if (selfValue > enemyValue) id else pickEnemy(enemies, level, rng)
        return Action.Shoot(target)
    }

    private fun pickEnemy(enemies: List<Player>, level: Int, rng: Random): Int =
        if (level >= 2) enemies.minBy { it.hp + it.armor }.id
        else if (level == 1 && rng.nextFloat() < 0.6f) enemies.minBy { it.hp + it.armor }.id
        else enemies.random(rng).id

    private fun itemChoice(
        e: ChaosEngine, me: Player, level: Int, pRed: Float, known: Shell?, enemies: List<Player>, rng: Random,
    ): Action? {
        fun slotOf(item: Item) = me.items.indexOfFirst { it == item }
        fun usable(item: Item, target: Int = -1): Int {
            val s = slotOf(item)
            return if (s >= 0 && e.itemBlocker(me, s, target) == null) s else -1
        }
        val withRoom = enemies.filter { it.emptySlot() >= 0 }
        val bombTarget = (if (level >= 1) withRoom.minByOrNull { it.hp + it.armor } else withRoom.randomOrNull(rng))?.id ?: -1

        // Get rid of a live bomb first.
        if (bombTarget >= 0) usable(Item.LIVE_BOMB, bombTarget).let { if (it >= 0) return Action.UseItem(it, bombTarget) }
        usable(Item.ARMOR).let { if (it >= 0) return Action.UseItem(it) }
        if (me.hp <= if (level >= 2) 2 else 1) usable(Item.ADRENALINE).let { if (it >= 0) return Action.UseItem(it) }
        if (me.hp in 3 until MAX_HP || (level == 0 && me.hp < MAX_HP)) usable(Item.SYRINGE).let { if (it >= 0) return Action.UseItem(it) }
        if (known == null && pRed > 0f && pRed < 1f) usable(Item.MAGNIFIER).let { if (it >= 0) return Action.UseItem(it) }
        if (bombTarget >= 0) usable(Item.BOMB, bombTarget).let { if (it >= 0) return Action.UseItem(it, bombTarget) }
        if (me.hp >= 3 && (known == Shell.RED || (level >= 2 && pRed >= 0.8f))) {
            usable(Item.CHISEL).let { if (it >= 0) return Action.UseItem(it) }
        }
        if (e.magazine.size >= 2 && rng.nextFloat() < 0.35f) usable(Item.GLUE).let { if (it >= 0) return Action.UseItem(it) }
        if (rng.nextFloat() < 0.15f) usable(Item.UNO).let { if (it >= 0) return Action.UseItem(it) }
        return null
    }
}
