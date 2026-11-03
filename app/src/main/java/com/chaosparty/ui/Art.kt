package com.chaosparty.ui

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import com.chaosparty.engine.Item
import com.chaosparty.engine.RoundEvent
import com.chaosparty.engine.Shell
import kotlin.math.cos
import kotlin.math.sin

/** Item names and descriptions shown in the UI. */
object Texts {
    fun item(i: Item) = when (i) {
        Item.SYRINGE -> "Kim tiêm"
        Item.CHISEL -> "Cái đục"
        Item.BOMB -> "Bom"
        Item.LIVE_BOMB -> "Bom đang chạy"
        Item.ARMOR -> "Giáp"
        Item.MAGNIFIER -> "Kính lúp"
        Item.UNO -> "Uno đảo chỗ"
        Item.ADRENALINE -> "Adrenaline"
        Item.GLUE -> "Keo mờ ám"
    }

    fun itemDesc(i: Item) = when (i) {
        Item.SYRINGE -> "Hồi 1 máu. 25% bị sốc thuốc: mất 2 máu."
        Item.CHISEL -> "Viên hiện tại gây ×2 sát thương. Nếu là đạn đỏ: 50% nổ ngòi, bạn mất 2 máu và hết lượt."
        Item.BOMB -> "Đặt vào ô trống của người khác. Nổ ngẫu nhiên sau 3–8 lượt, gây 3 sát thương. Họ có thể đặt ngược lại!"
        Item.LIVE_BOMB -> "Bom đang đếm ngược trong túi bạn! Dùng để chuyển sang ô trống của người khác."
        Item.ARMOR -> "+1 giáp (tối đa 3). Mỗi giáp đỡ 1 sát thương."
        Item.MAGNIFIER -> "Nhìn lén viên đạn hiện tại. Chỉ mình bạn thấy."
        Item.UNO -> "Đổi chỗ ngồi ngẫu nhiên của tất cả người chơi."
        Item.ADRENALINE -> "Hồi đầy máu ngay, đạn bạn bắn ×2 sát thương. Bạn sẽ chết sau 2 round."
        Item.GLUE -> "Bôi keo bí mật: sau phát bắn của bạn, viên tiếp theo dội ngược 100% vào người bắn. Chỉ bạn biết."
    }

    fun event(e: RoundEvent) = when (e) {
        RoundEvent.NORMAL -> "Bình thường"
        RoundEvent.COLORBLIND -> "Mù màu"
        RoundEvent.MADNESS -> "Điên loạn"
        RoundEvent.ROTATION -> "Xoay vòng"
        RoundEvent.WEREWOLF -> "Ma sói"
    }

    fun eventDesc(e: RoundEvent) = when (e) {
        RoundEvent.NORMAL -> "Không có gì đặc biệt... hoặc là có?"
        RoundEvent.COLORBLIND -> "Mọi viên đạn đều màu cầu vồng. Phó mặc cho vận may!"
        RoundEvent.MADNESS -> "Súng run tay: 40% bắn trúng người ngẫu nhiên, kể cả bạn."
        RoundEvent.ROTATION -> "Số lượt = số người còn sống. Ai bị bắn sẽ cầm súng lượt sau."
        RoundEvent.WEREWOLF -> "Mỗi phát trúng ai là +1 phiếu cho người đó. Hết round, ai nhiều phiếu nhất sẽ chết!"
    }

    fun shell(s: Shell) = when (s) {
        Shell.RED -> "ĐỎ"
        Shell.BLACK -> "ĐEN"
        Shell.PINK -> "HỒNG"
    }
}

object Palette {
    const val RED = 0xFFFF3B4E.toInt()
    const val BLACK = 0xFF2B2B38.toInt()
    const val PINK = 0xFFFF8AD8.toInt()
    const val GOLD = 0xFFFFD23F.toInt()
    const val TEXT = 0xFFF5F0FF.toInt()
    const val DIM = 0xFFA79BC4.toInt()
    const val PANEL = 0xE61A1030.toInt()
    const val ACCENT = 0xFFFF3D7F.toInt()
    const val GREEN = 0xFF4ADE80.toInt()
    const val CYAN = 0xFF22D3EE.toInt()
    val RAINBOW = intArrayOf(0xFFFF4D4D.toInt(), 0xFFFFB84D.toInt(), 0xFFFFF04D.toInt(), 0xFF4DFF88.toInt(), 0xFF4DB8FF.toInt(), 0xFFB84DFF.toInt())

    fun shell(s: Shell) = when (s) {
        Shell.RED -> RED
        Shell.BLACK -> BLACK
        Shell.PINK -> PINK
    }

    fun event(e: RoundEvent) = when (e) {
        RoundEvent.NORMAL -> 0xFF64748B.toInt()
        RoundEvent.COLORBLIND -> 0xFF22C55E.toInt()
        RoundEvent.MADNESS -> 0xFFEF4444.toInt()
        RoundEvent.ROTATION -> 0xFF3B82F6.toInt()
        RoundEvent.WEREWOLF -> 0xFF7C3AED.toInt()
    }
}

/** Stateless drawing helpers. Paints and paths are reused to avoid allocations per frame. */
object Art {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val path = Path()
    private val rect = RectF()

    private fun f(color: Int, alpha: Int = 255): Paint = fill.apply { this.color = color; this.alpha = alpha * Color8.a(color) / 255; shader = null }
    private fun s(color: Int, w: Float, alpha: Int = 255): Paint = stroke.apply { this.color = color; strokeWidth = w; this.alpha = alpha * Color8.a(color) / 255; shader = null }

    /**
     * Round blob character. [mood]: 0 normal, 1 hurt, 2 happy, 3 dead. [blink] in 0..1 closes the eyes.
     */
    fun avatar(c: Canvas, cx: Float, cy: Float, r: Float, skin: AvatarSkin, mood: Int, blink: Float = 0f, alpha: Int = 255, lookX: Float = 0f) {
        val body = if (mood == 3) 0xFF6B6680.toInt() else skin.body
        // shadow
        c.drawOval(cx - r * 0.9f, cy + r * 0.75f, cx + r * 0.9f, cy + r * 1.05f, f(0xFF000000.toInt(), alpha / 3))
        // body
        c.drawCircle(cx, cy, r, f(body, alpha))
        c.drawCircle(cx - r * 0.3f, cy - r * 0.35f, r * 0.28f, f(0xFFFFFFFF.toInt(), alpha / 5))
        c.drawCircle(cx, cy, r, s(0xFF1A1028.toInt(), r * 0.08f, alpha))
        // eyes
        val ex = r * 0.34f
        val ey = cy - r * 0.08f
        val lx = lookX * r * 0.08f
        when (mood) {
            3 -> {
                val k = r * 0.13f
                for (sx in floatArrayOf(-ex, ex)) {
                    c.drawLine(cx + sx - k, ey - k, cx + sx + k, ey + k, s(0xFF1A1028.toInt(), r * 0.09f, alpha))
                    c.drawLine(cx + sx + k, ey - k, cx + sx - k, ey + k, s(0xFF1A1028.toInt(), r * 0.09f, alpha))
                }
            }
            2 -> for (sx in floatArrayOf(-ex, ex)) {
                rect.set(cx + sx - r * 0.13f, ey - r * 0.1f, cx + sx + r * 0.13f, ey + r * 0.14f)
                c.drawArc(rect, 200f, 140f, false, s(0xFF1A1028.toInt(), r * 0.08f, alpha))
            }
            else -> for (sx in floatArrayOf(-ex, ex)) {
                val h = r * 0.16f * (1f - blink * 0.9f)
                c.drawOval(cx + sx - r * 0.12f + lx, ey - h, cx + sx + r * 0.12f + lx, ey + h, f(0xFFFFFFFF.toInt(), alpha))
                if (blink < 0.7f) c.drawCircle(cx + sx + lx * 1.6f, ey + r * 0.02f, r * 0.065f, f(0xFF1A1028.toInt(), alpha))
            }
        }
        // mouth
        val my = cy + r * 0.38f
        when (mood) {
            1 -> {
                rect.set(cx - r * 0.18f, my - r * 0.05f, cx + r * 0.18f, my + r * 0.2f)
                c.drawOval(rect, f(0xFF3B0A1A.toInt(), alpha))
            }
            2 -> {
                rect.set(cx - r * 0.25f, my - r * 0.22f, cx + r * 0.25f, my + r * 0.16f)
                c.drawArc(rect, 0f, 180f, true, f(0xFF3B0A1A.toInt(), alpha))
            }
            3 -> c.drawLine(cx - r * 0.18f, my, cx + r * 0.18f, my, s(0xFF1A1028.toInt(), r * 0.07f, alpha))
            else -> {
                rect.set(cx - r * 0.2f, my - r * 0.15f, cx + r * 0.2f, my + r * 0.08f)
                c.drawArc(rect, 20f, 140f, false, s(0xFF1A1028.toInt(), r * 0.07f, alpha))
            }
        }
        // cheeks
        if (mood != 3) {
            c.drawCircle(cx - r * 0.55f, cy + r * 0.2f, r * 0.11f, f(0xFFFF6B8B.toInt(), alpha / 3))
            c.drawCircle(cx + r * 0.55f, cy + r * 0.2f, r * 0.11f, f(0xFFFF6B8B.toInt(), alpha / 3))
        }
        accessory(c, cx, cy, r, skin.accessory, alpha, ey)
    }

    private fun accessory(c: Canvas, cx: Float, cy: Float, r: Float, kind: Int, alpha: Int, ey: Float) {
        val top = cy - r
        val ink = 0xFF1A1028.toInt()
        when (kind) {
            1 -> { // cap
                rect.set(cx - r * 0.75f, top - r * 0.15f, cx + r * 0.75f, top + r * 0.55f)
                c.drawArc(rect, 180f, 180f, true, f(0xFF2563EB.toInt(), alpha))
                rect.set(cx - r * 0.1f, top + r * 0.12f, cx + r * 1.05f, top + r * 0.32f)
                c.drawRoundRect(rect, r * 0.1f, r * 0.1f, f(0xFF1D4ED8.toInt(), alpha))
            }
            2 -> { // party hat
                path.reset(); path.moveTo(cx - r * 0.42f, top + r * 0.2f); path.lineTo(cx + r * 0.42f, top + r * 0.2f); path.lineTo(cx + r * 0.05f, top - r * 0.85f); path.close()
                c.drawPath(path, f(0xFFA855F7.toInt(), alpha))
                c.drawLine(cx - r * 0.25f, top - r * 0.1f, cx + r * 0.3f, top + r * 0.05f, s(Palette.GOLD, r * 0.08f, alpha))
                c.drawCircle(cx + r * 0.05f, top - r * 0.88f, r * 0.13f, f(Palette.GOLD, alpha))
            }
            3 -> { // sunglasses
                for (sx in floatArrayOf(-0.34f, 0.34f)) {
                    rect.set(cx + sx * r - r * 0.24f, ey - r * 0.15f, cx + sx * r + r * 0.24f, ey + r * 0.15f)
                    c.drawRoundRect(rect, r * 0.08f, r * 0.08f, f(ink, alpha))
                }
                c.drawLine(cx - r * 0.12f, ey - r * 0.05f, cx + r * 0.12f, ey - r * 0.05f, s(ink, r * 0.06f, alpha))
                c.drawLine(cx - r * 0.4f, ey - r * 0.08f, cx - r * 0.25f, ey - r * 0.02f, s(0xFFFFFFFF.toInt(), r * 0.04f, alpha / 2))
            }
            4 -> { // headband
                rect.set(cx - r * 0.95f, top + r * 0.22f, cx + r * 0.95f, top + r * 0.42f)
                c.drawRoundRect(rect, r * 0.08f, r * 0.08f, f(0xFFEF4444.toInt(), alpha))
                c.drawLine(cx + r * 0.85f, top + r * 0.32f, cx + r * 1.25f, top + r * 0.55f, s(0xFFEF4444.toInt(), r * 0.12f, alpha))
                c.drawLine(cx + r * 0.85f, top + r * 0.32f, cx + r * 1.2f, top + r * 0.15f, s(0xFFEF4444.toInt(), r * 0.12f, alpha))
            }
            5 -> { // crown
                path.reset()
                path.moveTo(cx - r * 0.5f, top + r * 0.15f); path.lineTo(cx - r * 0.55f, top - r * 0.4f); path.lineTo(cx - r * 0.25f, top - r * 0.12f)
                path.lineTo(cx, top - r * 0.5f); path.lineTo(cx + r * 0.25f, top - r * 0.12f); path.lineTo(cx + r * 0.55f, top - r * 0.4f)
                path.lineTo(cx + r * 0.5f, top + r * 0.15f); path.close()
                c.drawPath(path, f(Palette.GOLD, alpha))
                c.drawPath(path, s(0xFFB8860B.toInt(), r * 0.05f, alpha))
                c.drawCircle(cx, top - r * 0.05f, r * 0.08f, f(0xFFEF4444.toInt(), alpha))
            }
            6 -> { // horns
                for (sgn in floatArrayOf(-1f, 1f)) {
                    path.reset()
                    path.moveTo(cx + sgn * r * 0.35f, top + r * 0.25f)
                    path.quadTo(cx + sgn * r * 0.75f, top - r * 0.05f, cx + sgn * r * 0.55f, top - r * 0.55f)
                    path.quadTo(cx + sgn * r * 0.5f, top, cx + sgn * r * 0.62f, top + r * 0.35f)
                    path.close()
                    c.drawPath(path, f(0xFF7F1D1D.toInt(), alpha))
                }
            }
            7 -> { // halo
                rect.set(cx - r * 0.55f, top - r * 0.5f, cx + r * 0.55f, top - r * 0.2f)
                c.drawOval(rect, s(Palette.GOLD, r * 0.1f, alpha))
            }
            8 -> { // top hat
                rect.set(cx - r * 0.75f, top + r * 0.05f, cx + r * 0.75f, top + r * 0.22f)
                c.drawRoundRect(rect, r * 0.05f, r * 0.05f, f(ink, alpha))
                rect.set(cx - r * 0.45f, top - r * 0.75f, cx + r * 0.45f, top + r * 0.1f)
                c.drawRect(rect, f(ink, alpha))
                rect.set(cx - r * 0.45f, top - r * 0.1f, cx + r * 0.45f, top + r * 0.02f)
                c.drawRect(rect, f(0xFFDC2626.toInt(), alpha))
            }
            9 -> { // flower
                val fx = cx + r * 0.55f
                val fy = top + r * 0.2f
                for (k in 0 until 5) {
                    val a = k * 72.0 * Math.PI / 180
                    c.drawCircle(fx + (cos(a) * r * 0.17f).toFloat(), fy + (sin(a) * r * 0.17f).toFloat(), r * 0.13f, f(0xFFF472B6.toInt(), alpha))
                }
                c.drawCircle(fx, fy, r * 0.1f, f(Palette.GOLD, alpha))
            }
        }
    }

    /** A chunky cartoon revolver, centered at its cylinder, pointing along +x before rotation. */
    fun gun(c: Canvas, x: Float, y: Float, len: Float, angleDeg: Float, skin: GunSkin, recoil: Float = 0f, glow: Int = 0, glowAlpha: Int = 0) {
        c.save()
        c.translate(x, y)
        c.rotate(angleDeg)
        c.translate(-recoil * len * 0.12f, 0f)
        // Flip so the grip is always below the barrel on screen.
        val a = ((angleDeg % 360) + 360) % 360
        if (a > 90 && a < 270) c.scale(1f, -1f)
        val h = len * 0.16f
        val ink = 0xFF120A1E.toInt()
        if (glowAlpha > 0) {
            c.drawCircle(len * 0.1f, 0f, len * 0.55f, f(glow, glowAlpha / 3))
        }
        // barrel
        rect.set(-len * 0.05f, -h * 0.42f, len * 0.62f, h * 0.32f)
        c.drawRoundRect(rect, h * 0.2f, h * 0.2f, f(skin.body))
        c.drawRoundRect(rect, h * 0.2f, h * 0.2f, s(ink, len * 0.018f))
        c.drawLine(len * 0.02f, -h * 0.18f, len * 0.55f, -h * 0.18f, s(0xFFFFFFFF.toInt(), len * 0.012f, 90))
        // front sight
        rect.set(len * 0.52f, -h * 0.62f, len * 0.58f, -h * 0.4f)
        c.drawRect(rect, f(skin.accent))
        // frame
        rect.set(-len * 0.32f, -h * 0.55f, len * 0.1f, h * 0.5f)
        c.drawRoundRect(rect, h * 0.25f, h * 0.25f, f(skin.accent))
        c.drawRoundRect(rect, h * 0.25f, h * 0.25f, s(ink, len * 0.018f))
        // cylinder
        rect.set(-len * 0.2f, -h * 0.62f, len * 0.12f, h * 0.58f)
        c.drawRoundRect(rect, h * 0.35f, h * 0.35f, f(skin.body))
        c.drawRoundRect(rect, h * 0.35f, h * 0.35f, s(ink, len * 0.018f))
        for (k in 0 until 3) {
            val lx = -len * 0.14f + k * len * 0.09f
            c.drawLine(lx, -h * 0.45f, lx, h * 0.42f, s(ink, len * 0.01f, 120))
        }
        // hammer
        path.reset(); path.moveTo(-len * 0.3f, -h * 0.5f); path.lineTo(-len * 0.42f, -h * 0.85f); path.lineTo(-len * 0.34f, -h * 0.9f); path.lineTo(-len * 0.22f, -h * 0.55f); path.close()
        c.drawPath(path, f(skin.accent)); c.drawPath(path, s(ink, len * 0.014f))
        // grip
        path.reset()
        path.moveTo(-len * 0.3f, h * 0.2f)
        path.lineTo(-len * 0.08f, h * 0.35f)
        path.lineTo(-len * 0.2f, h * 1.6f)
        path.quadTo(-len * 0.34f, h * 1.75f, -len * 0.44f, h * 1.5f)
        path.close()
        c.drawPath(path, f(skin.grip)); c.drawPath(path, s(ink, len * 0.018f))
        // trigger guard + trigger
        rect.set(-len * 0.12f, h * 0.2f, len * 0.08f, h * 0.95f)
        c.drawArc(rect, 0f, 180f, false, s(ink, len * 0.02f))
        c.drawLine(-len * 0.03f, h * 0.35f, -len * 0.05f, h * 0.7f, s(ink, len * 0.018f))
        c.restore()
    }

    fun muzzle(c: Canvas, x: Float, y: Float, len: Float, angleDeg: Float, color: Int, t: Float) {
        if (t <= 0f || t >= 1f) return
        c.save()
        c.translate(x, y)
        c.rotate(angleDeg)
        val k = 1f - t
        val size = len * 0.45f * (0.6f + t)
        path.reset()
        path.moveTo(len * 0.62f, 0f)
        for (i in 0 until 10) {
            val ang = (i * 36.0 - 90) * Math.PI / 180
            val rr = if (i % 2 == 0) size else size * 0.45f
            path.lineTo(len * 0.62f + size * 0.6f + (cos(ang) * rr).toFloat(), (sin(ang) * rr * 0.7f).toFloat())
        }
        path.close()
        c.drawPath(path, f(color, (255 * k).toInt()))
        c.drawCircle(len * 0.62f + size * 0.6f, 0f, size * 0.3f, f(0xFFFFFFFF.toInt(), (230 * k).toInt()))
        c.restore()
    }

    fun shellIcon(c: Canvas, x: Float, y: Float, w: Float, h: Float, shell: Shell?, alpha: Int = 255, rainbowPhase: Float = 0f) {
        rect.set(x, y + h * 0.18f, x + w, y + h)
        if (shell == null) {
            fill.shader = LinearGradient(x, y, x + w, y + h, Palette.RAINBOW.map { it }.toIntArray().let { arr ->
                IntArray(arr.size) { arr[(it + (rainbowPhase * arr.size).toInt()) % arr.size] }
            }, null, Shader.TileMode.CLAMP)
            fill.alpha = alpha
            c.drawRoundRect(rect, w * 0.3f, w * 0.3f, fill)
            fill.shader = null
        } else {
            c.drawRoundRect(rect, w * 0.3f, w * 0.3f, f(Palette.shell(shell), alpha))
        }
        rect.set(x + w * 0.12f, y, x + w * 0.88f, y + h * 0.3f)
        c.drawRoundRect(rect, w * 0.3f, w * 0.3f, f(0xFFE8C35A.toInt(), alpha))
        rect.set(x, y + h * 0.18f, x + w, y + h)
        c.drawRoundRect(rect, w * 0.3f, w * 0.3f, s(if (shell == Shell.BLACK) 0xFFB8B8C8.toInt() else 0xFF0A0612.toInt(), w * 0.1f, alpha))
        c.drawLine(x + w * 0.28f, y + h * 0.35f, x + w * 0.28f, y + h * 0.85f, s(0xFFFFFFFF.toInt(), w * 0.12f, alpha / 3))
    }

    fun heart(c: Canvas, cx: Float, cy: Float, size: Float, full: Boolean, color: Int = Palette.RED) {
        path.reset()
        val s2 = size / 2
        path.moveTo(cx, cy + s2 * 0.9f)
        path.cubicTo(cx - size * 0.9f, cy + s2 * 0.1f, cx - s2 * 0.9f, cy - s2 * 1.1f, cx, cy - s2 * 0.35f)
        path.cubicTo(cx + s2 * 0.9f, cy - s2 * 1.1f, cx + size * 0.9f, cy + s2 * 0.1f, cx, cy + s2 * 0.9f)
        path.close()
        if (full) c.drawPath(path, f(color)) else c.drawPath(path, f(0xFF3A2D52.toInt()))
        c.drawPath(path, s(0xFF0A0612.toInt(), size * 0.12f))
    }

    fun shield(c: Canvas, cx: Float, cy: Float, size: Float) {
        path.reset()
        path.moveTo(cx, cy - size * 0.55f)
        path.lineTo(cx + size * 0.45f, cy - size * 0.35f)
        path.quadTo(cx + size * 0.45f, cy + size * 0.3f, cx, cy + size * 0.6f)
        path.quadTo(cx - size * 0.45f, cy + size * 0.3f, cx - size * 0.45f, cy - size * 0.35f)
        path.close()
        c.drawPath(path, f(0xFF60A5FA.toInt()))
        c.drawPath(path, s(0xFF0A0612.toInt(), size * 0.12f))
    }

    /** Icon for an item, fitting in a square of [size] centered at (cx, cy). */
    fun item(c: Canvas, item: Item, cx: Float, cy: Float, size: Float, alpha: Int = 255, t: Float = 0f) {
        val u = size / 2
        val ink = 0xFF120A1E.toInt()
        val lw = size * 0.06f
        when (item) {
            Item.SYRINGE -> {
                c.save(); c.rotate(-40f, cx, cy)
                rect.set(cx - u * 0.75f, cy - u * 0.22f, cx + u * 0.35f, cy + u * 0.22f)
                c.drawRoundRect(rect, u * 0.08f, u * 0.08f, f(0xFFE0F2FE.toInt(), alpha))
                rect.set(cx - u * 0.6f, cy - u * 0.14f, cx + u * 0.05f, cy + u * 0.14f)
                c.drawRect(rect, f(0xFF4ADE80.toInt(), alpha))
                rect.set(cx - u * 0.75f, cy - u * 0.22f, cx + u * 0.35f, cy + u * 0.22f)
                c.drawRoundRect(rect, u * 0.08f, u * 0.08f, s(ink, lw, alpha))
                c.drawLine(cx + u * 0.35f, cy, cx + u * 0.9f, cy, s(0xFFCBD5E1.toInt(), lw * 0.8f, alpha))
                c.drawLine(cx - u * 0.75f, cy, cx - u * 0.95f, cy, s(ink, lw * 1.4f, alpha))
                c.drawLine(cx - u * 0.95f, cy - u * 0.3f, cx - u * 0.95f, cy + u * 0.3f, s(ink, lw * 1.4f, alpha))
                c.restore()
            }
            Item.CHISEL -> {
                c.save(); c.rotate(45f, cx, cy)
                rect.set(cx - u * 0.85f, cy - u * 0.18f, cx - u * 0.05f, cy + u * 0.18f)
                c.drawRoundRect(rect, u * 0.1f, u * 0.1f, f(0xFFF59E0B.toInt(), alpha))
                c.drawRoundRect(rect, u * 0.1f, u * 0.1f, s(ink, lw, alpha))
                path.reset(); path.moveTo(cx - u * 0.05f, cy - u * 0.12f); path.lineTo(cx + u * 0.7f, cy - u * 0.12f); path.lineTo(cx + u * 0.9f, cy); path.lineTo(cx + u * 0.7f, cy + u * 0.12f); path.lineTo(cx - u * 0.05f, cy + u * 0.12f); path.close()
                c.drawPath(path, f(0xFFCBD5E1.toInt(), alpha)); c.drawPath(path, s(ink, lw, alpha))
                c.restore()
                c.drawText("×2", cx + u * 0.35f, cy + u * 0.85f, label(size * 0.3f, Palette.GOLD, alpha))
            }
            Item.BOMB, Item.LIVE_BOMB -> {
                val live = item == Item.LIVE_BOMB
                c.drawCircle(cx - u * 0.08f, cy + u * 0.12f, u * 0.6f, f(0xFF1F1B2E.toInt(), alpha))
                c.drawCircle(cx - u * 0.08f, cy + u * 0.12f, u * 0.6f, s(ink, lw, alpha))
                c.drawCircle(cx - u * 0.3f, cy - u * 0.1f, u * 0.14f, f(0xFFFFFFFF.toInt(), alpha / 3))
                rect.set(cx + u * 0.12f, cy - u * 0.62f, cx + u * 0.42f, cy - u * 0.38f)
                c.drawRect(rect, f(0xFF6B7280.toInt(), alpha))
                c.drawLine(cx + u * 0.3f, cy - u * 0.6f, cx + u * 0.55f, cy - u * 0.85f, s(0xFFD6B36A.toInt(), lw, alpha))
                val spark = if (live) (0.6f + 0.4f * sin(t * 30f)) else 0.7f
                c.drawCircle(cx + u * 0.6f, cy - u * 0.9f, u * 0.18f * spark, f(if (live) 0xFFFF5A1F.toInt() else Palette.GOLD, alpha))
                if (live) c.drawCircle(cx - u * 0.08f, cy + u * 0.12f, u * 0.72f, s(0xFFFF3B4E.toInt(), lw, (alpha * (0.4f + 0.4f * sin(t * 8f))).toInt().coerceIn(0, 255)))
            }
            Item.ARMOR -> shieldIcon(c, cx, cy, u, alpha)
            Item.MAGNIFIER -> {
                c.drawCircle(cx - u * 0.15f, cy - u * 0.15f, u * 0.45f, f(0xFFBAE6FD.toInt(), alpha * 2 / 3))
                c.drawCircle(cx - u * 0.15f, cy - u * 0.15f, u * 0.45f, s(ink, lw * 1.6f, alpha))
                c.drawCircle(cx - u * 0.15f, cy - u * 0.15f, u * 0.45f, s(0xFFCBD5E1.toInt(), lw * 0.8f, alpha))
                c.drawLine(cx + u * 0.18f, cy + u * 0.18f, cx + u * 0.75f, cy + u * 0.75f, s(0xFF8B5A2B.toInt(), lw * 2.4f, alpha))
            }
            Item.UNO -> {
                c.save(); c.rotate(-12f, cx, cy)
                rect.set(cx - u * 0.5f, cy - u * 0.75f, cx + u * 0.5f, cy + u * 0.75f)
                c.drawRoundRect(rect, u * 0.15f, u * 0.15f, f(0xFFFFFFFF.toInt(), alpha))
                rect.inset(u * 0.08f, u * 0.08f)
                c.drawRoundRect(rect, u * 0.12f, u * 0.12f, f(0xFFEF4444.toInt(), alpha))
                // two curved arrows
                rect.set(cx - u * 0.3f, cy - u * 0.35f, cx + u * 0.3f, cy + u * 0.35f)
                c.drawArc(rect, 200f, 140f, false, s(0xFFFFFFFF.toInt(), lw * 1.3f, alpha))
                c.drawArc(rect, 20f, 140f, false, s(0xFFFFFFFF.toInt(), lw * 1.3f, alpha))
                c.restore()
            }
            Item.ADRENALINE -> {
                path.reset()
                path.moveTo(cx + u * 0.15f, cy - u * 0.85f); path.lineTo(cx - u * 0.45f, cy + u * 0.1f); path.lineTo(cx - u * 0.02f, cy + u * 0.1f)
                path.lineTo(cx - u * 0.2f, cy + u * 0.85f); path.lineTo(cx + u * 0.5f, cy - u * 0.15f); path.lineTo(cx + u * 0.05f, cy - u * 0.15f); path.close()
                c.drawPath(path, f(0xFFFACC15.toInt(), alpha)); c.drawPath(path, s(ink, lw, alpha))
            }
            Item.GLUE -> {
                rect.set(cx - u * 0.42f, cy - u * 0.35f, cx + u * 0.42f, cy + u * 0.8f)
                c.drawRoundRect(rect, u * 0.2f, u * 0.2f, f(0xFFF8FAFC.toInt(), alpha))
                c.drawRoundRect(rect, u * 0.2f, u * 0.2f, s(ink, lw, alpha))
                rect.set(cx - u * 0.42f, cy + u * 0.05f, cx + u * 0.42f, cy + u * 0.45f)
                c.drawRect(rect, f(0xFF8B5CF6.toInt(), alpha))
                path.reset(); path.moveTo(cx - u * 0.18f, cy - u * 0.35f); path.lineTo(cx, cy - u * 0.8f); path.lineTo(cx + u * 0.18f, cy - u * 0.35f); path.close()
                c.drawPath(path, f(0xFFF97316.toInt(), alpha))
                c.drawCircle(cx + u * 0.05f, cy - u * 0.9f, u * 0.08f, f(0xFFE2E8F0.toInt(), alpha))
            }
        }
    }

    private fun shieldIcon(c: Canvas, cx: Float, cy: Float, u: Float, alpha: Int) {
        path.reset()
        path.moveTo(cx, cy - u * 0.85f)
        path.lineTo(cx + u * 0.7f, cy - u * 0.55f)
        path.quadTo(cx + u * 0.7f, cy + u * 0.45f, cx, cy + u * 0.9f)
        path.quadTo(cx - u * 0.7f, cy + u * 0.45f, cx - u * 0.7f, cy - u * 0.55f)
        path.close()
        c.drawPath(path, f(0xFF3B82F6.toInt(), alpha))
        c.drawPath(path, s(0xFF120A1E.toInt(), u * 0.12f, alpha))
        c.drawLine(cx, cy - u * 0.6f, cx, cy + u * 0.6f, s(0xFFBFDBFE.toInt(), u * 0.1f, alpha))
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
    }

    private fun label(size: Float, color: Int, alpha: Int): Paint = labelPaint.apply {
        textSize = size; this.color = color; this.alpha = alpha
    }
}

object Color8 {
    fun a(c: Int) = (c ushr 24) and 0xFF
}
