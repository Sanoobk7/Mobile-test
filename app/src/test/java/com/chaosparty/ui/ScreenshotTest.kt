package com.chaosparty.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.View
import org.robolectric.RuntimeEnvironment
import com.chaosparty.engine.Action
import com.chaosparty.engine.ChaosEngine
import com.chaosparty.engine.Phase
import com.chaosparty.engine.PlayerInit
import com.chaosparty.engine.Step
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSystemClock
import java.io.File
import java.time.Duration
import kotlin.random.Random

/** Renders game scenes to PNG files so the layout can be checked without a device. Run with -Dshots=dir. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    private val out = System.getProperty("shots")?.takeIf { it.isNotEmpty() }?.let { File(it) }

    private val host = object : GameScreenHost {
        val sent = ArrayList<Action>()
        override fun sendAction(a: Action) { sent.add(a) }
        override fun sendEmote(e: Int) {}
        override fun openMenu() {}
        override fun gameOverButtons() = listOf("Ván mới", "Về phòng chờ", "Đóng phòng")
        override fun onGameOverButton(index: Int) {}
        override fun onGameOver(won: Boolean) {}
    }

    @Test
    fun renderScenes() {
        if (out == null) return
        out.mkdirs()
        val ctx = RuntimeEnvironment.getApplication()
        Prefs.init(ctx)
        Prefs.sfx = false; Prefs.vibrate = false; Prefs.music = false
        val screen = GameScreen(ctx, host, true)
        val w = 1080; val h = 2340
        screen.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        screen.layout(0, 0, w, h)

        val names = listOf("Sanoob", "Bé Tí", "Lão Hạc", "Su Su", "Mèo Mập", "Chú Tư")
        val e = ChaosEngine(names.mapIndexed { i, n -> PlayerInit(n, i > 0, 1, i, i % 6) }, Random(5))
        fun feed(steps: List<Step>) = steps.forEach { s -> screen.pushStep(s.events.filter { it.visibleTo(0) }, s.views.getValue(0)) }
        fun shot(name: String, advanceMs: Long) {
            ShadowSystemClock.advanceBy(Duration.ofMillis(advanceMs))
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            screen.draw(Canvas(bmp))
            File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
        }
        feed(e.start())
        shot("01_wheel_spin", 30); shot("01_wheel_spin", 1200)
        shot("02_wheel_result", 2300)
        shot("03_wheel_shells", 1000)
        shot("04_offer", 400)
        // resolve all offers: me keeps, bots decide
        var guard = 0
        while (e.phase == Phase.OFFERS && guard++ < 50) {
            val p = e.alivePlayers().first { it.pending.isNotEmpty() }
            feed(e.apply(p.id, Action.ResolveOffer(true))!!)
        }
        // make it my turn by letting bots play
        guard = 0
        while (e.phase == Phase.PLAYING && e.turn != 0 && guard++ < 30) {
            val a = com.chaosparty.engine.BotBrain.decide(e, e.turn, Random(guard))!!
            feed(e.apply(e.turn, a)!!)
            repeat(8) { shot("05_play", 400) }
        }
        shot("06_my_turn", 3000)
        shot("06_my_turn", 3000)
        shot("06_my_turn", 6000)
        // simulate selecting slot 0
        val me = e.player(0)
        if (me.items[0] != null) {
            screen.dispatchTouchEvent(android.view.MotionEvent.obtain(0, 0, android.view.MotionEvent.ACTION_UP, w * 0.17f, h * 0.83f, 0))
            shot("07_item_selected", 50)
        }
        screen.dispatchTouchEvent(android.view.MotionEvent.obtain(0, 0, android.view.MotionEvent.ACTION_UP, w * 0.5f, h * 0.2f, 0))
        shot("08_target", 300)
        if (e.phase == Phase.PLAYING && e.turn == 0) {
            feed(e.apply(0, Action.Shoot(e.alivePlayers().last().id))!!)
            shot("09_shot", 300); shot("09_shot", 400); shot("09_shot", 300)
        }
        // play to the end
        guard = 0
        while (e.phase != Phase.GAME_OVER && guard++ < 3000) {
            val actor = if (e.phase == Phase.OFFERS) e.alivePlayers().first { it.pending.isNotEmpty() }.id else e.turn
            feed(e.apply(actor, com.chaosparty.engine.BotBrain.decide(e, actor, Random(guard))!!)!!)
        }
        repeat(400) { shot("10_end", 500) }
        shot("10_end", 2000)
        println("SHOTS " + SystemClock.uptimeMillis())
    }
}
