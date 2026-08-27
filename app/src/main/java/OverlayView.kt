package com.pixelassistant.chess

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.*

class OverlayView(context: Context) : View(context) {
    // ---- MODERN COLORS ----
    private val darkBlue = Color.parseColor("#0B132B")
    private val lightBlue = Color.parseColor("#4CC9F0")
    private val glassBg = Color.argb(180, 11, 19, 43) // Semi-transparent dark blue

    // ---- STATE ----
    var showArrows = true
    var showEvalOnTap = true
    var isMinimized = false

    // Data from Engine
    var topArrows = listOf<Triple<Float, Float, Float, Float>>() // x1,y1,x2,y2
    var threatRects = listOf<RectF>()
    
    // TAP EVAL FEATURE: Map of destination square -> evaluation text
    var evalOverlays = mutableMapOf<Pair<Float, Float>, String>() // (x,y) -> "+1.23"

    // Board mapping (set by service)
    var boardRect = Rect()
    var boardFlipped = false

    // Touch listener to detect piece taps
    var onPieceTapped: ((String) -> Unit)? = null // Returns square like "e4"

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 40f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (isMinimized) {
            // Draw small floating restore pill
            drawMinimizedPill(canvas)
            return
        }

        // 1. Draw semi-transparent dark blue glass background for the whole overlay (so it looks modern)
        canvas.drawColor(glassBg)

        // 2. Draw the top dark blue navigation bar (modern)
        val navPaint = Paint().apply { color = darkBlue }
        canvas.drawRect(0f, 0f, width.toFloat(), 80f, navPaint)
        textPaint.color = lightBlue
        textPaint.textSize = 50f
        canvas.drawText("♛ Chess Assistant", 20f, 55f, textPaint)

        // 3. Draw Toggle Buttons (Modern Light Blue pills)
        drawToggle(canvas, "Arrows", 300f, 20f, showArrows)
        drawToggle(canvas, "Eval", 500f, 20f, showEvalOnTap)
        drawToggle(canvas, "Auto-Rematch", 700f, 20f, true) // replace with actual var

        // 4. Minimize Button (Right side)
        val minPaint = Paint().apply { color = lightBlue }
        canvas.drawCircle(width - 60f, 40f, 30f, minPaint)
        textPaint.color = darkBlue
        textPaint.textSize = 40f
        canvas.drawText("−", width - 70f, 55f, textPaint)

        // 5. Draw Arrows (Green/Yellow/Orange for top 3 moves)
        if (showArrows) {
            topArrows.forEachIndexed { idx, (x1,y1,x2,y2) ->
                val color = when(idx) { 0 -> Color.GREEN; 1 -> Color.YELLOW; else -> Color.argb(255,255,165,0) }
                paint.color = color
                paint.strokeWidth = 15f
                canvas.drawLine(x1, y1, x2, y2, paint)
                // Draw arrowhead
                val angle = atan2(y2-y1, x2-x1)
                val headLen = 40f
                canvas.drawLine(x2, y2, x2 - headLen * cos(angle - 0.5), y2 - headLen * sin(angle - 0.5), paint)
                canvas.drawLine(x2, y2, x2 - headLen * cos(angle + 0.5), y2 - headLen * sin(angle + 0.5), paint)
            }
        }

        // 6. Draw Threat Rects (Red translucent for forks/mates)
        threatRects.forEach { rect ->
            paint.color = Color.argb(100, 255, 0, 0)
            canvas.drawRect(rect, paint)
        }

        // 7. Draw Evaluation Overlays (The M1, +1.58 feature)
        if (showEvalOnTap) {
            evalOverlays.forEach { (coords, text) ->
                val (x, y) = coords
                // Draw modern glowing pill background
                paint.color = darkBlue
                paint.alpha = 220
                canvas.drawRoundRect(x - 50f, y - 30f, x + 50f, y + 30f, 20f, 20f, paint)
                // Draw border light blue
                paint.color = lightBlue
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 4f
                canvas.drawRoundRect(x - 50f, y - 30f, x + 50f, y + 30f, 20f, 20f, paint)
                paint.style = Paint.Style.FILL
                // Draw text
                textPaint.color = if (text.startsWith("M")) Color.YELLOW else Color.WHITE
                textPaint.textSize = 35f
                canvas.drawText(text, x - 30f, y + 12f, textPaint)
            }
        }
    }

    private fun drawToggle(canvas: Canvas, label: String, x: Float, y: Float, isOn: Boolean) {
        val paint = Paint().apply {
            color = if (isOn) lightBlue else Color.GRAY
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(x, y, x + 80f, y + 40f, 20f, 20f, paint)
        textPaint.color = darkBlue
        textPaint.textSize = 25f
        canvas.drawText(if(isOn) "ON" else "OFF", x + 15f, y + 28f, textPaint)
    }

    private fun drawMinimizedPill(canvas: Canvas) {
        paint.color = lightBlue
        canvas.drawRoundRect(width/2 - 100f, 20f, width/2 + 100f, 80f, 40f, 40f, paint)
        textPaint.color = darkBlue
        textPaint.textSize = 30f
        canvas.drawText("♛ OPEN", width/2 - 55f, 60f, textPaint)
    }

    // Handle touch to detect piece clicks AND minimize button
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val x = event.x
            val y = event.y

            // Minimize button hitbox
            if (x > width - 100f && y < 80f) {
                isMinimized = true
                invalidate()
                return true
            }

            // If minimized, clicking the OPEN pill restores
            if (isMinimized) {
                if (x > width/2 - 150f && x < width/2 + 150f && y > 20f && y < 80f) {
                    isMinimized = false
                    invalidate()
                }
                return true
            }

            // --- THE MAIN FEATURE: Detect which piece/square was tapped ---
            if (boardRect.width() > 0) {
                val stepX = boardRect.width() / 8f
                val stepY = boardRect.height() / 8f
                // Check if tap is inside board
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
            // If click on toggles, flip their states (simplified for demo)
            if (y in 20f..60f) {
                if (x in 300f..380f) { showArrows = !showArrows; invalidate(); return true }
                if (x in 500f..580f) { showEvalOnTap = !showEvalOnTap; invalidate(); return true }
            }
        }
        return super.onTouchEvent(event)
    }
}