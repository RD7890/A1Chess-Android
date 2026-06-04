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
    val move: String,
    val eval: Float,
    val isMate: Boolean = false,
    val mateIn: Int = 0,
    val continuation: List<String> = emptyList(),
    val depth: Int = 0,
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

    private val _bestMoveFlow = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val bestMoveFlow: SharedFlow<String> = _bestMoveFlow

    private val _evalFlow = MutableSharedFlow<Float>(extraBufferCapacity = 10)
    val evalFlow: SharedFlow<Float> = _evalFlow

    private val _analysisFlow = MutableSharedFlow<List<AnalysisLine>>(extraBufferCapacity = 10)
    val analysisFlow: SharedFlow<List<AnalysisLine>> = _analysisFlow

    // Buffer collected during one search run
    private val lineBuffer = mutableMapOf<Int, AnalysisLine>()

    // Track last emitted depth to avoid redundant partial emits
    private var lastEmittedDepth = -1

    var isReady = false
        private set

    @Volatile private var pendingFen: String? = null
    @Volatile private var pendingSettings: EngineSettings? = null
    @Volatile private var isAnalysisPending = false

    @Volatile private var pendingSearchFen: String? = null
    @Volatile private var pendingSearchSettings: EngineSettings? = null
    @Volatile private var isSearchPending = false

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
                        line == "readyok" -> {
                            isReady = true
                            if (isAnalysisPending) {
                                isAnalysisPending = false
                                val fen = pendingFen
                                val settings = pendingSettings
                                pendingFen = null
                                pendingSettings = null
                                if (fen != null && settings != null) {
                                    sendCommand("ucinewgame")
                                    sendCommand("position fen $fen")
                                    sendCommand("go movetime ${settings.searchTimeMs}")
                                    Log.d(TAG, "Analysis started for FEN: $fen")
                                }
                            }
                            if (isSearchPending) {
                                isSearchPending = false
                                val fen = pendingSearchFen
                                val settings = pendingSearchSettings
                                pendingSearchFen = null
                                pendingSearchSettings = null
                                if (fen != null && settings != null) {
                                    sendCommand("ucinewgame")
                                    sendCommand("position fen $fen")
                                    sendCommand("go movetime ${settings.searchTimeMs}")
                                    Log.d(TAG, "Search started for FEN: $fen")
                                }
                            }
                        }

                        line.startsWith("bestmove") -> {
                            val parts = line.split(" ")
                            val move = if (parts.size >= 2) parts[1] else null
                            // Flush final accumulated MultiPV lines
                            if (lineBuffer.isNotEmpty()) {
                                val sorted = lineBuffer.values.sortedBy { it.rank }
                                _analysisFlow.emit(sorted)
                                lineBuffer.clear()
                                lastEmittedDepth = -1
                                Log.d(TAG, "Final analysis flushed: ${sorted.size} lines")
                            }
                            if (move != null && move != "(none)") {
                                _bestMoveFlow.emit(move)
                            }
                        }

                        line.startsWith("info") && line.contains("multipv") -> {
                            parseMultiPvLine(line)?.let { al ->
                                lineBuffer[al.rank] = al
                                if (al.rank == 1) {
                                    _evalFlow.emit(al.eval)
                                }
                                // Emit partial results as soon as we have ALL N lines at same depth
                                // This gives live updates without waiting for bestmove
                                val expectedPvCount = pendingSettings?.multiPv ?: 3
                                val allSameDepth = lineBuffer.values.isNotEmpty() &&
                                    lineBuffer.values.all { it.depth == al.depth }
                                val haveEnoughLines = lineBuffer.size >= minOf(expectedPvCount, lineBuffer.size + 1)
                                if (allSameDepth && haveEnoughLines && al.depth > lastEmittedDepth) {
                                    lastEmittedDepth = al.depth
                                    val sorted = lineBuffer.values.sortedBy { it.rank }
                                    _analysisFlow.emit(sorted)
                                    Log.d(TAG, "Partial emit at depth ${al.depth}: ${sorted.size} lines")
                                }
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

    fun startAnalysis(fen: String, settings: EngineSettings) {
        lineBuffer.clear()
        lastEmittedDepth = -1
        pendingFen = fen
        pendingSettings = settings
        isAnalysisPending = true
        sendCommand("stop")
        sendCommand("isready")
    }

    fun startSearch(fen: String, settings: EngineSettings) {
        lineBuffer.clear()
        lastEmittedDepth = -1
        pendingSearchFen = fen
        pendingSearchSettings = settings
        isSearchPending = true
        sendCommand("stop")
        sendCommand("isready")
    }

    fun stop() {
        lineBuffer.clear()
        lastEmittedDepth = -1
        isAnalysisPending = false
        isSearchPending = false
        pendingFen = null
        pendingSettings = null
        pendingSearchFen = null
        pendingSearchSettings = null
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
            Log.e(TAG, "Extract binary failed — stockfish not in assets: ${e.message}")
            null
        }
    }

    private fun parseMultiPvLine(line: String): AnalysisLine? {
        return try {
            val tokens = line.split(" ").filter { it.isNotBlank() }
            var rank = -1
            var evalCp: Float? = null
            var isMate = false
            var mateIn = 0
            var pvStart = -1
            var depth = 0

            var i = 0
            while (i < tokens.size) {
                when (tokens[i]) {
                    "multipv" -> { rank = tokens.getOrNull(i + 1)?.toIntOrNull() ?: -1; i++ }
                    "depth"   -> { depth = tokens.getOrNull(i + 1)?.toIntOrNull() ?: 0; i++ }
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
            val mainMove = pvMoves.firstOrNull()?.trimEnd() ?: return null
            if (mainMove.length < 4) return null
            val continuation = pvMoves.drop(1).take(5).map { it.trimEnd() }

            AnalysisLine(
                rank = rank,
                move = mainMove,
                eval = evalCp,
                isMate = isMate,
                mateIn = mateIn,
                continuation = continuation,
                depth = depth,
            )
        } catch (e: Exception) {
            Log.e(TAG, "parseMultiPvLine error: ${e.message} | line: $line")
            null
        }
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
