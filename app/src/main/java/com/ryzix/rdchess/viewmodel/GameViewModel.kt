package com.ryzix.rdchess.viewmodel

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.bhlangonijr.chesslib.Piece
import com.ryzix.rdchess.chess.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "a1chess_prefs")

object PrefKeys {
    val LEVEL       = intPreferencesKey("engine_level")
    val SEARCH_TIME = intPreferencesKey("search_time_ms")
    val MULTI_PV    = intPreferencesKey("multi_pv")
    val THREADS     = intPreferencesKey("threads")
    val PLAY_AS_WHITE = intPreferencesKey("play_as_white")
    val SHOW_ARROWS = intPreferencesKey("show_arrows")
}

data class AppPrefs(
    val levelIndex: Int = 3,
    val searchTimeMs: Int = 1000,
    val multiPv: Int = 3,
    val threads: Int = 1,
    val playAsWhite: Boolean = true,
    val showArrows: Boolean = true,
)

/** 5 minutes per player by default */
private const val DEFAULT_TIME_SECS = 5 * 60

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

    private val _prefs = MutableStateFlow(AppPrefs())
    val prefs: StateFlow<AppPrefs> = _prefs

    // --- Timer state ---
    private val _whiteTimeSecs = MutableStateFlow(DEFAULT_TIME_SECS)
    val whiteTimeSecs: StateFlow<Int> = _whiteTimeSecs

    private val _blackTimeSecs = MutableStateFlow(DEFAULT_TIME_SECS)
    val blackTimeSecs: StateFlow<Int> = _blackTimeSecs

    private val _isTimerPaused = MutableStateFlow(false)
    val isTimerPaused: StateFlow<Boolean> = _isTimerPaused

    private val _isTimerRunning = MutableStateFlow(false)
    val isTimerRunning: StateFlow<Boolean> = _isTimerRunning

    private var timerJob: Job? = null

    // --- OTB mode (pass-and-play, engine only analyses) ---
    private val _isOtbMode = MutableStateFlow(true)
    val isOtbMode: StateFlow<Boolean> = _isOtbMode

    init {
        loadPrefs()
        initEngine()
        collectEngineOutput()
    }

    private fun loadPrefs() {
        viewModelScope.launch {
            getApplication<Application>().dataStore.data.collect { data ->
                _prefs.value = AppPrefs(
                    levelIndex    = data[PrefKeys.LEVEL] ?: 3,
                    searchTimeMs  = data[PrefKeys.SEARCH_TIME] ?: 1000,
                    multiPv       = data[PrefKeys.MULTI_PV] ?: 3,
                    threads       = data[PrefKeys.THREADS] ?: 1,
                    playAsWhite   = (data[PrefKeys.PLAY_AS_WHITE] ?: 1) == 1,
                    showArrows    = (data[PrefKeys.SHOW_ARROWS] ?: 1) == 1,
                )
            }
        }
    }

    private fun initEngine() {
        viewModelScope.launch {
            engineReady = engine.init()
            if (engineReady) {
                val settings = STOCKFISH_LEVELS.getOrElse(_prefs.value.levelIndex) { STOCKFISH_LEVELS[3] }
                engine.applySettings(settings)
            }
        }
    }

    private fun collectEngineOutput() {
        // bestmove — only used in vs-computer mode
        viewModelScope.launch {
            engine.bestMoveFlow.collect { bestMove ->
                _isEngineThinking.value = false
                val state = gameState.value
                if (!_isOtbMode.value && !state.isGameOver && !state.isWhiteTurn) {
                    val from = bestMove.substring(0, 2)
                    val to   = bestMove.substring(2, 4)
                    delay(300)
                    val captured = chessGame.getPieceAt(to) != Piece.NONE
                    chessGame.tryMove(from, to)
                    // After engine move, run analysis for player
                    runAnalysis()
                }
            }
        }
        // eval
        viewModelScope.launch {
            engine.evalFlow.collect { eval ->
                _engineEval.value = eval
            }
        }
        // analysis lines
        viewModelScope.launch {
            engine.analysisFlow.collect { lines ->
                _analysisLines.value = lines
                // Set arrows from top moves if engine is on
                if (_engineEnabled.value && _prefs.value.showArrows) {
                    updateArrows(lines)
                }
            }
        }
    }

    private fun updateArrows(lines: List<AnalysisLine>) {
        val arrows = lines.take(3).mapIndexed { idx, line ->
            val from = if (line.move.length >= 4) line.move.substring(0, 2) else return@mapIndexed null
            val to   = if (line.move.length >= 4) line.move.substring(2, 4) else return@mapIndexed null
            val color = when (idx) {
                0 -> ArrowColor.GREEN
                1 -> ArrowColor.YELLOW
                else -> ArrowColor.RED
            }
            Arrow(from, to, color)
        }.filterNotNull()
        chessGame.setArrows(arrows)
    }

    /** Run Stockfish analysis on the current position (no best-move action). */
    private fun runAnalysis() {
        if (!engineReady || !_engineEnabled.value) return
        val settings = STOCKFISH_LEVELS.getOrElse(_prefs.value.levelIndex) { STOCKFISH_LEVELS[3] }
            .copy(multiPv = maxOf(3, _prefs.value.multiPv))
        engine.applySettings(settings)
        engine.startAnalysis(chessGame.getCurrentFen(), settings)
    }

    // -------- Public API --------

    fun setOtbMode(otb: Boolean) {
        _isOtbMode.value = otb
    }

    fun onSquareTap(square: String) {
        val state = gameState.value
        if (state.isGameOver) return

        // In vs-computer mode, lock input when it's the engine's turn or engine is thinking
        if (!_isOtbMode.value && (!state.isWhiteTurn || _isEngineThinking.value)) return

        val prevSelected = state.selectedSquare

        if (prevSelected != null && state.legalMoves.contains(square)) {
            val captured = chessGame.getPieceAt(square) != Piece.NONE
            val moved = chessGame.tryMove(prevSelected, square)
            if (moved) {
                if (!_isTimerRunning.value) startTimer()
                if (_isOtbMode.value) {
                    // OTB: just analyse after each move
                    chessGame.clearArrows()
                    runAnalysis()
                } else {
                    // vs Computer: trigger engine move
                    triggerEngineMove()
                }
                return
            }
        }
        chessGame.selectSquare(square)
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
            _analysisLines.value = emptyList()
            chessGame.clearArrows()
        }
    }

    // -------- Timer --------

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
                    if (remaining <= 0) {
                        // Black wins on time
                        break
                    }
                } else {
                    val remaining = _blackTimeSecs.value - 1
                    _blackTimeSecs.value = maxOf(0, remaining)
                    if (remaining <= 0) break
                }
            }
        }
    }

    fun toggleTimer() {
        _isTimerPaused.value = !_isTimerPaused.value
    }

    // -------- Game control --------

    fun newGame(playAsWhite: Boolean = true, otbMode: Boolean = true) {
        timerJob?.cancel()
        engine.stop()
        _isEngineThinking.value = false
        _engineEval.value = 0f
        _analysisLines.value = emptyList()
        _isOtbMode.value = otbMode
        _whiteTimeSecs.value = DEFAULT_TIME_SECS
        _blackTimeSecs.value = DEFAULT_TIME_SECS
        _isTimerRunning.value = false
        _isTimerPaused.value = false
        chessGame.reset()

        viewModelScope.launch {
            // Give the engine a moment to process the stop, then analyse the start position
            delay(400)
            if (otbMode) {
                runAnalysis()
            } else if (!playAsWhite) {
                triggerEngineMove()
            }
        }
    }

    fun navigateBack() {
        engine.stop()
        _isEngineThinking.value = false
        chessGame.navigateBack()
        chessGame.clearArrows()
        if (_engineEnabled.value) runAnalysis()
    }

    fun navigateForward() {
        chessGame.navigateForward()
        chessGame.clearArrows()
        if (_engineEnabled.value) runAnalysis()
    }

    /** OTB undo: take back the last half-move only. */
    fun undoOtbMove() {
        engine.stop()
        _isEngineThinking.value = false
        chessGame.undoMove()
        chessGame.clearArrows()
        if (_engineEnabled.value) runAnalysis()
    }

    /** vs-Computer undo: take back two half-moves (player + engine). */
    fun undoMove() {
        engine.stop()
        _isEngineThinking.value = false
        chessGame.undoMove()
        chessGame.undoMove()
    }

    fun flipBoard() {
        chessGame.flipBoard()
    }

    // -------- Pref saves --------

    fun saveLevel(levelIndex: Int) {
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { prefs ->
                prefs[PrefKeys.LEVEL] = levelIndex
                val settings = STOCKFISH_LEVELS.getOrElse(levelIndex) { STOCKFISH_LEVELS[3] }
                prefs[PrefKeys.SEARCH_TIME] = settings.searchTimeMs
                prefs[PrefKeys.MULTI_PV]    = settings.multiPv
                prefs[PrefKeys.THREADS]     = settings.threads
            }
        }
    }

    fun saveSearchTime(ms: Int) {
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { it[PrefKeys.SEARCH_TIME] = ms }
        }
    }

    fun saveMultiPv(v: Int) {
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { it[PrefKeys.MULTI_PV] = v }
        }
    }

    fun saveThreads(v: Int) {
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { it[PrefKeys.THREADS] = v }
        }
    }

    override fun onCleared() {
        super.onCleared()
        engine.quit()
    }
}
