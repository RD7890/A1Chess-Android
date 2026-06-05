package com.ryzix.rdchess.viewmodel

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ryzix.rdchess.chess.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "a1chess_prefs")

object PrefKeys {
    val LEVEL         = intPreferencesKey("engine_level")
    val SEARCH_TIME   = intPreferencesKey("search_time_ms")
    val MULTI_PV      = intPreferencesKey("multi_pv")
    val THREADS       = intPreferencesKey("threads")
    val PLAY_AS_WHITE = intPreferencesKey("play_as_white")
    val SHOW_ARROWS   = intPreferencesKey("show_arrows")
    val GAME_HISTORY  = stringPreferencesKey("game_history")
}

data class AppPrefs(
    val levelIndex: Int = 3,
    val searchTimeMs: Int = 1000,
    val multiPv: Int = 3,
    val threads: Int = 1,
    val playAsWhite: Boolean = true,
    val showArrows: Boolean = true,
)

// ── Move grade ────────────────────────────────────────────────────────────────

enum class MoveGrade(val label: String, val symbol: String, val colorHex: Long) {
    BRILLIANT("Brilliant", "!!", 0xFF00BCD4),
    BEST("Best Move", "!",  0xFF4CAF50),
    GOOD("Good",     "+",  0xFF8BC34A),
    INACCURACY("Inaccuracy", "?!", 0xFFFF9800),
    MISTAKE("Mistake", "?", 0xFFFF5722),
    BLUNDER("Blunder", "??", 0xFFF44336),
}

data class MoveGradeResult(
    val grade: MoveGrade,
    val playedUci: String,
    val bestUci: String,
    val cpLoss: Float,
)

// ── Saved game record ─────────────────────────────────────────────────────────

data class SavedGame(
    val result: String,
    val moveCount: Int,
    val timestamp: Long,
    val startTimeSecs: Int,
) {
    fun toRecord() = "$result|$moveCount|$timestamp|$startTimeSecs"

    companion object {
        fun fromRecord(s: String): SavedGame? {
            val p = s.split("|")
            if (p.size < 4) return null
            return SavedGame(
                result        = p[0],
                moveCount     = p[1].toIntOrNull() ?: 0,
                timestamp     = p[2].toLongOrNull() ?: 0L,
                startTimeSecs = p[3].toIntOrNull() ?: 300,
            )
        }
    }
}

// ── Constants ─────────────────────────────────────────────────────────────────

const val DEFAULT_TIME_SECS = 5 * 60

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val chessGame = ChessGame()
    val gameState: StateFlow<GameState> = chessGame.state

    private val engine = StockfishEngine(application)
    private var engineReady = false

    private val _engineEval = MutableStateFlow(0f)
    val engineEval: StateFlow<Float> = _engineEval

    private val _isEngineThinking = MutableStateFlow(false)
    val isEngineThinking: StateFlow<Boolean> = _isEngineThinking

    private val _engineEnabled = MutableStateFlow(true)
    val engineEnabled: StateFlow<Boolean> = _engineEnabled

    private val _analysisLines = MutableStateFlow<List<AnalysisLine>>(emptyList())
    val analysisLines: StateFlow<List<AnalysisLine>> = _analysisLines

    private val _lastMoveGrade = MutableStateFlow<MoveGradeResult?>(null)
    val lastMoveGrade: StateFlow<MoveGradeResult?> = _lastMoveGrade

    private val _engineAvailable = MutableStateFlow(false)
    val engineAvailable: StateFlow<Boolean> = _engineAvailable

    private val _prefs = MutableStateFlow(AppPrefs())
    val prefs: StateFlow<AppPrefs> = _prefs

    // ── Pawn promotion pending ────────────────────────────────────────────────
    /** Non-null while waiting for the user to pick a promotion piece. */
    private val _promotionPending = MutableStateFlow<Pair<String, String>?>(null)
    val promotionPending: StateFlow<Pair<String, String>?> = _promotionPending

    // ── Timer ────────────────────────────────────────────────────────────────
    private val _whiteTimeSecs = MutableStateFlow(DEFAULT_TIME_SECS)
    val whiteTimeSecs: StateFlow<Int> = _whiteTimeSecs

    private val _blackTimeSecs = MutableStateFlow(DEFAULT_TIME_SECS)
    val blackTimeSecs: StateFlow<Int> = _blackTimeSecs

    private val _isTimerPaused = MutableStateFlow(false)
    val isTimerPaused: StateFlow<Boolean> = _isTimerPaused

    private val _isTimerRunning = MutableStateFlow(false)
    val isTimerRunning: StateFlow<Boolean> = _isTimerRunning

    private var timerJob: Job? = null
    private var currentStartTimeSecs = DEFAULT_TIME_SECS

    // ── OTB mode (pass-and-play, engine only analyses) ───────────────────────
    private val _isOtbMode = MutableStateFlow(true)
    val isOtbMode: StateFlow<Boolean> = _isOtbMode

    // ── Game history ──────────────────────────────────────────────────────────
    private val _gameHistory = MutableStateFlow<List<SavedGame>>(emptyList())
    val gameHistory: StateFlow<List<SavedGame>> = _gameHistory

    // ── Move grade tracking ───────────────────────────────────────────────────
    @Volatile private var isGradingPending = false
    @Volatile private var preMoveEval = 0f
    @Volatile private var preMoveLines: List<AnalysisLine> = emptyList()

    init {
        loadPrefs()
        initEngine()
        collectEngineOutput()
    }

    private fun loadPrefs() {
        viewModelScope.launch {
            getApplication<Application>().dataStore.data.collect { data ->
                _prefs.value = AppPrefs(
                    levelIndex   = data[PrefKeys.LEVEL] ?: 3,
                    searchTimeMs = data[PrefKeys.SEARCH_TIME] ?: 1000,
                    multiPv      = data[PrefKeys.MULTI_PV] ?: 3,
                    threads      = data[PrefKeys.THREADS] ?: 1,
                    playAsWhite  = (data[PrefKeys.PLAY_AS_WHITE] ?: 1) == 1,
                    showArrows   = (data[PrefKeys.SHOW_ARROWS] ?: 1) == 1,
                )
                // Parse saved game history
                val raw = data[PrefKeys.GAME_HISTORY] ?: ""
                _gameHistory.value = raw
                    .split("\n")
                    .filter { it.isNotBlank() }
                    .mapNotNull { SavedGame.fromRecord(it) }
            }
        }
    }

    private fun initEngine() {
        viewModelScope.launch {
            engineReady = engine.init()
            _engineAvailable.value = engineReady
            if (engineReady) {
                val settings = STOCKFISH_LEVELS.getOrElse(_prefs.value.levelIndex) { STOCKFISH_LEVELS[3] }
                engine.applySettings(settings)
                delay(300)
                runAnalysis()
            }
        }
    }

    private fun collectEngineOutput() {
        // bestmove — marks end of thinking
        viewModelScope.launch {
            engine.bestMoveFlow.collect { bestMove ->
                _isEngineThinking.value = false

                // Grade the move just played (OTB mode)
                if (_isOtbMode.value && isGradingPending && _analysisLines.value.isNotEmpty()) {
                    isGradingPending = false
                    val postEval = _analysisLines.value.firstOrNull()?.eval ?: _engineEval.value
                    gradeLastMove(postEval)
                }

                // vs-Computer mode: engine makes its reply
                if (!_isOtbMode.value && !gameState.value.isGameOver && !gameState.value.isWhiteTurn) {
                    val from = bestMove.substring(0, 2)
                    val to   = bestMove.substring(2, 4)
                    val promo = if (bestMove.length >= 5) bestMove[4] else 'q'
                    delay(300)
                    chessGame.tryMove(from, to, promo)
                    runAnalysis()
                }
            }
        }

        // eval — update value only, do NOT toggle isThinking (prevents recomposition storm)
        viewModelScope.launch {
            engine.evalFlow.collect { eval ->
                _engineEval.value = eval
                // isThinking is set TRUE in runAnalysis/triggerEngineMove and
                // FALSE only when bestmove arrives. Do not set it here.
            }
        }

        // analysis lines → update UI + arrows
        viewModelScope.launch {
            engine.analysisFlow.collect { lines ->
                _analysisLines.value = lines
                if (_engineEnabled.value && _prefs.value.showArrows) {
                    updateArrows(lines)
                }
            }
        }
    }

    // ── Move grading ──────────────────────────────────────────────────────────

    private fun gradeLastMove(postEval: Float) {
        val cpLoss = preMoveEval + postEval
        val preBestUci = preMoveLines.firstOrNull()?.move ?: ""
        val lastMove = gameState.value.moves.lastOrNull()
        val playedUci = if (lastMove != null) "${lastMove.from}${lastMove.to}" else ""
        val isBestMove = playedUci.take(4) == preBestUci.take(4)

        val grade = when {
            cpLoss <= 0.05f && isBestMove -> MoveGrade.BEST
            cpLoss <= 0.05f              -> MoveGrade.BRILLIANT
            cpLoss <= 0.30f              -> MoveGrade.GOOD
            cpLoss <= 0.75f              -> MoveGrade.INACCURACY
            cpLoss <= 1.50f              -> MoveGrade.MISTAKE
            else                         -> MoveGrade.BLUNDER
        }

        _lastMoveGrade.value = MoveGradeResult(
            grade = grade, playedUci = playedUci, bestUci = preBestUci, cpLoss = cpLoss,
        )
    }

    // ── Arrow drawing ──────────────────────────────────────────────────────────

    private fun updateArrows(lines: List<AnalysisLine>) {
        val arrows = lines.take(3).mapIndexedNotNull { idx, line ->
            val from = line.move.take(2).takeIf { it.length == 2 } ?: return@mapIndexedNotNull null
            val to   = line.move.drop(2).take(2).takeIf { it.length == 2 } ?: return@mapIndexedNotNull null
            Arrow(from, to, when (idx) {
                0 -> ArrowColor.GREEN
                1 -> ArrowColor.YELLOW
                else -> ArrowColor.RED
            })
        }
        chessGame.setArrows(arrows)
    }

    /** Run Stockfish analysis on the current position (analysis only, no move). */
    private fun runAnalysis() {
        if (!engineReady || !_engineEnabled.value) return
        val settings = STOCKFISH_LEVELS.getOrElse(_prefs.value.levelIndex) { STOCKFISH_LEVELS[3] }
            .copy(multiPv = maxOf(3, _prefs.value.multiPv))
        engine.applySettings(settings)
        _isEngineThinking.value = true
        engine.startAnalysis(chessGame.getCurrentFen(), settings)
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    fun setOtbMode(otb: Boolean) { _isOtbMode.value = otb }

    fun onSquareTap(square: String) {
        val state = gameState.value
        if (state.isGameOver) return
        if (!_isOtbMode.value && (!state.isWhiteTurn || _isEngineThinking.value)) return

        val prevSelected = state.selectedSquare

        if (prevSelected != null && state.legalMoves.contains(square)) {
            // Check for pawn promotion before executing the move
            if (chessGame.isPromotionMove(prevSelected, square)) {
                _promotionPending.value = Pair(prevSelected, square)
                // Keep the square selected visually by clearing selection
                chessGame.selectSquare(prevSelected) // re-select to show legal moves still
                return
            }

            val moved = chessGame.tryMove(prevSelected, square)
            if (moved) {
                if (!_isTimerRunning.value) startTimer()
                chessGame.clearArrows()

                if (_isOtbMode.value) {
                    isGradingPending = engineReady && _engineEnabled.value
                    preMoveEval = _engineEval.value
                    preMoveLines = _analysisLines.value
                    _lastMoveGrade.value = null
                    _analysisLines.value = emptyList()
                    runAnalysis()

                    // Auto-save if game ended
                    if (gameState.value.isGameOver) saveCurrentGame()
                } else {
                    triggerEngineMove()
                }
                return
            }
        }
        chessGame.selectSquare(square)
    }

    /** Called from UI after the user picks a promotion piece ('q','r','b','n'). */
    fun confirmPromotion(promoChar: Char) {
        val pending = _promotionPending.value ?: return
        _promotionPending.value = null

        val moved = chessGame.tryMove(pending.first, pending.second, promoChar)
        if (moved) {
            if (!_isTimerRunning.value) startTimer()
            chessGame.clearArrows()

            if (_isOtbMode.value) {
                isGradingPending = engineReady && _engineEnabled.value
                preMoveEval = _engineEval.value
                preMoveLines = _analysisLines.value
                _lastMoveGrade.value = null
                _analysisLines.value = emptyList()
                runAnalysis()
                if (gameState.value.isGameOver) saveCurrentGame()
            } else {
                triggerEngineMove()
            }
        }
    }

    fun cancelPromotion() {
        _promotionPending.value = null
    }

    fun triggerEngineMove() {
        if (!engineReady || gameState.value.isGameOver) return
        val settings = STOCKFISH_LEVELS.getOrElse(_prefs.value.levelIndex) { STOCKFISH_LEVELS[3] }
        _isEngineThinking.value = true
        engine.applySettings(settings)
        engine.startSearch(chessGame.getCurrentFen(), settings)
    }

    fun toggleEngine() {
        _engineEnabled.value = !_engineEnabled.value
        if (_engineEnabled.value) {
            runAnalysis()
        } else {
            engine.stop()
            _isEngineThinking.value = false
            _analysisLines.value = emptyList()
            chessGame.clearArrows()
        }
    }

    // ── Timer ─────────────────────────────────────────────────────────────────

    private fun startTimer() {
        _isTimerRunning.value = true
        _isTimerPaused.value = false
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                if (_isTimerPaused.value || gameState.value.isGameOver) continue
                val isWhite = gameState.value.isWhiteTurn
                if (isWhite) {
                    val remaining = _whiteTimeSecs.value - 1
                    _whiteTimeSecs.value = maxOf(0, remaining)
                    if (remaining <= 0) break
                } else {
                    val remaining = _blackTimeSecs.value - 1
                    _blackTimeSecs.value = maxOf(0, remaining)
                    if (remaining <= 0) break
                }
            }
        }
    }

    fun toggleTimer() { _isTimerPaused.value = !_isTimerPaused.value }

    // ── Game control ───────────────────────────────────────────────────────────

    fun newGame(
        playAsWhite: Boolean = true,
        otbMode: Boolean = true,
        timeSecs: Int = DEFAULT_TIME_SECS,
    ) {
        timerJob?.cancel()
        engine.stop()
        _isEngineThinking.value = false
        _engineEval.value = 0f
        _analysisLines.value = emptyList()
        _lastMoveGrade.value = null
        _promotionPending.value = null
        isGradingPending = false
        _isOtbMode.value = otbMode
        currentStartTimeSecs = timeSecs
        _whiteTimeSecs.value = timeSecs
        _blackTimeSecs.value = timeSecs
        _isTimerRunning.value = false
        _isTimerPaused.value = false
        chessGame.reset()

        viewModelScope.launch {
            delay(400)
            if (otbMode) runAnalysis()
            else if (!playAsWhite) triggerEngineMove()
        }
    }

    fun navigateBack() {
        engine.stop()
        _isEngineThinking.value = false
        _lastMoveGrade.value = null
        chessGame.navigateBack()
        chessGame.clearArrows()
        if (_engineEnabled.value) runAnalysis()
    }

    fun navigateForward() {
        _lastMoveGrade.value = null
        chessGame.navigateForward()
        chessGame.clearArrows()
        if (_engineEnabled.value) runAnalysis()
    }

    fun undoOtbMove() {
        engine.stop()
        _isEngineThinking.value = false
        _lastMoveGrade.value = null
        chessGame.undoMove()
        chessGame.clearArrows()
        if (_engineEnabled.value) runAnalysis()
    }

    fun undoMove() {
        engine.stop()
        _isEngineThinking.value = false
        chessGame.undoMove()
        chessGame.undoMove()
    }

    fun flipBoard() { chessGame.flipBoard() }

    // ── Game history ───────────────────────────────────────────────────────────

    private fun saveCurrentGame() {
        val state = gameState.value
        val result = state.gameResult ?: return
        val game = SavedGame(
            result        = result,
            moveCount     = state.moves.size,
            timestamp     = System.currentTimeMillis(),
            startTimeSecs = currentStartTimeSecs,
        )
        viewModelScope.launch {
            val current = _gameHistory.value.toMutableList()
            current.add(0, game)                     // newest first
            val kept = current.take(50)              // cap at 50 entries
            _gameHistory.value = kept
            val encoded = kept.joinToString("\n") { it.toRecord() }
            getApplication<Application>().dataStore.edit { it[PrefKeys.GAME_HISTORY] = encoded }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            _gameHistory.value = emptyList()
            getApplication<Application>().dataStore.edit { it[PrefKeys.GAME_HISTORY] = "" }
        }
    }

    // ── Pref saves ─────────────────────────────────────────────────────────────

    fun saveLevel(levelIndex: Int) {
        val settings = STOCKFISH_LEVELS.getOrElse(levelIndex) { STOCKFISH_LEVELS[3] }
        // Update in-memory prefs immediately so runAnalysis picks up new level right away
        _prefs.value = _prefs.value.copy(
            levelIndex   = levelIndex,
            searchTimeMs = settings.searchTimeMs,
            multiPv      = settings.multiPv,
            threads      = settings.threads,
        )
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { prefs ->
                prefs[PrefKeys.LEVEL]       = levelIndex
                prefs[PrefKeys.SEARCH_TIME] = settings.searchTimeMs
                prefs[PrefKeys.MULTI_PV]    = settings.multiPv
                prefs[PrefKeys.THREADS]     = settings.threads
            }
        }
        // Re-run analysis with new level
        if (_engineEnabled.value && engineReady) runAnalysis()
    }

    fun saveSearchTime(ms: Int) {
        _prefs.value = _prefs.value.copy(searchTimeMs = ms)
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { it[PrefKeys.SEARCH_TIME] = ms }
        }
    }

    fun saveMultiPv(v: Int) {
        _prefs.value = _prefs.value.copy(multiPv = v)
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { it[PrefKeys.MULTI_PV] = v }
        }
    }

    fun saveThreads(v: Int) {
        _prefs.value = _prefs.value.copy(threads = v)
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { it[PrefKeys.THREADS] = v }
        }
    }

    override fun onCleared() {
        super.onCleared()
        engine.quit()
    }
}
