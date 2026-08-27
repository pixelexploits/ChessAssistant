package com.pixelassistant.chess

// Custom Rect class for screen bounds (since Android's Rect isn't always imported easily)
data class BoardRect(
    var left: Int = 0,
    var top: Int = 0,
    var right: Int = 0,
    var bottom: Int = 0
) {
    fun width(): Int = right - left
    fun height(): Int = bottom - top
}

// Custom RectF for drawing threats (floating point positions)
data class RectF(
    var left: Float = 0f,
    var top: Float = 0f,
    var right: Float = 0f,
    var bottom: Float = 0f
)