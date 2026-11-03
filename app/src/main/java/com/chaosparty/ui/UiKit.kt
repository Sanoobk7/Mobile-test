package com.chaosparty.ui

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.InputFilter
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** Small helpers for building the menu screens in code (no XML layouts needed). */
object UiKit {
    fun dp(ctx: Context, v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, ctx.resources.displayMetrics).toInt()

    fun rounded(color: Int, radius: Float, stroke: Int = 0, strokeColor: Int = 0) = GradientDrawable().apply {
        setColor(color); cornerRadius = radius
        if (stroke > 0) setStroke(stroke, strokeColor)
    }

    fun column(ctx: Context, pad: Int = 20): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        val p = dp(ctx, pad.toFloat())
        setPadding(p, p, p, p)
    }

    fun row(ctx: Context): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    fun title(ctx: Context, text: String, size: Float = 30f, color: Int = Palette.GOLD) = TextView(ctx).apply {
        this.text = text; textSize = size; setTextColor(color); typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setShadowLayer(8f, 0f, 3f, 0xAA000000.toInt())
    }

    fun text(ctx: Context, text: String, size: Float = 15f, color: Int = Palette.TEXT, center: Boolean = false) = TextView(ctx).apply {
        this.text = text; textSize = size; setTextColor(color)
        if (center) gravity = Gravity.CENTER
        setLineSpacing(0f, 1.15f)
    }

    fun button(ctx: Context, label: String, color: Int = Palette.ACCENT, onClick: () -> Unit) = Button(ctx).apply {
        text = label
        isAllCaps = false
        textSize = 17f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Palette.TEXT)
        stateListAnimator = null
        val r = dp(ctx, 16f).toFloat()
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), rounded(darker(color), r))
            addState(intArrayOf(-android.R.attr.state_enabled), rounded(0xFF3A3150.toInt(), r))
            addState(intArrayOf(), rounded(color, r))
        }
        val p = dp(ctx, 14f)
        setPadding(p, p, p, p)
        setOnClickListener {
            Audio.play(Sfx.CARD, 0.5f)
            onClick()
        }
    }

    fun input(ctx: Context, hint: String, max: Int) = EditText(ctx).apply {
        this.hint = hint
        setHintTextColor(Palette.DIM)
        setTextColor(Palette.TEXT)
        textSize = 18f
        isSingleLine = true
        filters = arrayOf(InputFilter.LengthFilter(max))
        background = rounded(0xFF22163D.toInt(), dp(ctx, 14f).toFloat(), dp(ctx, 2f), 0xFF5B4594.toInt())
        val p = dp(ctx, 14f)
        setPadding(p, p, p, p)
    }

    fun card(ctx: Context): LinearLayout = column(ctx, 16).apply {
        background = rounded(0xCC1A1030.toInt(), dp(ctx, 18f).toFloat(), dp(ctx, 1f), 0x33FFFFFF)
    }

    fun lp(ctx: Context, top: Float = 10f, width: Int = ViewGroup.LayoutParams.MATCH_PARENT, weight: Float = 0f) =
        LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT, weight).apply { topMargin = dp(ctx, top) }

    private fun darker(c: Int): Int {
        val r = ((c shr 16 and 0xFF) * 0.75f).toInt(); val g = ((c shr 8 and 0xFF) * 0.75f).toInt(); val b = ((c and 0xFF) * 0.75f).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun immersive(a: Activity) {
        @Suppress("DEPRECATION")
        a.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    }
}

/** A tiny view that draws an avatar, used in menus and the lobby. */
class AvatarView(ctx: Context, var skin: Int, var mood: Int = 0) : View(ctx) {
    override fun onDraw(c: Canvas) {
        val r = minOf(width, height) * 0.36f
        Art.avatar(c, width / 2f, height / 2f + r * 0.1f, r, Skins.avatar(skin), mood)
    }
}
