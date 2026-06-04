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
    val multiPv: Int = 1,
    val threads: Int = 1,
)

val STOCKFISH_LEVELS = listOf(
    EngineSettings(skillLevel = 0,  searchTimeMs = 500,  multiPv = 10, threads = 1),
    EngineSettings(skillLevel = 2,  searchTimeMs = 500,  multiPv = 8,  threads = 1),
    EngineSettings(skillLevel = 4,  searchTimeMs = 610,  multiPv = 6,  threads = 1),
    EngineSettings(skillLevel = 6,  searchTimeMs = 765,  multiPv = 5,  threads = 1),
    EngineSettings(skillLevel = 8,  searchTimeMs = 920,  multiPv = 5,  threads = 1),
    EngineSettings(skillLevel = 10, searchTimeMs = 1075, multiPv = 4,  threads = 2),
    EngineSettings(skillLevel = 12, searchTimeMs = 1225, multiPv = 4,  threads = 2),
    EngineSettings(skillLevel = 14, searchTimeMs = 1380, multiPv = 4,  threads = 2),
    EngineSettings(skillLevel = 16, searchTimeMs = 1535, multiPv = 4,  threads = 2),
    EngineSettings(skillLevel = 18, searchTimeMs = 1690, multiPv = 4,  threads = 2),
    EngineSettings(skillLevel = 19, searchTimeMs = 1845, multiPv = 4,  threads = 2),
    EngineSettings(skillLevel = 20, searchTimeMs = 2000, multiPv = 4,  threads = 2),
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
                        }
                        line.startsWith("info") && line.contains("score cp") -> {
                            parseEval(line)?.let { _evalFlow.emit(it) }
                        }
                        line.startsWith("info") && line.contains("score mate") -> {
                            val mateIn = parseMate(line)
                            if (mateIn != null) {
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
        sendCommand("stop")
        sendCommand("ucinewgame")
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
