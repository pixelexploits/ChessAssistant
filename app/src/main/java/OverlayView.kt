package com.pixelassistant.chess

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

class OverlayView(context: Context) : View(context) {
    private val darkBlue = Color.parseColor("#0B132B")
    private val lightBlue = Color.parseColor("#4CC9F0")
    private val glassBg = Color.argb(180, 11, 19, 43)

    var showArrows = true
    var showEvalOnTap = true
    var isMinimized = false
    var isMenuOpen = false

    var topArrows = listOf<Pair<Pair<Float, Float>, Pair<Float, Float>>>()
    var threatRects = listOf<RectF>()
    var evalOverlays = mutableMapOf<Pair<Float, Float>, String>()
    var boardRect = Rect()
    var boardFlipped = false

    var onPieceTapped: ((String) -> Unit)? = null
    var onHomeClicked: (() -> Unit)? = null
    var onGameReviewClicked: (() -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 40f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (isMinimized) {
            drawMinimizedPill(canvas)
            return
        }

        canvas.drawColor(glassBg)

        val navPaint = Paint().apply { color = darkBlue }
        canvas.drawRect(0f, 0f, width.toFloat(), 80f, navPaint)
        textPaint.color = lightBlue
        textPaint.textSize = 50f
        canvas.drawText("♛ Chess Assistant", 60f, 55f, textPaint)

        // Three-dots menu button (top-left)
        val dotsPaint = Paint().apply { color = lightBlue; style = Paint.Style.FILL }
        canvas.drawCircle(40f, 40f, 25f, dotsPaint)
        textPaint.color = darkBlue
        textPaint.textSize = 30f
        canvas.drawText("⋮", 30f, 50f, textPaint)

        drawToggle(canvas, "Arrows", 300f, 20f, showArrows)
        drawToggle(canvas, "Eval", 500f, 20f, showEvalOnTap)
        drawToggle(canvas, "Auto-Rematch", 700f, 20f, true)

        val minPaint = Paint().apply { color = lightBlue }
        canvas.drawCircle(width - 60f, 40f, 30f, minPaint)
        textPaint.color = darkBlue
        textPaint.textSize = 40f
        canvas.drawText("−", width - 70f, 55f, textPaint)

        if (isMenuOpen) {
            drawMenu(canvas)
        }

        if (showArrows) {
            topArrows.forEachIndexed { idx, arrow ->
                val (start, end) = arrow
                val (x1, y1) = start
                val (x2, y2) = end
                val color = when (idx) {
                    0 -> Color.GREEN
                    1 -> Color.YELLOW
                    else -> Color.argb(255, 255, 165, 0)
                }
                paint.color = color
                paint.strokeWidth = 15f
                canvas.drawLine(x1, y1, x2, y2, paint)
                val angle = atan2(y2 - y1, x2 - x1)
                val headLen = 40f
                canvas.drawLine(x2, y2,
                    x2 - headLen * cos(angle - 0.5).toFloat(),
                    y2 - headLen * sin(angle - 0.5).toFloat(), paint)
                canvas.drawLine(x2, y2,
                    x2 - headLen * cos(angle + 0.5).toFloat(),
                    y2 - headLen * sin(angle + 0.5).toFloat(), paint)
            }
        }

        threatRects.forEach { rect ->
            paint.color = Color.argb(100, 255, 0, 0)
            canvas.drawRect(rect, paint)
        }

        if (showEvalOnTap) {
            evalOverlays.forEach { (coords, text) ->
                val (x, y) = coords
                paint.color = darkBlue
                paint.alpha = 220
                canvas.drawRoundRect(x - 50f, y - 30f, x + 50f, y + 30f, 20f, 20f, paint)
                paint.color = lightBlue
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 4f
                canvas.drawRoundRect(x - 50f, y - 30f, x + 50f, y + 30f, 20f, 20f, paint)
                paint.style = Paint.Style.FILL
                textPaint.color = if (text.startsWith("M")) Color.YELLOW else Color.WHITE
                textPaint.textSize = 35f
                canvas.drawText(text, x - 30f, y + 12f, textPaint)
            }
        }
    }

    private fun drawMenu(canvas: Canvas) {
        val menuBg = Paint().apply {
            color = darkBlue
            alpha = 240
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(10f, 90f, 250f, 300f, 20f, 20f, menuBg)

        val borderPaint = Paint().apply {
            color = lightBlue
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRoundRect(10f, 90f, 250f, 300f, 20f, 20f, borderPaint)

        textPaint.color = Color.WHITE
        textPaint.textSize = 32f

        canvas.drawText("🏠 Home", 30f, 140f, textPaint)
        canvas.drawText("📊 Game Review", 30f, 200f, textPaint)
        canvas.drawText("🎬 Credits", 30f, 260f, textPaint)
    }

    private fun drawToggle(canvas: Canvas, label: String, x: Float, y: Float, isOn: Boolean) {
        val paint = Paint().apply {
            color = if (isOn) lightBlue else Color.GRAY
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(x, y, x + 80f, y + 40f, 20f, 20f, paint)
        textPaint.color = darkBlue
        textPaint.textSize = 25f
        canvas.drawText(if (isOn) "ON" else "OFF", x + 15f, y + 28f, textPaint)
    }

    private fun drawMinimizedPill(canvas: Canvas) {
        paint.color = lightBlue
        canvas.drawRoundRect(width / 2 - 100f, 20f, width / 2 + 100f, 80f, 40f, 40f, paint)
        textPaint.color = darkBlue
        textPaint.textSize = 30f
        canvas.drawText("♛ OPEN", width / 2 - 55f, 60f, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val x = event.x
            val y = event.y

            // Three-dots menu
            if (x in 15f..65f && y in 15f..65f) {
                isMenuOpen = !isMenuOpen
                invalidate()
                return true
            }

            // Menu click handling
            if (isMenuOpen) {
                if (x in 10f..250f && y in 90f..300f) {
                    if (y in 90f..160f) {
                        isMenuOpen = false
                        onHomeClicked?.invoke()
                        invalidate()
                        return true
                    } else if (y in 160f..230f) {
                        isMenuOpen = false
                        onGameReviewClicked?.invoke()
                        invalidate()
                        return true
                    } else if (y in 230f..300f) {
                        isMenuOpen = false
                        showCredits()
                        invalidate()
                        return true
                    }
                }
                // Click outside closes menu
                isMenuOpen = false
                invalidate()
                return true
            }

            if (x > width - 100f && y < 80f) {
                isMinimized = true
                invalidate()
                return true
            }

            if (isMinimized) {
                if (x > width / 2 - 150f && x < width / 2 + 150f && y > 20f && y < 80f) {
                    isMinimized = false
                    invalidate()
                }
                return true
            }

            if (boardRect.width() > 0) {
                val stepX = boardRect.width() / 8f
                val stepY = boardRect.height() / 8f
                if (x > boardRect.left && x < boardRect.right && y > boardRect.top && y < boardRect.bottom) {
                    val file = ((x - boardRect.left) / stepX).toInt()
                    val rank = if (!boardFlipped) 7 - ((y - boardRect.top) / stepY).toInt() else ((y - boardRect.top) / stepY).toInt()
                    if (file in 0..7 && rank in 0..7) {
                        val square = "${'a' + file}${rank + 1}"
                        onPieceTapped?.invoke(square)
                        return true
                    }
                }
            }

            if (y in 20f..60f) {
                if (x in 300f..380f) { showArrows = !showArrows; invalidate(); return true }
                if (x in 500f..580f) { showEvalOnTap = !showEvalOnTap; invalidate(); return true }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun showCredits() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://youtube.com/@pixeld3v"))
        context.startActivity(intent)
    }
}
