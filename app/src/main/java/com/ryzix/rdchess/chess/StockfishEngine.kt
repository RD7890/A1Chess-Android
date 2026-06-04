package com.ryzix.rdchess.chess

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.io.*

data class EngineSettings(
    val skillLevel: Int = 4,
    val searchTimeMs: Int = 1000,
    val multiPv: Int = 3,
    val threads: Int = 1,
)

data class AnalysisLine(
    val rank: Int,
    val move: String,            // UCI move, e.g. "e2e4"
    val eval: Float,
    val isMate: Boolean = false,
    val mateIn: Int = 0,
    val continuation: List<String> = emptyList(),
)

val STOCKFISH_LEVELS = listOf(
    EngineSettings(skillLevel = 0,  searchTimeMs = 500,  multiPv = 3, threads = 1),
    EngineSettings(skillLevel = 2,  searchTimeMs = 500,  multiPv = 3, threads = 1),
    EngineSettings(skillLevel = 4,  searchTimeMs = 610,  multiPv = 3, threads = 1),
    EngineSettings(skillLevel = 6,  searchTimeMs = 765,  multiPv = 3, threads = 1),
    EngineSettings(skillLevel = 8,  searchTimeMs = 920,  multiPv = 3, threads = 1),
    EngineSettings(skillLevel = 10, searchTimeMs = 1075, multiPv = 3, threads = 2),
    EngineSettings(skillLevel = 12, searchTimeMs = 1225, multiPv = 3, threads = 2),
    EngineSettings(skillLevel = 14, searchTimeMs = 1380, multiPv = 3, threads = 2),
    EngineSettings(skillLevel = 16, searchTimeMs = 1535, multiPv = 3, threads = 2),
    EngineSettings(skillLevel = 18, searchTimeMs = 1690, multiPv = 3, threads = 2),
    EngineSettings(skillLevel = 19, searchTimeMs = 1845, multiPv = 3, threads = 2),
    EngineSettings(skillLevel = 20, searchTimeMs = 2000, multiPv = 3, threads = 2),
)

class StockfishEngine(private val context: Context) {
    companion object {
        private const val TAG = "StockfishEngine"
        private const val STOCKFISH_ASSET = "stockfish"
    }

    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // bestmove (for vs-computer mode)
    private val _bestMoveFlow = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val bestMoveFlow: SharedFlow<String> = _bestMoveFlow

    // overall eval (white's perspective in pawns)
    private val _evalFlow = MutableSharedFlow<Float>(extraBufferCapacity = 10)
    val evalFlow: SharedFlow<Float> = _evalFlow

    // MultiPV analysis lines
    private val _analysisFlow = MutableSharedFlow<List<AnalysisLine>>(extraBufferCapacity = 10)
    val analysisFlow: SharedFlow<List<AnalysisLine>> = _analysisFlow

    // buffer collected during one search run
    private val lineBuffer = mutableMapOf<Int, AnalysisLine>()

    var isReady = false
        private set

    fun init(): Boolean {
        return try {
            val binary = extractBinary() ?: return false
            val pb = ProcessBuilder(binary.absolutePath)
            pb.redirectErrorStream(true)
            process = pb.start()
            writer = BufferedWriter(OutputStreamWriter(process!!.outputStream))
            reader = BufferedReader(InputStreamReader(process!!.inputStream))
            sendCommand("uci")
            startReadLoop()
            sendCommand("isready")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init Stockfish: ${e.message}")
            false
        }
    }

    private fun startReadLoop() {
        scope.launch {
            try {
                while (true) {
                    val line = reader?.readLine() ?: break
                    Log.d(TAG, "SF> $line")
                    when {
                        line == "readyok" -> isReady = true

                        line.startsWith("bestmove") -> {
                            val parts = line.split(" ")
                            if (parts.size >= 2 && parts[1] != "(none)") {
                                _bestMoveFlow.emit(parts[1])
                            }
                            // flush collected analysis lines
                            if (lineBuffer.isNotEmpty()) {
                                val sorted = lineBuffer.values.sortedBy { it.rank }
                                _analysisFlow.emit(sorted)
                                lineBuffer.clear()
                            }
                        }

                        line.startsWith("info") && line.contains("multipv") -> {
                            parseMultiPvLine(line)?.let { al ->
                                lineBuffer[al.rank] = al
                                // also emit best eval (rank 1)
                                if (al.rank == 1) _evalFlow.emit(al.eval)
                            }
                        }

                        line.startsWith("info") && line.contains("score cp") && !line.contains("multipv") -> {
                            parseEval(line)?.let { _evalFlow.emit(it) }
                        }

                        line.startsWith("info") && line.contains("score mate") && !line.contains("multipv") -> {
                            parseMate(line)?.let { mateIn ->
                                _evalFlow.emit(if (mateIn > 0) 10f else -10f)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Read loop error: ${e.message}")
            }
        }
    }

    fun applySettings(settings: EngineSettings) {
        sendCommand("setoption name Skill Level value ${settings.skillLevel}")
        sendCommand("setoption name Threads value ${settings.threads}")
        sendCommand("setoption name MultiPV value ${settings.multiPv}")
    }

    fun startSearch(fen: String, settings: EngineSettings) {
        lineBuffer.clear()
        sendCommand("stop")
        sendCommand("ucinewgame")
        sendCommand("position fen $fen")
        sendCommand("go movetime ${settings.searchTimeMs}")
    }

    /** Analysis-only search — emits analysisFlow when done, no bestmove used. */
    fun startAnalysis(fen: String, settings: EngineSettings) {
        lineBuffer.clear()
        sendCommand("stop")
        sendCommand("position fen $fen")
        sendCommand("go movetime ${settings.searchTimeMs}")
    }

    fun stop() {
        sendCommand("stop")
    }

    fun quit() {
        sendCommand("quit")
        scope.cancel()
        process?.destroy()
        writer?.close()
        reader?.close()
    }

    private fun sendCommand(cmd: String) {
        try {
            writer?.write(cmd)
            writer?.newLine()
            writer?.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Send command failed: $cmd -> ${e.message}")
        }
    }

    private fun extractBinary(): File? {
        return try {
            val stockfishDir = File(context.filesDir, "stockfish")
            stockfishDir.mkdirs()
            val binary = File(stockfishDir, "stockfish")
            if (!binary.exists() || binary.length() == 0L) {
                context.assets.open(STOCKFISH_ASSET).use { input ->
                    binary.outputStream().use { output -> input.copyTo(output) }
                }
                binary.setExecutable(true)
            }
            binary
        } catch (e: Exception) {
            Log.e(TAG, "Extract binary failed: ${e.message}")
            null
        }
    }

    /**
     * Parses a UCI info line that contains "multipv N".
     * Example:
     *   info depth 20 seldepth 28 multipv 1 score cp 30 nodes 12345 nps 800000 time 1000 pv e2e4 e7e5 g1f3
     */
    private fun parseMultiPvLine(line: String): AnalysisLine? {
        return try {
            val tokens = line.split(" ")
            var rank = -1
            var evalCp: Float? = null
            var isMate = false
            var mateIn = 0
            var pvStart = -1

            var i = 0
            while (i < tokens.size) {
                when (tokens[i]) {
                    "multipv" -> { rank = tokens.getOrNull(i + 1)?.toIntOrNull() ?: -1; i++ }
                    "score" -> {
                        when (tokens.getOrNull(i + 1)) {
                            "cp" -> {
                                evalCp = tokens.getOrNull(i + 2)?.toFloatOrNull()?.div(100f)
                                i += 2
                            }
                            "mate" -> {
                                isMate = true
                                mateIn = tokens.getOrNull(i + 2)?.toIntOrNull() ?: 0
                                evalCp = if (mateIn > 0) 100f else -100f
                                i += 2
                            }
                        }
                    }
                    "pv" -> { pvStart = i + 1; i = tokens.size; continue }
                }
                i++
            }

            if (rank < 1 || evalCp == null) return null

            val pvMoves = if (pvStart >= 0) tokens.drop(pvStart) else emptyList()
            val mainMove = pvMoves.firstOrNull() ?: return null
            val continuation = pvMoves.drop(1).take(5) // up to 5 follow-up moves

            AnalysisLine(
                rank = rank,
                move = mainMove,
                eval = evalCp,
                isMate = isMate,
                mateIn = mateIn,
                continuation = continuation,
            )
        } catch (e: Exception) { null }
    }

    private fun parseEval(line: String): Float? {
        return try {
            val idx = line.indexOf("score cp")
            if (idx < 0) return null
            val parts = line.substring(idx + 9).trim().split(" ")
            parts[0].toFloat() / 100f
        } catch (e: Exception) { null }
    }

    private fun parseMate(line: String): Int? {
        return try {
            val idx = line.indexOf("score mate")
            if (idx < 0) return null
            val parts = line.substring(idx + 11).trim().split(" ")
            parts[0].toInt()
        } catch (e: Exception) { null }
    }
}
