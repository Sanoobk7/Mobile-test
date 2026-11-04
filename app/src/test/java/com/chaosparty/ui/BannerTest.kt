package com.chaosparty.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

/** Renders the README banner and a menu screenshot. Run with -Pshots=dir. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class BannerTest {
    private val out = System.getProperty("shots")?.takeIf { it.isNotEmpty() }?.let { File(it) }

    private fun save(bmp: Bitmap, name: String) {
        out!!.mkdirs()
        File(out, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 95, it) }
    }

    @Test
    fun banner() {
        if (out == null) return
        Prefs.init(RuntimeEnvironment.getApplication())
        val w = 1600; val h = 640
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFF2A1650.toInt(), 0xFF0B0616.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        val tx = w * 0.72f; val ty = h * 0.5f
        p.shader = RadialGradient(tx, ty, 460f, intArrayOf(0x66FFD9A0, 0x00FFD9A0), null, Shader.TileMode.CLAMP)
        c.drawCircle(tx, ty, 460f, p)
        p.shader = null
        fun hex(cx: Float, cy: Float, rx: Float, ry: Float): Path = Path().apply {
            for (k in 0 until 6) {
                val a = Math.toRadians(90.0 + 60.0 * k)
                val x = cx + (cos(a) * rx).toFloat(); val y = cy + (sin(a) * ry).toFloat()
                if (k == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        val rx = 290f; val ry = 215f
        p.color = 0xFF7A4A24.toInt(); c.drawPath(hex(tx, ty, rx * 1.1f, ry * 1.1f), p)
        p.shader = RadialGradient(tx, ty - 60, rx, 0xFF1F7A4D.toInt(), 0xFF135235.toInt(), Shader.TileMode.CLAMP)
        c.drawPath(hex(tx, ty, rx * 0.97f, ry * 0.97f), p)
        p.shader = null
        Art.gun(c, tx, ty, 200f, -28f, Skins.guns[1])
        val moods = intArrayOf(2, 0, 1, 2, 0, 2)
        for (k in 0 until 6) {
            val a = Math.toRadians(90.0 + 60.0 * k)
            Art.avatar(c, tx + (cos(a) * rx).toFloat(), ty + (sin(a) * ry).toFloat(), if (k == 0) 62f else 52f,
                Skins.avatars[(k * 3) % Skins.avatars.size], moods[k])
        }
        // shells
        Art.shellIcon(c, 120f, 470f, 40f, 76f, com.chaosparty.engine.Shell.RED)
        Art.shellIcon(c, 180f, 470f, 40f, 76f, com.chaosparty.engine.Shell.BLACK)
        Art.shellIcon(c, 240f, 470f, 40f, 76f, com.chaosparty.engine.Shell.PINK)
        Art.shellIcon(c, 300f, 470f, 40f, 76f, null, 255, 0.3f)
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
        tp.textSize = 128f; tp.color = 0xFF000000.toInt(); tp.alpha = 140
        c.drawText("CHAOS", 126f, 236f, tp); c.drawText("PARTY", 126f, 366f, tp)
        tp.alpha = 255; tp.color = Palette.GOLD
        c.drawText("CHAOS", 116f, 226f, tp)
        tp.color = Palette.ACCENT
        c.drawText("PARTY", 116f, 356f, tp)
        tp.textSize = 36f; tp.color = Palette.TEXT
        c.drawText("Cò quay hỗn loạn • 2–6 người • LAN", 120f, 425f, tp)
        save(bmp, "banner.png")
    }

    @Test
    fun menu() {
        if (out == null) return
        val app = RuntimeEnvironment.getApplication()
        Prefs.init(app)
        Prefs.seenRules = true; Prefs.name = "Sanoob"; Prefs.sfx = false; Prefs.music = false
        val act = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val v = act.window.decorView
        v.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, 1080, 2340)
        val bmp = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        save(bmp, "menu.png")
    }
}
