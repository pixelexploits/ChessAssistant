package com.pixelassistant.chess

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.move.Move
import com.github.bhlangonijr.chesslib.Square
import kotlinx.coroutines.*

class ChessService : AccessibilityService() {
    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: OverlayView
    private lateinit var engine: EngineManager
    private val board = Board()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var lastMoveText = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView = OverlayView(this)
        engine = EngineManager(this)

        // Setup overlay params (modern, full screen, not touchable except our view handles touches)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        // Allow our view to receive touches even though it's overlaying
        windowManager.addView(overlayView, params)

        // Set callback for when user taps a piece
        overlayView.onPieceTapped = { square ->
            analyzeLegalMovesForSquare(square)
        }

        // Start foreground notification for minimize/restore
        startForeground(1, createNotification())

        // Fetch board bounds and start listening
        Handler(Looper.getMainLooper()).postDelayed({
            updateBoardBounds()
        }, 1000)
    }

    private fun createNotification() = NotificationCompat.Builder(this, "CHESS_CH")
        .setContentTitle("Chess Assistant")
        .setContentText("Tap to restore overlay")
        .setSmallIcon(android.R.drawable.ic_menu_edit)
        .setContentIntent(
            PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        )
        .build()

    // Called when user taps a piece
    private fun analyzeLegalMovesForSquare(squareStr: String) {
        if (!overlayView.showEvalOnTap) return
        
        val square = Square.valueOf(squareStr.uppercase())
        val legalMoves = board.legalMoves().filter { it.from == square }
        
        serviceScope.launch {
            val results = mutableMapOf<Pair<Float, Float>, String>()
            legalMoves.forEach { move ->
                // Play move on a clone board to get evaluation
                val clone = Board()
                clone.loadFromFen(board.fen)
                clone.doMove(move)
                val score = engine.getEvaluation(clone.fen) // returns "+1.23" or "M2"
                // Map destination square to screen coords
                val dest = move.to.toString().lowercase()
                val (x, y) = squareToPixel(dest)
                results[Pair(x, y)] = score
            }
            withContext(Dispatchers.Main) {
                overlayView.evalOverlays = results
                overlayView.invalidate()
                // Clear evals after 3 seconds
                Handler(Looper.getMainLooper()).postDelayed({
                    overlayView.evalOverlays.clear()
                    overlayView.invalidate()
                }, 3000)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Detect move changes (same as before, but parse and update board)
        val root = rootInActiveWindow ?: return
        val nodes = root.findAccessibilityNodeInfosByViewId("com.lichess.mobileapp:id/move_text") // CHANGE THIS ID
        if (nodes.isNotEmpty()) {
            val current = nodes.last().text.toString()
            if (current != lastMoveText) {
                lastMoveText = current
                // Parse the move (e.g., "e4", "Nf3") and apply to board
                try {
                    val moveObj = Move(current, board.sideToMove)
                    board.doMove(moveObj)
                    // Trigger analysis for arrows
                    runAnalysis()
                } catch (e: Exception) { }
            }
        }
    }

    private fun runAnalysis() {
        serviceScope.launch {
            val topMoves = engine.getTopMoves(board.fen, 3)
            withContext(Dispatchers.Main) {
                overlayView.topArrows = topMoves.map { move ->
                    val from = move.from.toString().lowercase()
                    val to = move.to.toString().lowercase()
                    val (x1,y1) = squareToPixel(from)
                    val (x2,y2) = squareToPixel(to)
                    Triple(x1, y1, x2, y2)
                }
                // Run threat detection (check if any move leads to mate/fork)
                overlayView.threatRects = engine.getThreatenedSquares(board.fen).map { 
                    val (x,y) = squareToPixel(it.lowercase())
                    RectF(x-40f, y-40f, x+40f, y+40f) 
                }
                overlayView.invalidate()
            }
        }
    }

    private fun updateBoardBounds() {
        val root = rootInActiveWindow ?: return
        // CHANGE THIS ID to Lichess/Chess.com board
        val boardNode = root.findAccessibilityNodeInfosByViewId("com.lichess.mobileapp:id/board_view")
        if (boardNode.isNotEmpty()) {
            val rect = android.graphics.Rect()
            boardNode[0].getBoundsInScreen(rect)
            overlayView.boardRect = rect
            // Detect flip: check color of a1 square (if it's white, not flipped)
            overlayView.boardFlipped = false // default
        }
    }

    private fun squareToPixel(square: String): Pair<Float, Float> {
        val rect = overlayView.boardRect
        val file = square[0] - 'a'
        val rank = square[1] - '1'
        val stepX = rect.width() / 8f
        val stepY = rect.height() / 8f
        val x = rect.left + (file + 0.5f) * stepX
        val y = if (!overlayView.boardFlipped) rect.bottom - (rank + 0.5f) * stepY else rect.top + (rank + 0.5f) * stepY
        return Pair(x, y)
    }

    override fun onInterrupt() {}
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        windowManager.removeView(overlayView)
    }
}