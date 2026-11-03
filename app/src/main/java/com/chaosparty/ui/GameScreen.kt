package com.chaosparty.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.chaosparty.engine.Action
import com.chaosparty.engine.Ev
import com.chaosparty.engine.GameView
import com.chaosparty.engine.Item
import com.chaosparty.engine.MAX_ARMOR
import com.chaosparty.engine.MAX_HP
import com.chaosparty.engine.Phase
import com.chaosparty.engine.PlayerView
import com.chaosparty.engine.RoundEvent
import com.chaosparty.engine.SLOTS
import com.chaosparty.engine.Shell
import com.chaosparty.engine.Wheel
import com.chaosparty.session.Timing
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

interface GameScreenHost {
    fun sendAction(a: Action)
    fun sendEmote(e: Int)
    fun openMenu()
    fun gameOverButtons(): List<String>
    fun onGameOverButton(index: Int)
    fun onGameOver(won: Boolean)
}

/**
 * The whole in-game screen, drawn on a Canvas: hex table, players, gun, HUD and animations.
 * It receives steps (events + resulting view) and plays them in order.
 */
@SuppressLint("ViewConstructor")
class GameScreen(ctx: Context, private val host: GameScreenHost, private val online: Boolean) : View(ctx) {

    companion object {
        val EMOTES = arrayOf("😂", "😱", "😡", "😎", "🤡", "💀", "👍", "🙏")
        private val LAYOUTS = arrayOf(
            intArrayOf(), intArrayOf(0), intArrayOf(0, 3), intArrayOf(0, 2, 4), intArrayOf(0, 1, 3, 4),
            intArrayOf(0, 1, 2, 4, 5), intArrayOf(0, 1, 2, 3, 4, 5),
        )
        private const val TURN_LIMIT_MS = 45_000L
    }

    // ------------------------------------------------------------------ state

    private var view: GameView? = null
    private val steps = ArrayDeque<Pair<List<Ev>, GameView>>()
    private var stepEvents: List<Ev> = emptyList()
    private var stepView: GameView? = null
    private var stepIndex = 0
    private var cur: Ev? = null
    private var curStart = 0L
    private var curDur = 0L
    private var fired = false

    private var selectedTarget = -1
    private var selectedSlot = -1
    private var pickingBombTarget = false
    private var waitingReply = 0L
    private var emotePicker = false
    private var turnShownAt = 0L
    private val emotes = HashMap<Int, Pair<Int, Long>>()
    private val hurtUntil = HashMap<Int, Long>()
    private val happyUntil = HashMap<Int, Long>()
    private val votes = HashMap<Int, Int>()
    private var votesUntil = 0L
    private var glueOnGun = false
    private var gunAngle = -90f
    private var gunTarget = -90f
    private var recoilUntil = 0L
    private val log = ArrayDeque<Pair<String, Int>>()
    private var toast: String? = null
    private var toastUntil = 0L
    private var gameOverShown = false
    private var gameOverAt = 0L

    // effects
    private class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val maxLife: Float, val color: Int, val size: Float, val kind: Int)
    private class FloatText(val x: Float, val y: Float, val text: String, val color: Int, val start: Long, val dur: Long, val size: Float)
    private val particles = ArrayList<Particle>()
    private val texts = ArrayList<FloatText>()
    private var shakeUntil = 0L
    private var shakeAmp = 0f
    private var flashColor = 0
    private var flashStart = 0L
    private var banner: Triple<String, String, Int>? = null
    private var bannerStart = 0L
    private var bannerDur = 0L
    private val rng = Random(SystemClock.uptimeMillis())
    private var lastFrame = 0L

    // ------------------------------------------------------------------ layout

    private var w = 0f
    private var h = 0f
    private var u = 0f
    private var cx = 0f
    private var cy = 0f
    private var rx = 0f
    private var ry = 0f
    private val vx = FloatArray(6)
    private val vy = FloatArray(6)
    private var tableBmp: Bitmap? = null
    private val slotRects = Array(SLOTS) { RectF() }
    private val actionBtn = RectF()
    private val menuBtn = RectF()
    private val emoteBtn = RectF()
    private val emoteRects = Array(EMOTES.size) { RectF() }
    private val offerKeep = RectF()
    private val offerDrop = RectF()
    private val overBtns = ArrayList<RectF>()
    private var bottomTop = 0f

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val r1 = RectF()
    private val path = Path()

    override fun onSizeChanged(nw: Int, nh: Int, ow: Int, oh: Int) {
        w = nw.toFloat(); h = nh.toFloat(); u = w / 100f
        cx = w / 2f
        bottomTop = h - min(h * 0.29f, u * 62f)
        val tableSpace = bottomTop - u * 22f
        cy = u * 20f + tableSpace * 0.5f
        rx = min(u * 37f, tableSpace * 0.62f)
        ry = min(rx * 0.95f, tableSpace * 0.4f)
        for (k in 0 until 6) {
            val a = Math.toRadians(90.0 + 60.0 * k)
            vx[k] = cx + (cos(a) * rx).toFloat()
            vy[k] = cy + (sin(a) * ry).toFloat()
        }
        menuBtn.set(w - u * 13f, u * 2.5f, w - u * 3f, u * 12.5f)
        emoteBtn.set(w - u * 25f, u * 2.5f, w - u * 15f, u * 12.5f)
        val slotW = u * 19f
        val gap = u * 3f
        val sx = (w - (slotW * 4 + gap * 3)) / 2f
        val sy = bottomTop + u * 19f
        for (i in 0 until SLOTS) slotRects[i].set(sx + i * (slotW + gap), sy, sx + i * (slotW + gap) + slotW, sy + slotW)
        actionBtn.set(u * 8f, sy + slotW + u * 3f, w - u * 8f, min(h - u * 2f, sy + slotW + u * 15f))
        val ew = u * 11f
        val ex = (w - ew * 4 - u * 2f * 3) / 2f
        for (i in EMOTES.indices) {
            val col = i % 4
            val row = i / 4
            emoteRects[i].set(ex + col * (ew + u * 2f), u * 15f + row * (ew + u * 2f), ex + col * (ew + u * 2f) + ew, u * 15f + row * (ew + u * 2f) + ew)
        }
        buildTable()
    }

    private fun buildTable() {
        if (w <= 0 || h <= 0) return
        val skin = Skins.table(Prefs.table)
        val bmp = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        // background vignette
        p.shader = RadialGradient(cx, cy, h * 0.7f, intArrayOf(0xFF2A1650.toInt(), 0xFF0B0616.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null
        // lamp glow
        p.shader = RadialGradient(cx, cy - ry * 0.2f, rx * 1.6f, intArrayOf(0x55FFD9A0, 0x00FFD9A0), null, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, rx * 1.6f, p)
        p.shader = null
        // shadow under the table
        hexPath(cx, cy + u * 3f, rx * 1.1f, ry * 1.1f)
        p.color = 0x88000000.toInt(); c.drawPath(path, p)
        // rim
        hexPath(cx, cy, rx * 1.1f, ry * 1.1f)
        p.shader = LinearGradient(0f, cy - ry, 0f, cy + ry, skin.rim, darken(skin.rim, 0.55f), Shader.TileMode.CLAMP)
        c.drawPath(path, p)
        p.shader = null
        sp.color = 0xFF0A0612.toInt(); sp.strokeWidth = u * 0.7f; c.drawPath(path, sp)
        // felt
        hexPath(cx, cy, rx * 0.97f, ry * 0.97f)
        p.shader = RadialGradient(cx, cy - ry * 0.25f, rx * 1.1f, skin.felt, skin.feltDark, Shader.TileMode.CLAMP)
        c.drawPath(path, p)
        p.shader = null
        sp.color = 0x33FFFFFF; sp.strokeWidth = u * 0.35f
        hexPath(cx, cy, rx * 0.8f, ry * 0.8f); c.drawPath(path, sp)
        hexPath(cx, cy, rx * 0.42f, ry * 0.42f); c.drawPath(path, sp)
        tp.textSize = u * 4.2f; tp.color = 0x22FFFFFF
        c.drawText("CHAOS PARTY", cx, cy + ry * 0.62f, tp)
        tableBmp?.recycle()
        tableBmp = bmp
    }

    private fun hexPath(x: Float, y: Float, ax: Float, ay: Float) {
        path.reset()
        for (k in 0 until 6) {
            val a = Math.toRadians(90.0 + 60.0 * k)
            val px = x + (cos(a) * ax).toFloat()
            val py = y + (sin(a) * ay).toFloat()
            if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
    }

    private fun darken(c: Int, k: Float): Int {
        val r = ((c shr 16 and 0xFF) * k).toInt(); val g = ((c shr 8 and 0xFF) * k).toInt(); val b = ((c and 0xFF) * k).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    // ------------------------------------------------------------------ incoming

    fun pushStep(events: List<Ev>, v: GameView) {
        if (view == null && steps.isEmpty() && stepView == null) {
            // First state: show it right away so the table isn't empty during the first animation.
            view = v.copy(total = 0, red = 0, black = 0, pink = 0)
        }
        steps.addLast(events to v)
        waitingReply = 0L
    }

    fun showEmote(player: Int, e: Int) {
        emotes[player] = e to SystemClock.uptimeMillis()
        Audio.play(Sfx.CARD, 0.5f)
    }

    fun showToast(msg: String) {
        toast = msg
        toastUntil = SystemClock.uptimeMillis() + 2200
        waitingReply = 0L
    }

    fun tableSkinChanged() = buildTable()

    private fun name(id: Int): String {
        val v = stepView ?: view
        if (v != null && id == v.you) return "Bạn"
        return v?.players?.getOrNull(id)?.name ?: "?"
    }

    private fun addLog(s: String, color: Int = Palette.TEXT) {
        log.addLast(s to color)
        while (log.size > 3) log.removeFirst()
    }

    // ------------------------------------------------------------------ animation driver

    private fun advance(now: Long) {
        var guard = 0
        while (guard++ < 50) {
            val e = cur
            if (e != null) {
                if (now - curStart < curDur) return
                endEvent(e)
                cur = null
            }
            if (stepIndex < stepEvents.size) {
                val next = stepEvents[stepIndex++]
                startEvent(next, now)
                continue
            }
            stepView?.let { applyView(it) }
            stepView = null
            val s = steps.removeFirstOrNull() ?: return
            stepEvents = s.first
            stepView = s.second
            stepIndex = 0
        }
    }

    private fun applyView(v: GameView) {
        val old = view
        view = v
        if (old?.turn != v.turn || old.phase != v.phase) {
            selectedTarget = -1
            pickingBombTarget = false
        }
        if (selectedSlot >= 0 && v.players.getOrNull(v.you)?.items?.getOrNull(selectedSlot) == null) selectedSlot = -1
        if (v.phase == Phase.GAME_OVER && !gameOverShown) {
            gameOverShown = true
            gameOverAt = SystemClock.uptimeMillis()
            host.onGameOver(v.winner == v.you)
        }
    }

    private fun speed(): Float = if (steps.size > 4) 0.5f else 1f

    private fun startEvent(e: Ev, now: Long) {
        cur = e
        curStart = now
        curDur = (Timing.of(e) * speed()).toLong()
        fired = false
        val v = stepView ?: view ?: return
        when (e.type) {
            Ev.ROUND_START -> {
                glueOnGun = false
                votes.clear()
                Audio.play(Sfx.WHOOSH)
                addLog("— Round ${e.int("round")} —", Palette.GOLD)
            }
            Ev.TURN -> {
                val id = e.int("p")
                turnShownAt = now
                gunTarget = angleTo(id) + 180f
                if (id == v.you) {
                    Audio.play(Sfx.DING, 0.6f)
                    Audio.vibrate(30)
                    showBanner(if (e.bool("again")) "Bạn được bắn tiếp!" else "Lượt của bạn!", "", Palette.GOLD, 900)
                }
            }
            Ev.SHOT -> {
                gunTarget = angleTo(e.int("aim"))
            }
            Ev.ITEM -> {
                val item = Item.valueOf(e.str("item"))
                Audio.play(Sfx.PICK)
                val who = e.int("p")
                val t = e.int("target")
                addLog("${name(who)} dùng ${Texts.item(item)}" + if (t >= 0) " → ${name(t)}" else "")
                val (x, y) = posOf(who)
                texts.add(FloatText(x, y - u * 10f, Texts.item(item), Palette.GOLD, now, 1100, u * 4f))
            }
            Ev.SYRINGE -> {
                val who = e.int("p")
                val (x, y) = posOf(who)
                if (e.bool("ok")) {
                    Audio.play(Sfx.HEAL)
                    texts.add(FloatText(x, y, "+1 ❤", Palette.GREEN, now, 1000, u * 5f))
                    happyUntil[who] = now + 900
                    burst(x, y, Palette.GREEN, 14, 1)
                } else {
                    Audio.play(Sfx.ZAP)
                    texts.add(FloatText(x, y, "Sốc thuốc! -2", Palette.RED, now, 1200, u * 5f))
                    hurt(who, now, 2)
                }
            }
            Ev.CHISEL_ON -> {
                Audio.play(Sfx.ZAP, 0.7f)
                texts.add(FloatText(cx, cy - u * 8f, "×2 SÁT THƯƠNG", 0xFFF59E0B.toInt(), now, 1000, u * 5f))
            }
            Ev.CHISEL_BOOM -> {
                val who = e.int("p")
                val (x, y) = posOf(who)
                Audio.play(Sfx.BOOM, 0.8f)
                explosion(x, y, 0.7f)
                texts.add(FloatText(x, y, "Nổ ngòi! -2", Palette.RED, now, 1300, u * 5f))
                hurt(who, now, 2)
                addLog("${name(who)} bị nổ ngòi cái đục!", Palette.RED)
            }
            Ev.BOMB_PLACED -> {
                Audio.play(Sfx.WHOOSH, 0.7f)
                val to = e.int("to")
                val (x, y) = posOf(to)
                texts.add(FloatText(x, y - u * 6f, "💣", Palette.TEXT, now, 900, u * 7f))
                if (to == v.you) {
                    showBanner("Bạn bị gài bom!", "Chuyển nó cho người khác trước khi nổ", Palette.RED, 1300)
                    Audio.vibrate(80)
                }
            }
            Ev.BOMB_BOOM -> {
                val who = e.int("p")
                val (x, y) = posOf(who)
                Audio.play(Sfx.BOOM)
                explosion(x, y, 1.2f)
                shake(now, u * 3f, 700)
                flash(0xFFFF7A1F.toInt(), now)
                val dmg = e.int("dmg")
                texts.add(FloatText(x, y, if (dmg > 0) "BÙM! -$dmg" else "BÙM! 🛡", Palette.RED, now, 1300, u * 6f))
                if (dmg > 0) hurt(who, now, dmg)
                addLog("💣 Bom nổ trúng ${name(who)}!", Palette.RED)
            }
            Ev.UNO -> {
                Audio.play(Sfx.CARD)
                Audio.play(Sfx.WHOOSH, 0.6f)
                showBanner("UNO ĐẢO CHỖ!", "Mọi người đổi ghế", 0xFFEF4444.toInt(), 1100)
            }
            Ev.ADRENALINE -> {
                val who = e.int("p")
                val (x, y) = posOf(who)
                Audio.play(Sfx.ZAP)
                burst(x, y, 0xFFFACC15.toInt(), 20, 0)
                texts.add(FloatText(x, y, "FULL MÁU • ×2", 0xFFFACC15.toInt(), now, 1200, u * 4.5f))
                happyUntil[who] = now + 1000
            }
            Ev.MAGNIFIER -> {
                Audio.play(Sfx.DING, 0.5f)
                val s = Shell.valueOf(e.str("shell"))
                showBanner("Viên hiện tại: ${Texts.shell(s)}", "Chỉ mình bạn thấy", Palette.shell(s).let { if (s == Shell.BLACK) 0xFF9CA3AF.toInt() else it }, 1400)
            }
            Ev.GLUE -> {
                glueOnGun = true
                Audio.play(Sfx.GLUE)
                showBanner("Bạn bôi keo lên súng 🤫", "Viên kế tiếp sau phát của bạn sẽ dội ngược", 0xFFA78BFA.toInt(), 1300)
                for (i in 0 until 12) particles.add(Particle(cx + rng.nextFloat() * u * 10 - u * 5, cy, rng.nextFloat() * u * 4 - u * 2, rng.nextFloat() * u * 6, 1f, 1f, 0xFFC4B5FD.toInt(), u * 1.6f, 2))
            }
            Ev.DEATH -> {
                val who = e.int("p")
                val (x, y) = posOf(who)
                Audio.play(Sfx.LOSE, 0.6f)
                burst(x, y, 0xFF9CA3AF.toInt(), 18, 0)
                val cause = when (e.str("cause")) {
                    "werewolf" -> "bị cả bàn treo cổ 🐺"
                    "adrenaline" -> "kiệt sức vì Adrenaline"
                    "bomb" -> "bay màu vì bom"
                    "syringe" -> "sốc thuốc"
                    "chisel" -> "nổ ngòi"
                    "rebound" -> "dính đạn dội ngược"
                    else -> "đã gục"
                }
                addLog("☠ ${name(who)} $cause", Palette.RED)
                if (who == v.you) showBanner("BẠN ĐÃ GỤC", "Ngồi xem mọi người hỗn loạn tiếp nhé", Palette.RED, 1100)
            }
            Ev.WEREWOLF -> {
                Audio.play(Sfx.BOING, 0.6f)
                val list = e.ints("votes")
                votes.clear()
                list.forEachIndexed { i, n -> if (n > 0) votes[i] = n }
                votesUntil = now + 3500
                val victim = e.int("victim")
                showBanner("🐺 MA SÓI ĐÃ NGỤY TRANG!", if (victim >= 0) "${name(victim)} nhiều phiếu nhất" else "Không ai bị bắn trúng", 0xFFA78BFA.toInt(), 3000)
            }
            Ev.ROUND_END -> Unit
            Ev.GAME_OVER -> {
                val wnr = e.int("winner")
                if (wnr == v.you) Audio.play(Sfx.WIN) else Audio.play(Sfx.LOSE)
            }
        }
    }

    private fun endEvent(e: Ev) {
        if (e.type == Ev.SHOT && !fired) fire(e, SystemClock.uptimeMillis())
    }

    private fun fire(e: Ev, now: Long) {
        fired = true
        val shell = Shell.valueOf(e.str("shell"))
        val hit = e.int("hit")
        val shooter = e.int("p")
        val v = stepView ?: view
        val (x, y) = posOf(hit)
        recoilUntil = now + 250
        if (e.bool("rebound")) glueOnGun = false
        val dmg = e.int("dmg")
        val armor = e.int("armor")
        val blind = v?.shownEvent == RoundEvent.COLORBLIND
        when (shell) {
            Shell.RED -> {
                Audio.play(Sfx.SHOT)
                shake(now, u * (1.5f + dmg), 450)
                flash(Palette.RED, now)
                if (dmg > 0) hurt(hit, now, dmg)
                burst(x, y, Palette.RED, 16, 0)
                val txt = when {
                    dmg > 0 && armor > 0 -> "-$dmg (🛡$armor)"
                    dmg > 0 -> "-$dmg"
                    else -> "🛡 đỡ!"
                }
                texts.add(FloatText(x, y - u * 4f, txt, Palette.RED, now, 1100, u * 6.5f))
            }
            Shell.BLACK -> {
                Audio.play(Sfx.CLICK)
                for (i in 0 until 10) particles.add(Particle(cx, cy, rng.nextFloat() * u * 6 - u * 3, -rng.nextFloat() * u * 5, 1f, 1f, 0xFF9CA3AF.toInt(), u * 2f, 1))
                texts.add(FloatText(x, y - u * 4f, "Cạch... đạn đen", 0xFFD1D5DB.toInt(), now, 1000, u * 4.2f))
            }
            Shell.PINK -> {
                Audio.play(Sfx.HEAL)
                burst(x, y, Palette.PINK, 18, 1)
                happyUntil[hit] = now + 900
                texts.add(FloatText(x, y - u * 4f, if (e.int("heal") > 0) "+1 ❤" else "Đạn hồng (đầy máu)", Palette.PINK, now, 1100, u * 5.5f))
            }
        }
        val sName = if (blind) "🌈" else Texts.shell(shell).lowercase()
        val line = when {
            e.bool("rebound") -> "${name(shooter)} dính keo! Đạn $sName dội vào chính mình"
            hit == shooter -> "${name(shooter)} tự bắn mình: đạn $sName"
            e.bool("madness") -> "${name(shooter)} run tay bắn trúng ${name(hit)}: đạn $sName"
            else -> "${name(shooter)} bắn ${name(hit)}: đạn $sName"
        }
        addLog(line, if (shell == Shell.RED) 0xFFFF8A8A.toInt() else Palette.TEXT)
        if (v != null && hit == v.you) Audio.vibrate(if (shell == Shell.RED) 120 else 25, shell == Shell.RED)
        if (shell == Shell.BLACK && hit == shooter) Unit
    }

    private fun hurt(id: Int, now: Long, dmg: Int) {
        hurtUntil[id] = now + 900
        val v = stepView ?: view
        if (v != null && id == v.you) {
            flash(Palette.RED, now)
            Audio.vibrate(100L * dmg, true)
        }
    }

    private fun shake(now: Long, amp: Float, dur: Long) {
        shakeAmp = amp; shakeUntil = now + dur
    }

    private fun flash(c: Int, now: Long) {
        flashColor = c; flashStart = now
    }

    private fun showBanner(t: String, sub: String, color: Int, dur: Long) {
        banner = Triple(t, sub, color)
        bannerStart = SystemClock.uptimeMillis()
        bannerDur = dur
    }

    private fun burst(x: Float, y: Float, color: Int, n: Int, kind: Int) {
        for (i in 0 until n) {
            val a = rng.nextFloat() * 6.283f
            val sp = u * (2f + rng.nextFloat() * 6f)
            particles.add(Particle(x, y, cos(a) * sp, sin(a) * sp - u * 2f, 1f, 0.6f + rng.nextFloat() * 0.6f, color, u * (0.8f + rng.nextFloat()), kind))
        }
    }

    private fun explosion(x: Float, y: Float, scale: Float) {
        burst(x, y, 0xFFFF7A1F.toInt(), (24 * scale).toInt(), 0)
        burst(x, y, 0xFFFFD23F.toInt(), (14 * scale).toInt(), 0)
        for (i in 0 until (10 * scale).toInt()) {
            particles.add(Particle(x, y, rng.nextFloat() * u * 4 - u * 2, -rng.nextFloat() * u * 4, 1f, 1.4f, 0xFF4B5563.toInt(), u * 3f, 1))
        }
    }

    // ------------------------------------------------------------------ geometry

    private fun hexIndexOf(pv: PlayerView, v: GameView, seatOverride: Int = -1): Int {
        val n = v.players.size
        val mySeat = v.players[v.you].seat
        val seat = if (seatOverride >= 0) seatOverride else pv.seat
        return LAYOUTS[n][(seat - mySeat).mod(n)]
    }

    /** Screen position of a player's avatar, including the Uno seat-shuffle tween. */
    private fun posOf(id: Int): Pair<Float, Float> {
        val v = view ?: return cx to cy
        val pv = v.players.getOrNull(id) ?: return cx to cy
        val e = cur
        val k = hexIndexOf(pv, v)
        if (e != null && e.type == Ev.UNO) {
            val seats = e.ints("seats")
            val n = v.players.size
            val newMy = seats.getOrElse(v.you) { v.players[v.you].seat }
            val k2 = LAYOUTS[n][(seats.getOrElse(id) { pv.seat } - newMy).mod(n)]
            val t = ease(progress())
            val x = vx[k] + (vx[k2] - vx[k]) * t
            val y = vy[k] + (vy[k2] - vy[k]) * t - sin(t * Math.PI).toFloat() * u * 8f
            return x to y
        }
        return vx[k] to vy[k]
    }

    private fun angleTo(id: Int): Float {
        val (x, y) = posOf(id)
        return Math.toDegrees(atan2((y - cy).toDouble(), (x - cx).toDouble())).toFloat()
    }

    private fun progress(): Float = if (curDur <= 0) 1f else ((SystemClock.uptimeMillis() - curStart).toFloat() / curDur).coerceIn(0f, 1f)
    private fun ease(t: Float) = 1f - (1f - t) * (1f - t) * (1f - t)

    private fun avatarR(k: Int) = if (k == 0) u * 8.5f else u * 7f

    private fun idle(): Boolean = cur == null && steps.isEmpty() && stepIndex >= stepEvents.size && stepView == null

    private fun myTurn(): Boolean {
        val v = view ?: return false
        return idle() && v.phase == Phase.PLAYING && v.turn == v.you && v.players[v.you].alive &&
            SystemClock.uptimeMillis() > waitingReply
    }

    // ------------------------------------------------------------------ drawing

    override fun onDraw(c: Canvas) {
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) 0.016f else ((now - lastFrame) / 1000f).coerceAtMost(0.05f)
        lastFrame = now
        advance(now)
        val e = cur
        if (e != null && e.type == Ev.SHOT) updateShot(e, now)
        // ease gun angle
        var d = ((gunTarget - gunAngle + 540f) % 360f) - 180f
        if (e?.type == Ev.SHOT && (e.bool("madness")) && progress() in 0.25f..0.6f) d = 25f
        gunAngle += d * min(1f, dt * 10f)

        c.save()
        if (now < shakeUntil) c.translate((rng.nextFloat() - 0.5f) * shakeAmp * 2, (rng.nextFloat() - 0.5f) * shakeAmp * 2)
        tableBmp?.let { c.drawBitmap(it, 0f, 0f, null) }
        val v = view
        if (v != null) {
            drawGun(c, v, now)
            drawPlayers(c, v, now)
            drawParticles(c, dt)
            drawTexts(c, now)
        }
        c.restore()
        if (v != null) {
            drawTopBar(c, v, now)
            drawBottom(c, v, now)
            if (e?.type == Ev.ROUND_START) drawWheel(c, e, now)
            drawBanner(c, now)
            if (v.phase == Phase.OFFERS && v.offer != null && idle()) drawOffer(c, v.offer)
            if (emotePicker) drawEmotePicker(c)
            if (v.phase == Phase.GAME_OVER && idle() && now - gameOverAt > 600) drawGameOver(c, v)
        } else {
            tp.textSize = u * 5f; tp.color = Palette.DIM
            c.drawText("Đang chuẩn bị bàn...", cx, h / 2, tp)
        }
        // screen flash
        val fa = 1f - (now - flashStart) / 350f
        if (fa > 0f) {
            p.color = flashColor; p.alpha = (fa * 90).toInt()
            c.drawRect(0f, 0f, w, h, p)
        }
        toast?.let { msg ->
            if (now < toastUntil) {
                tp.textSize = u * 3.8f
                val tw = tp.measureText(msg) + u * 8f
                r1.set(cx - tw / 2, bottomTop - u * 11f, cx + tw / 2, bottomTop - u * 3f)
                p.color = 0xEE000000.toInt(); c.drawRoundRect(r1, u * 3f, u * 3f, p)
                tp.color = 0xFFFFB4B4.toInt(); c.drawText(msg, cx, r1.centerY() + u * 1.3f, tp)
            } else toast = null
        }
        postInvalidateOnAnimation()
    }

    private fun updateShot(e: Ev, now: Long) {
        val pr = progress()
        val special = e.bool("rebound") || e.bool("madness")
        val fireAt = if (special) 0.72f else 0.42f
        if (special && pr > 0.3f) {
            gunTarget = angleTo(e.int("hit"))
            if (e.bool("rebound") && pr < 0.34f && banner?.first != "DÍNH KEO!") {
                showBanner("DÍNH KEO!", "Phát này dội ngược vào ${name(e.int("p"))}", 0xFFA78BFA.toInt(), 1000)
                Audio.play(Sfx.BOING)
            }
            if (e.bool("madness") && pr < 0.34f && banner?.first != "ĐIÊN LOẠN!") {
                showBanner("ĐIÊN LOẠN!", "Súng tự xoay...", 0xFFEF4444.toInt(), 900)
                Audio.play(Sfx.WHOOSH)
            }
        }
        if (!fired && pr >= fireAt) fire(e, now)
    }

    private fun drawPlayers(c: Canvas, v: GameView, now: Long) {
        val e = cur
        val aimId = when {
            selectedTarget >= 0 -> selectedTarget
            else -> -1
        }
        // draw farther players first
        val order = v.players.sortedBy { posOf(it.id).second }
        for (pv in order) {
            val (x, y) = posOf(pv.id)
            val k = hexIndexOf(pv, v)
            val r = avatarR(k)
            val isTurn = v.phase == Phase.PLAYING && v.turn == pv.id && pv.alive
            if (isTurn) {
                val pulse = 0.5f + 0.5f * sin(now / 180f)
                p.color = Palette.GOLD; p.alpha = (60 + pulse * 70).toInt()
                c.drawCircle(x, y, r * 1.35f, p)
                if (online && !pv.isBot && turnShownAt > 0) {
                    val left = 1f - ((now - turnShownAt).toFloat() / TURN_LIMIT_MS).coerceIn(0f, 1f)
                    sp.color = if (left < 0.25f) Palette.RED else Palette.GOLD; sp.strokeWidth = u * 0.9f
                    r1.set(x - r * 1.25f, y - r * 1.25f, x + r * 1.25f, y + r * 1.25f)
                    c.drawArc(r1, -90f, 360f * left, false, sp)
                }
            }
            if (aimId == pv.id || (pickingBombTarget && pv.alive && pv.id != v.you)) {
                sp.color = if (pickingBombTarget) 0xFFFF7A1F.toInt() else Palette.RED
                sp.strokeWidth = u * 0.8f
                val rr = r * (1.3f + 0.08f * sin(now / 120f))
                c.drawCircle(x, y, rr, sp)
                c.drawLine(x - rr - u * 2, y, x - rr + u * 2, y, sp); c.drawLine(x + rr - u * 2, y, x + rr + u * 2, y, sp)
                c.drawLine(x, y - rr - u * 2, x, y - rr + u * 2, sp); c.drawLine(x, y + rr - u * 2, x, y + rr + u * 2, sp)
            }
            val mood = when {
                !pv.alive -> 3
                now < (hurtUntil[pv.id] ?: 0) -> 1
                now < (happyUntil[pv.id] ?: 0) || (v.phase == Phase.GAME_OVER && v.winner == pv.id) -> 2
                else -> 0
            }
            val blinkT = ((now + pv.id * 1337) % 3800L)
            val blink = if (blinkT < 140) 1f - abs(blinkT - 70) / 70f else 0f
            val look = ((angleTo(v.turn.coerceAtLeast(0)) - angleTo(pv.id)) / 180f).coerceIn(-1f, 1f)
            val bob = if (pv.alive) sin(now / 500f + pv.id) * u * 0.4f else 0f
            Art.avatar(c, x, y + bob, r, Skins.avatar(pv.avatar), mood, blink, if (pv.alive) 255 else 150, look)
            if (pv.adrenaline > 0 && pv.alive) {
                Art.item(c, Item.ADRENALINE, x + r * 0.85f, y - r * 0.8f, r * 0.6f, 255)
            }
            // name plate
            if (k != 0) drawPlate(c, pv, x, y + r * 1.12f, now)
            // emote bubble
            emotes[pv.id]?.let { (em, t0) ->
                val age = now - t0
                if (age < 2200) {
                    val a = if (age > 1800) (2200 - age) / 400f else 1f
                    val by = y - r * 1.7f - min(1f, age / 200f) * u * 2f
                    p.color = 0xFFFFFFFF.toInt(); p.alpha = (a * 240).toInt()
                    c.drawCircle(x + r * 0.6f, by, u * 6f, p)
                    tp.textSize = u * 6.5f; tp.alpha = (a * 255).toInt(); tp.color = 0xFF000000.toInt()
                    c.drawText(EMOTES[em], x + r * 0.6f, by + u * 2.3f, tp)
                    tp.alpha = 255
                } else emotes.remove(pv.id)
            }
            // werewolf votes
            if (now < votesUntil) votes[pv.id]?.let { n ->
                r1.set(x - u * 6f, y - r - u * 7f, x + u * 6f, y - r - u * 1f)
                p.color = 0xFF7C3AED.toInt(); c.drawRoundRect(r1, u * 2f, u * 2f, p)
                tp.textSize = u * 3.6f; tp.color = Palette.TEXT
                c.drawText("🐺 $n", x, r1.bottom - u * 1.6f, tp)
            }
        }
        if (e?.type == Ev.ITEM && e.str("item").let { it == Item.BOMB.name || it == Item.LIVE_BOMB.name }) Unit
    }

    private fun drawPlate(c: Canvas, pv: PlayerView, x: Float, y: Float, now: Long) {
        val pw = u * 26f
        r1.set(x - pw / 2, y, x + pw / 2, y + u * 11.5f)
        p.color = 0xCC0E0820.toInt(); c.drawRoundRect(r1, u * 2.2f, u * 2.2f, p)
        tp.textSize = u * 3.1f; tp.color = if (pv.alive) Palette.TEXT else Palette.DIM
        val nm = (if (pv.isBot) "🤖 " else "") + pv.name
        c.drawText(ellipsize(nm, pw - u * 2f), x, y + u * 3.6f, tp)
        // hearts
        val hs = u * 2.6f
        val hx = x - (MAX_HP - 1) * hs * 0.6f
        for (i in 0 until MAX_HP) Art.heart(c, hx + i * hs * 1.2f, y + u * 6.2f, hs, i < pv.hp)
        if (pv.armor > 0) for (i in 0 until pv.armor) Art.shield(c, x + pw / 2 - u * 2f - i * u * 2.4f, y + u * 2.6f, u * 2.4f)
        // item slots
        val ss = u * 2.6f
        val sx = x - (SLOTS * ss + (SLOTS - 1) * u * 0.8f) / 2
        for (i in 0 until SLOTS) {
            r1.set(sx + i * (ss + u * 0.8f), y + u * 8.2f, sx + i * (ss + u * 0.8f) + ss, y + u * 8.2f + ss)
            p.color = if (pv.items.getOrNull(i) != null) 0xFFB794F6.toInt() else 0x33FFFFFF
            c.drawRoundRect(r1, u * 0.6f, u * 0.6f, p)
        }
        if (pv.choosing) {
            tp.textSize = u * 2.6f; tp.color = Palette.GOLD
            c.drawText("đang lấy đồ" + ".".repeat(((now / 300) % 4).toInt()), x, y + u * 14.5f, tp)
        }
    }

    private fun ellipsize(s: String, maxW: Float): String {
        if (tp.measureText(s) <= maxW) return s
        var t = s
        while (t.length > 1 && tp.measureText("$t…") > maxW) t = t.dropLast(1)
        return "$t…"
    }

    private fun drawGun(c: Canvas, v: GameView, now: Long) {
        val holder = v.turn
        val skin = Skins.gun(v.players.getOrNull(holder)?.gun ?: 0)
        if (selectedTarget >= 0 && myTurn()) gunTarget = angleTo(selectedTarget)
        val recoil = if (now < recoilUntil) (recoilUntil - now) / 250f else 0f
        val e = cur
        val glowAlpha = if (v.chisel) (140 + 80 * sin(now / 100f)).toInt() else 0
        Art.gun(c, cx, cy, u * 30f, gunAngle, skin, recoil, 0xFFF59E0B.toInt(), glowAlpha)
        if (e?.type == Ev.SHOT && fired) {
            val t = (now - (curStart + (curDur * if (e.bool("rebound") || e.bool("madness")) 0.72f else 0.42f).toLong())) / 300f
            val color = when (Shell.valueOf(e.str("shell"))) {
                Shell.RED -> 0xFFFFB020.toInt()
                Shell.BLACK -> 0xFF9CA3AF.toInt()
                Shell.PINK -> Palette.PINK
            }
            Art.muzzle(c, cx, cy, u * 30f, gunAngle, color, t)
        }
        if (glueOnGun) {
            // drippy goo on the barrel, only the gluer ever sees this
            val a = Math.toRadians(gunAngle.toDouble())
            for (i in 0 until 4) {
                val d = u * (4f + i * 4.5f)
                val gx = cx + (cos(a) * d).toFloat()
                val gy = cy + (sin(a) * d).toFloat()
                val drip = (sin(now / 300f + i) * 0.5f + 0.5f) * u * 2.5f
                p.color = 0xCCC4B5FD.toInt()
                c.drawCircle(gx, gy, u * 1.3f, p)
                c.drawCircle(gx, gy + drip + u * 1.2f, u * 0.7f, p)
            }
        }
        if (v.chisel) {
            tp.textSize = u * 3.4f; tp.color = 0xFFF59E0B.toInt()
            c.drawText("×2", cx, cy - u * 7f, tp)
        }
    }

    private fun drawParticles(c: Canvas, dt: Float) {
        val it = particles.iterator()
        while (it.hasNext()) {
            val q = it.next()
            q.life -= dt / q.maxLife
            if (q.life <= 0f) { it.remove(); continue }
            q.x += q.vx * dt * 10f
            q.y += q.vy * dt * 10f
            q.vy += u * dt * (if (q.kind == 1) -1f else 4f)
            p.color = q.color; p.alpha = (q.life * 255).toInt().coerceIn(0, 255)
            when (q.kind) {
                1 -> c.drawCircle(q.x, q.y, q.size * (2f - q.life), p)
                2 -> c.drawCircle(q.x, q.y, q.size, p)
                else -> c.drawCircle(q.x, q.y, q.size * q.life, p)
            }
        }
        if (particles.size > 400) particles.subList(0, particles.size - 400).clear()
    }

    private fun drawTexts(c: Canvas, now: Long) {
        val it = texts.iterator()
        while (it.hasNext()) {
            val t = it.next()
            val age = (now - t.start).toFloat() / t.dur
            if (age >= 1f) { it.remove(); continue }
            val y = t.y - age * u * 8f
            val a = if (age > 0.7f) (1f - age) / 0.3f else 1f
            val pop = if (age < 0.15f) 0.6f + age / 0.15f * 0.4f else 1f
            tp.textSize = t.size * pop
            tp.color = 0xFF000000.toInt(); tp.alpha = (a * 200).toInt()
            c.drawText(t.text, t.x + u * 0.4f, y + u * 0.4f, tp)
            tp.color = t.color; tp.alpha = (a * 255).toInt()
            c.drawText(t.text, t.x, y, tp)
            tp.alpha = 255
        }
    }

    private fun drawTopBar(c: Canvas, v: GameView, now: Long) {
        // round + event chip
        tp.textAlign = Paint.Align.LEFT
        tp.textSize = u * 3.6f; tp.color = Palette.DIM
        c.drawText("ROUND ${v.round}", u * 4f, u * 6.5f, tp)
        val ev = v.shownEvent
        tp.textSize = u * 3.8f
        val label = Texts.event(ev) + if (ev == RoundEvent.ROTATION) " • còn ${v.rotationLeft} lượt" else ""
        val lw = tp.measureText(label) + u * 5f
        r1.set(u * 3f, u * 8.2f, u * 3f + lw, u * 14f)
        p.color = Palette.event(ev); c.drawRoundRect(r1, u * 3f, u * 3f, p)
        tp.color = Palette.TEXT
        c.drawText(label, u * 5.5f, u * 12.4f, tp)
        tp.textAlign = Paint.Align.CENTER
        // buttons
        for ((rect, icon) in listOf(menuBtn to "⚙", emoteBtn to "😀")) {
            p.color = 0x55FFFFFF; c.drawRoundRect(rect, u * 3f, u * 3f, p)
            tp.textSize = u * 5.5f; tp.color = Palette.TEXT
            c.drawText(icon, rect.centerX(), rect.centerY() + u * 2f, tp)
        }
        // magazine counter
        val top = u * 16f
        val blind = v.red < 0
        val panelW = u * 64f
        r1.set(cx - panelW / 2, top, cx + panelW / 2, top + u * 10f)
        p.color = Palette.PANEL; c.drawRoundRect(r1, u * 3f, u * 3f, p)
        sp.color = 0x44FFFFFF; sp.strokeWidth = u * 0.3f; c.drawRoundRect(r1, u * 3f, u * 3f, sp)
        val sw = u * 3.2f
        val sh = u * 6f
        val ty = top + u * 2f
        tp.textSize = u * 4.6f
        if (blind) {
            Art.shellIcon(c, cx - u * 14f, ty, sw, sh, null, 255, (now % 2000) / 2000f)
            tp.color = Palette.TEXT
            c.drawText("× ${v.total}  •  ???", cx + u * 4f, ty + u * 4.8f, tp)
        } else {
            val items = listOf(Shell.RED to v.red, Shell.BLACK to v.black, Shell.PINK to v.pink)
            val colW = panelW / 3f
            items.forEachIndexed { i, (s, n) ->
                val x0 = cx - panelW / 2 + colW * i + colW * 0.22f
                Art.shellIcon(c, x0, ty, sw, sh, s, if (n > 0) 255 else 90)
                tp.color = if (n > 0) Palette.TEXT else Palette.DIM
                tp.textAlign = Paint.Align.LEFT
                c.drawText("× $n", x0 + sw + u * 2f, ty + u * 4.8f, tp)
                tp.textAlign = Paint.Align.CENTER
            }
        }
        v.known?.let { k ->
            tp.textSize = u * 3f; tp.color = Palette.CYAN
            c.drawText("🔍 Viên hiện tại: ${Texts.shell(k)}", cx, top + u * 13.5f, tp)
        }
    }

    private fun drawBottom(c: Canvas, v: GameView, now: Long) {
        val me = v.players[v.you]
        r1.set(0f, bottomTop, w, h)
        p.shader = LinearGradient(0f, bottomTop, 0f, h, 0xF0140B26.toInt(), 0xFF0A0614.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(r1, p); p.shader = null
        sp.color = 0x55FFFFFF; sp.strokeWidth = u * 0.3f; c.drawLine(0f, bottomTop, w, bottomTop, sp)
        // my hearts and armor
        val hs = u * 4.2f
        for (i in 0 until MAX_HP) Art.heart(c, u * 6f + i * hs * 1.25f, bottomTop + u * 5f, hs, i < me.hp)
        for (i in 0 until MAX_ARMOR) {
            if (i < me.armor) Art.shield(c, u * 36f + i * u * 4.6f, bottomTop + u * 5f, u * 4.2f)
        }
        // status line
        tp.textAlign = Paint.Align.RIGHT
        tp.textSize = u * 3.6f
        val status = when {
            !me.alive -> "Bạn đã gục 💀"
            v.phase == Phase.GAME_OVER -> "Hết trận"
            v.phase == Phase.OFFERS -> if (v.offer != null) "Chọn đồ từ hộp" else "Chờ mọi người lấy đồ..."
            v.turn == v.you -> if (idle()) "LƯỢT CỦA BẠN" else "..."
            else -> "Lượt: ${name(v.turn)}"
        }
        tp.color = if (v.turn == v.you && v.phase == Phase.PLAYING) Palette.GOLD else Palette.DIM
        c.drawText(status, w - u * 4f, bottomTop + u * 6.3f, tp)
        tp.textAlign = Paint.Align.CENTER
        // log
        tp.textSize = u * 2.9f
        log.forEachIndexed { i, (s, col) ->
            tp.color = col; tp.alpha = 110 + 145 * (i + 1) / log.size.coerceAtLeast(1)
            c.drawText(ellipsize(s, w - u * 8f), cx, bottomTop + u * 10.5f + i * u * 3.3f, tp)
        }
        tp.alpha = 255
        // slots
        for (i in 0 until SLOTS) {
            val r = slotRects[i]
            val item = me.items.getOrNull(i)
            p.color = if (i == selectedSlot) 0xFF4C2A85.toInt() else 0xFF22163D.toInt()
            c.drawRoundRect(r, u * 3f, u * 3f, p)
            sp.color = if (i == selectedSlot) Palette.GOLD else 0x44FFFFFF; sp.strokeWidth = u * 0.5f
            c.drawRoundRect(r, u * 3f, u * 3f, sp)
            if (item != null) Art.item(c, item, r.centerX(), r.centerY(), r.width() * 0.7f, 255, now / 1000f)
        }
        // action area
        val btnLabel: String?
        var enabled = myTurn()
        if (selectedSlot >= 0) {
            val item = me.items.getOrNull(selectedSlot)
            if (item != null) {
                // item info card above slots
                val top = bottomTop - u * 17f
                r1.set(u * 3f, top, w - u * 3f, bottomTop - u * 1.5f)
                p.color = 0xF2241544.toInt(); c.drawRoundRect(r1, u * 3f, u * 3f, p)
                sp.color = Palette.GOLD; sp.strokeWidth = u * 0.3f; c.drawRoundRect(r1, u * 3f, u * 3f, sp)
                tp.textAlign = Paint.Align.LEFT
                tp.textSize = u * 4f; tp.color = Palette.GOLD
                c.drawText(Texts.item(item), u * 6f, top + u * 5f, tp)
                tp.textSize = u * 3.1f; tp.color = Palette.TEXT
                wrap(Texts.itemDesc(item), w - u * 12f).take(3).forEachIndexed { i, line -> c.drawText(line, u * 6f, top + u * 9f + i * u * 3.6f, tp) }
                tp.textAlign = Paint.Align.CENTER
            }
            val block = if (item != null) clientBlocker(v, item) else null
            btnLabel = when {
                pickingBombTarget -> "Chạm vào người để gài bom • Huỷ"
                block != null && enabled -> block
                else -> "DÙNG"
            }
            if (block != null && !pickingBombTarget) enabled = false
        } else if (selectedTarget >= 0) {
            btnLabel = if (selectedTarget == v.you) "🔫 TỰ BẮN BẢN THÂN" else "🔫 BẮN ${name(selectedTarget).uppercase()}"
        } else {
            btnLabel = if (myTurn()) "Chạm vào một người (hoặc chính bạn) để ngắm" else null
            enabled = false
        }
        if (btnLabel != null && v.phase == Phase.PLAYING && me.alive) {
            p.color = if (enabled) Palette.ACCENT else 0xFF2E2347.toInt()
            c.drawRoundRect(actionBtn, u * 4f, u * 4f, p)
            tp.textSize = if (enabled) u * 4.8f else u * 3.4f
            tp.color = if (enabled) Palette.TEXT else Palette.DIM
            c.drawText(ellipsize(btnLabel, actionBtn.width() - u * 4f), actionBtn.centerX(), actionBtn.centerY() + tp.textSize * 0.35f, tp)
        }
    }

    private fun wrap(s: String, maxW: Float): List<String> {
        val out = ArrayList<String>()
        var line = ""
        for (word in s.split(' ')) {
            val t = if (line.isEmpty()) word else "$line $word"
            if (tp.measureText(t) > maxW && line.isNotEmpty()) { out.add(line); line = word } else line = t
        }
        if (line.isNotEmpty()) out.add(line)
        return out
    }

    /** Quick client-side check so obviously impossible items are greyed out; the host has the final say. */
    private fun clientBlocker(v: GameView, item: Item): String? {
        val me = v.players[v.you]
        return when (item) {
            Item.SYRINGE -> if (me.hp >= MAX_HP) "Máu đã đầy" else null
            Item.ARMOR -> if (me.armor >= MAX_ARMOR) "Giáp đã tối đa" else null
            Item.CHISEL -> if (v.chisel) "Đã đục rồi" else null
            Item.MAGNIFIER -> when {
                v.red < 0 -> "Mù màu: chỉ thấy cầu vồng"
                v.known != null -> "Bạn đã soi viên này"
                else -> null
            }
            Item.ADRENALINE -> if (me.adrenaline > 0) "Adrenaline đang chạy" else null
            Item.GLUE -> if (v.total < 2) "Cần ít nhất 2 viên trong súng" else null
            Item.UNO -> if (v.players.count { it.alive } < 3) "Cần ít nhất 3 người còn sống" else null
            Item.BOMB, Item.LIVE_BOMB ->
                if (v.players.none { it.alive && it.id != v.you && it.items.any { s -> s == null } }) "Không ai còn ô trống" else null
        }
    }

    private fun drawWheel(c: Canvas, e: Ev, now: Long) {
        val t = (now - curStart).toFloat() / curDur
        val spinEnd = 0.62f
        val slot = e.int("slot")
        p.color = 0xCC05020C.toInt(); c.drawRect(0f, 0f, w, h, p)
        val wr = u * 33f
        val wy = h * 0.36f
        val segDeg = 360f / Wheel.SLOTS.size
        val finalRot = -90f - (slot * segDeg + segDeg / 2) - 360f * 5
        val st = (t / spinEnd).coerceIn(0f, 1f)
        val rot = finalRot * ease(st)
        // tick sound when a segment passes the pointer
        val segNow = ((-rot) / segDeg).toInt()
        if (segNow != lastSeg && st < 1f) { lastSeg = segNow; Audio.play(Sfx.TICK, 0.4f, 1f + st * 0.3f) }
        r1.set(cx - wr, wy - wr, cx + wr, wy + wr)
        for (i in Wheel.SLOTS.indices) {
            val ev = Wheel.SLOTS[i]
            p.color = Palette.event(ev)
            if (i % 2 == 1 && ev == RoundEvent.NORMAL) p.color = darken(p.color, 0.8f)
            c.drawArc(r1, rot + i * segDeg, segDeg, true, p)
            sp.color = 0xFF0A0612.toInt(); sp.strokeWidth = u * 0.5f
            c.drawArc(r1, rot + i * segDeg, segDeg, true, sp)
            c.save()
            c.rotate(rot + i * segDeg + segDeg / 2, cx, wy)
            tp.textSize = u * 3.2f; tp.color = Palette.TEXT
            c.drawText(if (ev == RoundEvent.NORMAL) "Thường" else Texts.event(ev), cx + wr * 0.6f, wy + u * 1.1f, tp)
            c.restore()
        }
        sp.color = Palette.GOLD; sp.strokeWidth = u * 1.2f; c.drawCircle(cx, wy, wr, sp)
        p.color = 0xFF1A0F2E.toInt(); c.drawCircle(cx, wy, wr * 0.16f, p)
        tp.textSize = u * 5f; tp.color = Palette.GOLD; c.drawText("?", cx, wy + u * 1.8f, tp)
        // pointer
        path.reset(); path.moveTo(cx - u * 3.5f, wy - wr - u * 4f); path.lineTo(cx + u * 3.5f, wy - wr - u * 4f); path.lineTo(cx, wy - wr + u * 3f); path.close()
        p.color = Palette.TEXT; c.drawPath(path, p)
        tp.textSize = u * 6f; tp.color = Palette.TEXT
        c.drawText("ROUND ${e.int("round")}", cx, wy - wr - u * 9f, tp)
        if (t >= spinEnd) {
            if (!wheelDinged) { wheelDinged = true; Audio.play(Sfx.DING) }
            val ev = Wheel.SLOTS[slot]
            tp.textSize = u * 7.5f; tp.color = Palette.event(ev).let { if (ev == RoundEvent.NORMAL) Palette.TEXT else lighten(it) }
            c.drawText(Texts.event(ev).uppercase(), cx, wy + wr + u * 11f, tp)
            tp.textSize = u * 3.4f; tp.color = Palette.DIM
            wrap(Texts.eventDesc(ev), w - u * 14f).forEachIndexed { i, line -> c.drawText(line, cx, wy + wr + u * 17f + i * u * 4.4f, tp) }
            // shells being loaded
            val loadT = ((t - 0.75f) / 0.25f).coerceIn(0f, 1f)
            if (loadT > 0f) {
                val red = e.int("red"); val black = e.int("black"); val pink = e.int("pink")
                val blind = e.bool("blind")
                val list = ArrayList<Shell>()
                repeat(red) { list.add(Shell.RED) }; repeat(black) { list.add(Shell.BLACK) }; repeat(pink) { list.add(Shell.PINK) }
                val sw = u * 5f
                val total = list.size
                val startX = cx - (total * sw * 1.4f) / 2
                val shown = (loadT * total * 1.5f).toInt().coerceAtMost(total)
                for (i in 0 until shown) {
                    Art.shellIcon(c, startX + i * sw * 1.4f, wy + wr + u * 28f, sw, sw * 1.9f, if (blind) null else list[i], 255, (now % 2000) / 2000f)
                }
                tp.textSize = u * 3.6f; tp.color = Palette.TEXT
                c.drawText(if (blind) "$total viên • màu bí ẩn" else "$red đỏ • $black đen • $pink hồng", cx, wy + wr + u * 43f, tp)
            }
        } else wheelDinged = false
    }

    private var lastSeg = 0
    private var wheelDinged = false

    private fun lighten(c: Int): Int {
        val r = min(255, (c shr 16 and 0xFF) + 80); val g = min(255, (c shr 8 and 0xFF) + 80); val b = min(255, (c and 0xFF) + 80)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun drawBanner(c: Canvas, now: Long) {
        val b = banner ?: return
        val age = now - bannerStart
        if (age > bannerDur) { banner = null; return }
        val a = when {
            age < 150 -> age / 150f
            age > bannerDur - 250 -> (bannerDur - age) / 250f
            else -> 1f
        }
        val y = cy - ry * 0.1f
        tp.textSize = u * 6.2f
        val tw = max(tp.measureText(b.first), run { tp.textSize = u * 3.3f; tp.measureText(b.second) }) + u * 10f
        r1.set(cx - tw / 2, y - u * 8f, cx + tw / 2, y + if (b.second.isEmpty()) u * 3f else u * 7f)
        p.color = 0xEE0A0614.toInt(); p.alpha = (a * 235).toInt(); c.drawRoundRect(r1, u * 3f, u * 3f, p)
        sp.color = b.third; sp.alpha = (a * 255).toInt(); sp.strokeWidth = u * 0.5f; c.drawRoundRect(r1, u * 3f, u * 3f, sp)
        tp.textSize = u * 6.2f; tp.color = b.third; tp.alpha = (a * 255).toInt()
        c.drawText(b.first, cx, y, tp)
        if (b.second.isNotEmpty()) {
            tp.textSize = u * 3.3f; tp.color = Palette.TEXT; tp.alpha = (a * 255).toInt()
            c.drawText(b.second, cx, y + u * 4.8f, tp)
        }
        tp.alpha = 255; sp.alpha = 255
    }

    private fun drawOffer(c: Canvas, item: Item) {
        p.color = 0xB3000000.toInt(); c.drawRect(0f, 0f, w, h, p)
        val cw = u * 78f
        val chh = u * 92f
        r1.set(cx - cw / 2, h * 0.45f - chh / 2, cx + cw / 2, h * 0.45f + chh / 2)
        p.shader = LinearGradient(0f, r1.top, 0f, r1.bottom, 0xFF3A2466.toInt(), 0xFF1A0F2E.toInt(), Shader.TileMode.CLAMP)
        c.drawRoundRect(r1, u * 5f, u * 5f, p); p.shader = null
        sp.color = Palette.GOLD; sp.strokeWidth = u * 0.6f; c.drawRoundRect(r1, u * 5f, u * 5f, sp)
        val top = r1.top
        tp.textSize = u * 3.6f; tp.color = Palette.DIM
        c.drawText("📦 HỘP ĐỒ", cx, top + u * 8f, tp)
        val now = SystemClock.uptimeMillis()
        val wob = sin(now / 300f) * u
        p.color = 0x22FFFFFF; c.drawCircle(cx, top + u * 30f, u * 17f, p)
        Art.item(c, item, cx, top + u * 30f + wob, u * 26f, 255, now / 1000f)
        tp.textSize = u * 6f; tp.color = Palette.GOLD
        c.drawText(Texts.item(item), cx, top + u * 56f, tp)
        tp.textSize = u * 3.4f; tp.color = Palette.TEXT
        wrap(Texts.itemDesc(item), cw - u * 10f).forEachIndexed { i, line -> c.drawText(line, cx, top + u * 63f + i * u * 4.3f, tp) }
        val by = r1.bottom - u * 14f
        offerKeep.set(r1.left + u * 5f, by, cx - u * 2f, by + u * 10f)
        offerDrop.set(cx + u * 2f, by, r1.right - u * 5f, by + u * 10f)
        p.color = Palette.ACCENT; c.drawRoundRect(offerKeep, u * 3f, u * 3f, p)
        p.color = 0xFF3F3558.toInt(); c.drawRoundRect(offerDrop, u * 3f, u * 3f, p)
        tp.textSize = u * 4.2f; tp.color = Palette.TEXT
        c.drawText("Cất vào túi", offerKeep.centerX(), offerKeep.centerY() + u * 1.5f, tp)
        c.drawText("Vứt bỏ", offerDrop.centerX(), offerDrop.centerY() + u * 1.5f, tp)
        tp.textSize = u * 2.8f; tp.color = Palette.DIM
        c.drawText("Đã cất vào túi thì chỉ có thể dùng, không vứt được", cx, r1.bottom + u * 5f, tp)
    }

    private fun drawEmotePicker(c: Canvas) {
        r1.set(emoteRects[0].left - u * 2f, emoteRects[0].top - u * 2f, emoteRects[EMOTES.size - 1].right + u * 2f, emoteRects[EMOTES.size - 1].bottom + u * 2f)
        p.color = 0xF0140B26.toInt(); c.drawRoundRect(r1, u * 3f, u * 3f, p)
        for (i in EMOTES.indices) {
            p.color = 0x33FFFFFF; c.drawRoundRect(emoteRects[i], u * 2f, u * 2f, p)
            tp.textSize = u * 7f; tp.color = 0xFF000000.toInt()
            c.drawText(EMOTES[i], emoteRects[i].centerX(), emoteRects[i].centerY() + u * 2.5f, tp)
        }
    }

    private fun drawGameOver(c: Canvas, v: GameView) {
        p.color = 0xCC05020C.toInt(); c.drawRect(0f, 0f, w, h, p)
        val won = v.winner == v.you
        val title = when {
            won -> "🏆 BẠN THẮNG!"
            v.winner < 0 -> "HÒA • Không ai sống sót"
            else -> "${v.players[v.winner].name} THẮNG"
        }
        tp.textSize = u * 8f; tp.color = if (won) Palette.GOLD else Palette.TEXT
        c.drawText(title, cx, h * 0.3f, tp)
        if (v.winner >= 0) {
            val pv = v.players[v.winner]
            Art.avatar(c, cx, h * 0.42f, u * 12f, Skins.avatar(pv.avatar), 2, 0f)
        }
        tp.textSize = u * 3.6f; tp.color = Palette.DIM
        c.drawText(if (won) "+1 trận thắng • mở khoá skin mới trong Tủ đồ" else "Ván sau sẽ khác!", cx, h * 0.53f, tp)
        val labels = host.gameOverButtons()
        overBtns.clear()
        labels.forEachIndexed { i, s ->
            val r = RectF(u * 15f, h * 0.6f + i * u * 15f, w - u * 15f, h * 0.6f + i * u * 15f + u * 11f)
            overBtns.add(r)
            p.color = if (i == 0) Palette.ACCENT else 0xFF3F3558.toInt()
            c.drawRoundRect(r, u * 3.5f, u * 3.5f, p)
            tp.textSize = u * 4.6f; tp.color = Palette.TEXT
            c.drawText(s, r.centerX(), r.centerY() + u * 1.6f, tp)
        }
    }

    // ------------------------------------------------------------------ input

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action != MotionEvent.ACTION_UP) return true
        val x = ev.x
        val y = ev.y
        val v = view ?: return true
        Audio.play(Sfx.CARD, 0.25f)
        if (v.phase == Phase.GAME_OVER && idle()) {
            overBtns.forEachIndexed { i, r -> if (r.contains(x, y)) host.onGameOverButton(i) }
            return true
        }
        if (cur?.type == Ev.ROUND_START) return true
        if (emotePicker) {
            emoteRects.forEachIndexed { i, r -> if (r.contains(x, y)) host.sendEmote(i) }
            emotePicker = false
            return true
        }
        if (menuBtn.contains(x, y)) { host.openMenu(); return true }
        if (emoteBtn.contains(x, y)) { emotePicker = true; return true }
        if (v.phase == Phase.OFFERS && v.offer != null && idle()) {
            if (offerKeep.contains(x, y)) { send(Action.ResolveOffer(true)); Audio.play(Sfx.PICK) }
            else if (offerDrop.contains(x, y)) { send(Action.ResolveOffer(false)); Audio.play(Sfx.WHOOSH, 0.5f) }
            return true
        }
        val me = v.players[v.you]
        // item slots
        for (i in 0 until SLOTS) if (slotRects[i].contains(x, y)) {
            pickingBombTarget = false
            selectedSlot = if (selectedSlot == i || me.items.getOrNull(i) == null) -1 else i
            if (selectedSlot >= 0) selectedTarget = -1
            return true
        }
        if (actionBtn.contains(x, y) && myTurn()) {
            if (selectedSlot >= 0) {
                val item = me.items.getOrNull(selectedSlot) ?: return true
                if (pickingBombTarget) { pickingBombTarget = false; return true }
                if (clientBlocker(v, item) != null) return true
                if (item.needsTarget) {
                    pickingBombTarget = true
                } else {
                    send(Action.UseItem(selectedSlot))
                    selectedSlot = -1
                }
            } else if (selectedTarget >= 0) {
                send(Action.Shoot(selectedTarget))
                selectedTarget = -1
            }
            return true
        }
        // players
        val hitId = v.players.filter { it.alive }.minByOrNull { pv ->
            val (px, py) = posOf(pv.id); hypot(px - x, py - y)
        }?.takeIf { pv ->
            val (px, py) = posOf(pv.id)
            hypot(px - x, py - y) < avatarR(hexIndexOf(pv, v)) * 1.6f
        }?.id
        if (hitId != null && myTurn()) {
            if (pickingBombTarget) {
                if (hitId != v.you) {
                    send(Action.UseItem(selectedSlot, hitId))
                    pickingBombTarget = false
                    selectedSlot = -1
                }
                return true
            }
            selectedSlot = -1
            if (selectedTarget == hitId) {
                send(Action.Shoot(hitId))
                selectedTarget = -1
            } else {
                selectedTarget = hitId
                Audio.play(Sfx.CLICK, 0.3f, 1.6f)
            }
            return true
        }
        selectedTarget = -1
        if (!pickingBombTarget) selectedSlot = -1
        return true
    }

    private fun send(a: Action) {
        waitingReply = SystemClock.uptimeMillis() + 2500
        host.sendAction(a)
    }
}
