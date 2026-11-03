package com.chaosparty.net

import com.chaosparty.engine.Action
import com.chaosparty.engine.Ev
import com.chaosparty.engine.GameView
import com.chaosparty.engine.Item
import com.chaosparty.engine.Phase
import com.chaosparty.engine.PlayerView
import com.chaosparty.engine.RoundEvent
import com.chaosparty.engine.Shell
import org.json.JSONArray
import org.json.JSONObject

/** JSON encoding for everything sent between phones. */
object Codec {
    const val VERSION = 1

    fun action(a: Action): JSONObject = JSONObject().apply {
        put("t", "act")
        when (a) {
            is Action.Shoot -> { put("a", "shoot"); put("target", a.target) }
            is Action.UseItem -> { put("a", "use"); put("slot", a.slot); put("target", a.target) }
            is Action.ResolveOffer -> { put("a", "offer"); put("keep", a.keep) }
        }
    }

    fun action(o: JSONObject): Action? = when (o.optString("a")) {
        "shoot" -> Action.Shoot(o.getInt("target"))
        "use" -> Action.UseItem(o.getInt("slot"), o.optInt("target", -1))
        "offer" -> Action.ResolveOffer(o.getBoolean("keep"))
        else -> null
    }

    fun event(e: Ev): JSONObject = JSONObject().apply {
        put("type", e.type)
        val d = JSONObject()
        for ((k, v) in e.data) d.put(k, if (v is List<*>) JSONArray(v) else v)
        put("d", d)
    }

    fun event(o: JSONObject): Ev {
        val d = o.getJSONObject("d")
        val map = HashMap<String, Any?>()
        for (k in d.keys()) {
            val v = d.get(k)
            map[k] = if (v is JSONArray) (0 until v.length()).map { v.get(it) } else if (v == JSONObject.NULL) null else v
        }
        return Ev(o.getString("type"), map)
    }

    fun view(v: GameView): JSONObject = JSONObject().apply {
        put("you", v.you); put("round", v.round); put("phase", v.phase.name); put("shown", v.shownEvent.name)
        put("red", v.red); put("black", v.black); put("pink", v.pink); put("total", v.total)
        put("turn", v.turn); put("offer", v.offer?.name); put("known", v.known?.name)
        put("chisel", v.chisel); put("rot", v.rotationLeft); put("winner", v.winner)
        put("players", JSONArray().apply {
            v.players.forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id); put("name", p.name); put("seat", p.seat); put("hp", p.hp); put("armor", p.armor)
                    put("alive", p.alive); put("bot", p.isBot); put("av", p.avatar); put("gun", p.gun)
                    put("hidden", p.itemsHidden); put("adr", p.adrenaline); put("choosing", p.choosing)
                    put("items", JSONArray().apply { p.items.forEach { put(it?.name ?: "") } })
                })
            }
        })
    }

    fun view(o: JSONObject): GameView {
        val ps = o.getJSONArray("players")
        return GameView(
            you = o.getInt("you"), round = o.getInt("round"), phase = Phase.valueOf(o.getString("phase")),
            shownEvent = RoundEvent.valueOf(o.getString("shown")),
            red = o.getInt("red"), black = o.getInt("black"), pink = o.getInt("pink"), total = o.getInt("total"),
            turn = o.getInt("turn"),
            players = (0 until ps.length()).map { i ->
                val p = ps.getJSONObject(i)
                val items = p.getJSONArray("items")
                PlayerView(
                    id = p.getInt("id"), name = p.getString("name"), seat = p.getInt("seat"), hp = p.getInt("hp"),
                    armor = p.getInt("armor"), alive = p.getBoolean("alive"), isBot = p.getBoolean("bot"),
                    avatar = p.getInt("av"), gun = p.getInt("gun"),
                    items = (0 until items.length()).map { j -> items.getString(j).takeIf { it.isNotEmpty() }?.let { Item.valueOf(it) } },
                    itemsHidden = p.getBoolean("hidden"), adrenaline = p.getInt("adr"), choosing = p.getBoolean("choosing"),
                )
            },
            offer = o.optStringOrNull("offer")?.let { Item.valueOf(it) },
            known = o.optStringOrNull("known")?.let { Shell.valueOf(it) },
            chisel = o.getBoolean("chisel"), rotationLeft = o.getInt("rot"), winner = o.getInt("winner"),
        )
    }

    private fun JSONObject.optStringOrNull(k: String): String? =
        if (isNull(k) || !has(k)) null else getString(k).takeIf { it.isNotEmpty() }
}
