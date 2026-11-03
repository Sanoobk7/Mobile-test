package com.chaosparty.ui

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.GridLayout
import android.widget.ScrollView

/** Cosmetic skins: avatar and gun are visible to everyone at the table, the table is just for you. */
class SkinActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        build()
    }

    private fun build() {
        val root = UiKit.column(this, 18)
        root.addView(UiKit.title(this, "TỦ ĐỒ", 28f), UiKit.lp(this, 6f))
        root.addView(UiKit.text(this, "Bạn đã thắng ${Prefs.wins} trận. Thắng thêm để mở khoá skin!", 14f, Palette.DIM, center = true), UiKit.lp(this, 4f))
        section(root, "Nhân vật (mọi người đều thấy)", Skins.avatars.size, { Prefs.avatar }, { Prefs.avatar = it }, { Skins.avatars[it].unlock }, { Skins.avatars[it].name }, 0)
        section(root, "Súng (hiện khi bạn cầm súng)", Skins.guns.size, { Prefs.gun }, { Prefs.gun = it }, { Skins.guns[it].unlock }, { Skins.guns[it].name }, 1)
        section(root, "Bàn chơi (chỉ bạn thấy)", Skins.tables.size, { Prefs.table }, { Prefs.table = it }, { Skins.tables[it].unlock }, { Skins.tables[it].name }, 2)
        root.addView(UiKit.button(this, "Xong", Palette.ACCENT) { finish() }, UiKit.lp(this, 18f))
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun section(
        root: android.widget.LinearLayout, title: String, n: Int, get: () -> Int, set: (Int) -> Unit,
        unlock: (Int) -> Int, name: (Int) -> String, kind: Int,
    ) {
        root.addView(UiKit.text(this, title, 16f, Palette.GOLD), UiKit.lp(this, 18f))
        val grid = GridLayout(this).apply { columnCount = 3 }
        val size = (resources.displayMetrics.widthPixels - UiKit.dp(this, 48f)) / 3
        for (i in 0 until n) {
            val locked = unlock(i) > Prefs.wins
            val cell = SkinCell(this, kind, i, name(i), locked, unlock(i), get() == i)
            cell.setOnClickListener {
                if (locked) {
                    android.widget.Toast.makeText(this, "Thắng ${unlock(i)} trận để mở khoá", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    set(i)
                    Audio.play(Sfx.PICK)
                    build()
                }
            }
            grid.addView(cell, GridLayout.LayoutParams().apply { width = size; height = (size * 1.1f).toInt() })
        }
        root.addView(grid, UiKit.lp(this, 6f))
    }
}

private class SkinCell(
    ctx: Context, private val kind: Int, private val index: Int, private val label: String,
    private val locked: Boolean, private val need: Int, private val selected: Boolean,
) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
    private val r = android.graphics.RectF()

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        r.set(w * 0.04f, w * 0.04f, w * 0.96f, h - w * 0.04f)
        p.color = if (selected) 0xFF4C2A85.toInt() else 0xFF22163D.toInt()
        c.drawRoundRect(r, w * 0.1f, w * 0.1f, p)
        if (selected) {
            p.style = Paint.Style.STROKE; p.strokeWidth = w * 0.03f; p.color = Palette.GOLD
            c.drawRoundRect(r, w * 0.1f, w * 0.1f, p)
            p.style = Paint.Style.FILL
        }
        val cy = h * 0.42f
        when (kind) {
            0 -> Art.avatar(c, w / 2, cy, w * 0.26f, Skins.avatars[index], 0)
            1 -> Art.gun(c, w * 0.52f, cy, w * 0.62f, 0f, Skins.guns[index])
            else -> {
                val t = Skins.tables[index]
                p.color = t.rim; c.drawCircle(w / 2, cy, w * 0.3f, p)
                p.color = t.felt; c.drawCircle(w / 2, cy, w * 0.25f, p)
            }
        }
        tp.textSize = w * 0.11f
        tp.color = if (locked) Palette.DIM else Palette.TEXT
        c.drawText(label, w / 2, h * 0.85f, tp)
        if (locked) {
            p.color = 0xAA000000.toInt()
            c.drawRoundRect(r, w * 0.1f, w * 0.1f, p)
            tp.textSize = w * 0.16f; tp.color = Palette.TEXT
            c.drawText("🔒", w / 2, cy + w * 0.05f, tp)
            tp.textSize = w * 0.1f
            c.drawText("$need trận thắng", w / 2, h * 0.85f, tp)
        }
    }
}
