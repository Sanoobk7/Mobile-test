package com.chaosparty.engine

const val MAX_HP = 5
const val MAX_ARMOR = 3
const val SLOTS = 4
const val MAX_PLAYERS = 6
const val MAX_BOMBS = 2
const val ITEMS_PER_ROUND = 2

enum class Shell { RED, BLACK, PINK }

enum class Item {
    SYRINGE, CHISEL, BOMB, LIVE_BOMB, ARMOR, MAGNIFIER, UNO, ADRENALINE, GLUE;

    /** Items that need a target player when used. */
    val needsTarget get() = this == BOMB || this == LIVE_BOMB

    companion object {
        /** Items that can come out of the box (a live bomb only exists after being placed). */
        val BOX = listOf(SYRINGE, CHISEL, BOMB, ARMOR, MAGNIFIER, UNO, ADRENALINE, GLUE)
    }
}

enum class RoundEvent { NORMAL, COLORBLIND, MADNESS, ROTATION, WEREWOLF }

/** The wheel has 8 slots: 4 normal, then one of each special event. */
object Wheel {
    val SLOTS = listOf(
        RoundEvent.NORMAL, RoundEvent.NORMAL, RoundEvent.NORMAL, RoundEvent.NORMAL,
        RoundEvent.COLORBLIND, RoundEvent.MADNESS, RoundEvent.ROTATION, RoundEvent.WEREWOLF
    )
}

enum class Phase { OFFERS, PLAYING, GAME_OVER }

sealed class Action {
    data class Shoot(val target: Int) : Action()
    data class UseItem(val slot: Int, val target: Int = -1) : Action()
    data class ResolveOffer(val keep: Boolean) : Action()
}

/**
 * A game event used to drive animations and the log. [only] limits it to one player,
 * [except] hides it from one player (used for public, redacted copies of private events).
 */
class Ev(
    val type: String,
    val data: Map<String, Any?> = emptyMap(),
    val only: Int = -1,
    val except: Int = -1,
) {
    fun visibleTo(p: Int) = (only == -1 || only == p) && except != p
    fun int(k: String, def: Int = -1): Int = (data[k] as? Number)?.toInt() ?: def
    fun bool(k: String): Boolean = data[k] as? Boolean ?: false
    fun str(k: String): String = data[k]?.toString() ?: ""

    @Suppress("UNCHECKED_CAST")
    fun ints(k: String): List<Int> = (data[k] as? List<Any?>)?.map { (it as Number).toInt() } ?: emptyList()

    override fun toString() = "Ev($type $data)"

    companion object {
        const val ROUND_START = "round"
        const val OFFER = "offer"
        const val OFFER_DONE = "offerDone"
        const val TURN = "turn"
        const val SHOT = "shot"
        const val ITEM = "item"
        const val MAGNIFIER = "peek"
        const val SYRINGE = "syringe"
        const val CHISEL_ON = "chiselOn"
        const val CHISEL_BOOM = "chiselBoom"
        const val BOMB_PLACED = "bombPlaced"
        const val BOMB_BOOM = "bombBoom"
        const val UNO = "uno"
        const val ADRENALINE = "adrenaline"
        const val GLUE = "glue"
        const val DEATH = "death"
        const val WEREWOLF = "werewolf"
        const val ROUND_END = "roundEnd"
        const val GAME_OVER = "gameOver"
    }
}

data class PlayerView(
    val id: Int,
    val name: String,
    val seat: Int,
    val hp: Int,
    val armor: Int,
    val alive: Boolean,
    val isBot: Boolean,
    val avatar: Int,
    val gun: Int,
    /** Own items for the viewer; for others only an occupied/empty marker (non-null = occupied). */
    val items: List<Item?>,
    val itemsHidden: Boolean,
    val adrenaline: Int,
    val choosing: Boolean,
)

data class GameView(
    val you: Int,
    val round: Int,
    val phase: Phase,
    /** The event players are told about (werewolf shows as NORMAL until revealed). */
    val shownEvent: RoundEvent,
    val red: Int,
    val black: Int,
    val pink: Int,
    val total: Int,
    val turn: Int,
    val players: List<PlayerView>,
    val offer: Item?,
    val known: Shell?,
    val chisel: Boolean,
    val rotationLeft: Int,
    val winner: Int,
)

class Step(val events: List<Ev>, val views: Map<Int, GameView>)
