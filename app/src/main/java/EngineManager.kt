package com.pixelassistant.chess

import android.content.Context
import kotlinx.coroutines.*
import java.io.File

class EngineManager(context: Context) {
    private val process: Process
    private val writer: java.io.BufferedWriter
    private val reader: java.io.BufferedReader

    init {
        // Extract stockfish from assets (do this in init)
        val file = File(context.filesDir, "stockfish")
        if (!file.exists()) {
            context.assets.open("stockfish").use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            file.setExecutable(true)
        }
        process = ProcessBuilder(file.absolutePath).start()
        writer = process.outputStream.bufferedWriter()
        reader = process.inputStream.bufferedReader()
        writer.write("uci\n")
        writer.write("setoption name MultiPV value 3\n")
        writer.flush()
    }

    suspend fun getTopMoves(fen: String, count: Int): List<com.github.bhlangonijr.chesslib.move.Move> {
        return withContext(Dispatchers.IO) {
            writer.write("position fen $fen\n")
            writer.write("go movetime 500\n")
            writer.flush()
            val moves = mutableListOf<com.github.bhlangonijr.chesslib.move.Move>()
            var line: String?
            while (true) {
                line = reader.readLine() ?: break
                if (line.contains("pv")) {
                    val pv = line.substringAfter("pv ").split(" ").first()
                    moves.add(com.github.bhlangonijr.chesslib.move.Move(pv, null)) // simplified
                    if (moves.size >= count) break
                }
                if (line.contains("bestmove")) break
            }
            moves
        }
    }

    suspend fun getEvaluation(fen: String): String {
        return withContext(Dispatchers.IO) {
            writer.write("position fen $fen\n")
            writer.write("go movetime 300\n")
            writer.flush()
            var line: String?
            while (true) {
                line = reader.readLine() ?: break
                if (line.contains("score cp")) {
                    val cp = line.substringAfter("score cp ").substringBefore(" ").toInt()
                    val score = cp / 100.0
                    return@withContext if (score > 0) "+${String.format("%.2f", score)}" else String.format("%.2f", score)
                }
                if (line.contains("score mate")) {
                    val mate = line.substringAfter("score mate ").substringBefore(" ")
                    return@withContext "M$mate"
                }
                if (line.contains("bestmove")) break
            }
            "0.00"
        }
    }

    suspend fun getThreatenedSquares(fen: String): List<String> {
        // Dummy for now. Use chesslib to check if a square is attacked by opponent with double attack
        return listOf("e4", "d5") 
    }
}