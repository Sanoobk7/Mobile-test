package com.sanoob.caro

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class BoardView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var game: CaroGame? = null
        set(value) { field = value; invalidate() }
    var onCellTap: ((Int, Int) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val boardPaint = Paint().apply { color = context.getColor(R.color.board) }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.grid); strokeWidth = 1f * density
    }
    private val xPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.x); style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val oPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.o); style = Paint.Style.STROKE
    }
    private val lastPaint = Paint().apply { color = 0x33FFC107 }
    private val winPaint = Paint().apply { color = 0x5532CD32 }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val side = if (h == 0) w else minOf(w, h)
        setMeasuredDimension(side, side)
    }

    private fun cell(): Float = width.toFloat() / (game?.size ?: 15)

    override fun onDraw(canvas: Canvas) {
        val g = game ?: return
        val cs = cell()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), boardPaint)
        g.lastMove?.let { (r, c) -> canvas.drawRect(c * cs, r * cs, (c + 1) * cs, (r + 1) * cs, lastPaint) }
        for ((r, c) in g.winLine) canvas.drawRect(c * cs, r * cs, (c + 1) * cs, (r + 1) * cs, winPaint)
        for (i in 0..g.size) {
            val p = i * cs
            canvas.drawLine(p, 0f, p, height.toFloat(), gridPaint)
            canvas.drawLine(0f, p, width.toFloat(), p, gridPaint)
        }
        val pad = cs * 0.22f
        xPaint.strokeWidth = cs * 0.12f
        oPaint.strokeWidth = cs * 0.11f
        for (r in 0 until g.size) for (c in 0 until g.size) {
            val left = c * cs
            val top = r * cs
            when (g.get(r, c)) {
                CaroGame.X -> {
                    canvas.drawLine(left + pad, top + pad, left + cs - pad, top + cs - pad, xPaint)
                    canvas.drawLine(left + cs - pad, top + pad, left + pad, top + cs - pad, xPaint)
                }
                CaroGame.O -> canvas.drawCircle(left + cs / 2, top + cs / 2, cs / 2 - pad, oPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val g = game ?: return false
        if (event.action == MotionEvent.ACTION_UP) {
            val cs = cell()
            val c = (event.x / cs).toInt()
            val r = (event.y / cs).toInt()
            if (g.inside(r, c)) {
                performClick()
                onCellTap?.invoke(r, c)
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()
}
