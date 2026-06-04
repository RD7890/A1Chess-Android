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
import com.ryzix.rdchess.chess.ChessGame
import com.ryzix.rdchess.chess.GameState
import com.ryzix.rdchess.chess.STOCKFISH_LEVELS
import com.ryzix.rdchess.chess.StockfishEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "a1chess_prefs")

object PrefKeys {
    val LEVEL = intPreferencesKey("engine_level")
    val SEARCH_TIME = intPreferencesKey("search_time_ms")
    val MULTI_PV = intPreferencesKey("multi_pv")
    val THREADS = intPreferencesKey("threads")
    val PLAY_AS_WHITE = intPreferencesKey("play_as_white")
    val SHOW_ARROWS = intPreferencesKey("show_arrows")
}

data class AppPrefs(
    val levelIndex: Int = 3,
    val searchTimeMs: Int = 1000,
    val multiPv: Int = 1,
    val threads: Int = 1,
    val playAsWhite: Boolean = true,
    val showArrows: Boolean = true,
)

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val chessGame = ChessGame()
    val gameState: StateFlow<GameState> = chessGame.state

    private val engine = StockfishEngine(application)
    private var engineReady = false

    private val _engineEval = MutableStateFlow(0f)
    val engineEval: StateFlow<Float> = _engineEval

    private val _isEngineThinking = MutableStateFlow(false)
    val isEngineThinking: StateFlow<Boolean> = _isEngineThinking

    private val _prefs = MutableStateFlow(AppPrefs())
    val prefs: StateFlow<AppPrefs> = _prefs

    private val _moveSound = MutableStateFlow<String?>(null)
    val moveSound: StateFlow<String?> = _moveSound

    init {
        loadPrefs()
        initEngine()
        collectEngineOutput()
    }

    private fun loadPrefs() {
        viewModelScope.launch {
            getApplication<Application>().dataStore.data.collect { data ->
                _prefs.value = AppPrefs(
                    levelIndex = data[PrefKeys.LEVEL] ?: 3,
                    searchTimeMs = data[PrefKeys.SEARCH_TIME] ?: 1000,
                    multiPv = data[PrefKeys.MULTI_PV] ?: 1,
                    threads = data[PrefKeys.THREADS] ?: 1,
                    playAsWhite = (data[PrefKeys.PLAY_AS_WHITE] ?: 1) == 1,
                    showArrows = (data[PrefKeys.SHOW_ARROWS] ?: 1) == 1,
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
        viewModelScope.launch {
            engine.bestMoveFlow.collect { bestMove ->
                _isEngineThinking.value = false
                val state = gameState.value
                if (!state.isGameOver && !state.isWhiteTurn) {
                    val from = bestMove.substring(0, 2)
                    val to = bestMove.substring(2, 4)
                    delay(300)
                    val captured = chessGame.getPieceAt(to).value() != "."
                    chessGame.tryMove(from, to)
                    _moveSound.value = if (captured) "capture" else "move"
                    _moveSound.value = null
                }
            }
        }
        viewModelScope.launch {
            engine.evalFlow.collect { eval ->
                _engineEval.value = eval
            }
        }
    }

    fun onSquareTap(square: String) {
        val state = gameState.value
        if (state.isGameOver || !state.isWhiteTurn || _isEngineThinking.value) return

        val prevSelected = state.selectedSquare

        if (prevSelected != null && state.legalMoves.contains(square)) {
            val captured = chessGame.getPieceAt(square) != Piece.NONE
            val moved = chessGame.tryMove(prevSelected, square)
            if (moved) {
                _moveSound.value = if (captured) "capture" else "move"
                _moveSound.value = null
                triggerEngineMove()
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

    fun newGame(playAsWhite: Boolean = true) {
        chessGame.reset()
        _engineEval.value = 0f
        _isEngineThinking.value = false
        if (!playAsWhite) {
            viewModelScope.launch {
                delay(500)
                triggerEngineMove()
            }
        }
    }

    fun undoMove() {
        engine.stop()
        _isEngineThinking.value = false
        chessGame.undoMove()
        chessGame.undoMove()
    }

    fun flipBoard() {
        chessGame.flipBoard()
    }

    fun saveLevel(levelIndex: Int) {
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { prefs ->
                prefs[PrefKeys.LEVEL] = levelIndex
                val settings = STOCKFISH_LEVELS.getOrElse(levelIndex) { STOCKFISH_LEVELS[3] }
                prefs[PrefKeys.SEARCH_TIME] = settings.searchTimeMs
                prefs[PrefKeys.MULTI_PV] = settings.multiPv
                prefs[PrefKeys.THREADS] = settings.threads
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
