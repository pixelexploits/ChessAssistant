package com.pixelassistant.chess

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move
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

        // Load toggles from intent
        val intent = intent
        overlayView.showArrows = intent.getBooleanExtra("showArrows", true)
        overlayView.showEvalOnTap = intent.getBooleanExtra("showEvalOnTap", true)
        // autoRematch can be stored as a variable

        // Menu callbacks
        overlayView.onHomeClicked = {
            stopSelf()
            val homeIntent = Intent(this, MainActivity::class.java)
            homeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(homeIntent)
        }

        overlayView.onGameReviewClicked = {
            stopSelf()
            val reviewIntent = Intent(this, MainActivity::class.java)
            reviewIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            reviewIntent.putExtra("openGameReview", true)
            startActivity(reviewIntent)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        windowManager.addView(overlayView, params)

        overlayView.onPieceTapped = { square ->
            analyzeLegalMovesForSquare(square)
        }

        createNotificationChannel()
        startForeground(1, getNotification())

        Handler(Looper.getMainLooper()).postDelayed({
            updateBoardBounds()
        }, 1000)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("CHESS_CH", "Chess Assistant", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun getNotification() = NotificationCompat.Builder(this, "CHESS_CH")
        .setContentTitle("Chess Assistant")
        .setContentText("Tap to restore overlay")
        .setSmallIcon(android.R.drawable.ic_menu_edit)
        .setContentIntent(
            PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        )
        .build()

    private fun analyzeLegalMovesForSquare(squareStr: String) {
        if (!overlayView.showEvalOnTap) return

        val square = Square.valueOf(squareStr.uppercase())
        val legalMoves = board.legalMoves().filter { it.from == square }

        serviceScope.launch {
            val results = mutableMapOf<Pair<Float, Float>, String>()
            legalMoves.forEach { move ->
                val clone = Board()
                clone.loadFromFen(board.fen)
                clone.doMove(move)
                val score = engine.getEvaluation(clone.fen)
                val dest = move.to.toString().lowercase()
                val (x, y) = squareToPixel(dest)
                results[Pair(x, y)] = score
            }
            withContext(Dispatchers.Main) {
                overlayView.evalOverlays = results
                overlayView.invalidate()
                Handler(Looper.getMainLooper()).postDelayed({
                    overlayView.evalOverlays.clear()
                    overlayView.invalidate()
                }, 3000)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val root = rootInActiveWindow ?: return
        // REPLACE THIS ID with actual Lichess move list ID
        val nodes = root.findAccessibilityNodeInfosByViewId("com.lichess.mobileapp:id/move_text")
        if (nodes.isNotEmpty()) {
            val current = nodes.last().text.toString()
            if (current != lastMoveText) {
                lastMoveText = current
                try {
                    val moveObj = Move(current, board.sideToMove)
                    board.doMove(moveObj)
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
                    val (x1, y1) = squareToPixel(from)
                    val (x2, y2) = squareToPixel(to)
                    Pair(Pair(x1, y1), Pair(x2, y2))
                }
                overlayView.threatRects = engine.getThreatenedSquares(board.fen).map {
                    val (x, y) = squareToPixel(it.lowercase())
                    RectF(x - 40f, y - 40f, x + 40f, y + 40f)
                }
                overlayView.invalidate()
            }
        }
    }

    private fun updateBoardBounds() {
        val root = rootInActiveWindow ?: return
        // REPLACE THIS ID with actual Lichess board view ID
        val boardNode = root.findAccessibilityNodeInfosByViewId("com.lichess.mobileapp:id/board_view")
        if (boardNode.isNotEmpty()) {
            val rect = Rect()
            boardNode[0].getBoundsInScreen(rect)
            overlayView.boardRect = rect
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
        if (::windowManager.isInitialized && ::overlayView.isInitialized) {
            windowManager.removeView(overlayView)
        }
    }
}
