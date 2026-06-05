package com.ryzix.rdchess.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ryzix.rdchess.chess.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "a1chess_prefs")

object PrefKeys {
    val LEVEL              = intPreferencesKey("engine_level")
    val SEARCH_TIME        = intPreferencesKey("search_time_ms")
    val MULTI_PV           = intPreferencesKey("multi_pv")
    val THREADS            = intPreferencesKey("threads")
    val PLAY_AS_WHITE      = intPreferencesKey("play_as_white")
    val SHOW_ARROWS        = intPreferencesKey("show_arrows")
    val GAME_HISTORY       = stringPreferencesKey("game_history")
    val CURRENT_GAME_MOVES = stringPreferencesKey("current_game_moves")
}

data class AppPrefs(
    val levelIndex: Int = 3,
    val searchTimeMs: Int = 3000,
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
    val movesUci: String = "",
) {
    /** Returns a pipe-delimited record. movesUci cannot contain pipes. */
    fun toRecord() = "$result|$moveCount|$timestamp|$movesUci"

    fun generatePgn(): String {
        if (movesUci.isBlank()) return "[Result \"$result\"]\n\n$result"
        val game = ChessGame()
        movesUci.split(",").forEach { uci ->
            if (uci.length >= 4) {
                val from  = uci.substring(0, 2)
                val to    = uci.substring(2, 4)
                val promo = if (uci.length >= 5) uci[4] else 'q'
                game.tryMove(from, to, promo)
            }
        }
        return game.generatePgn(result)
    }

    companion object {
        fun fromRecord(s: String): SavedGame? {
            val p = s.split("|", limit = 4)
            if (p.size < 3) return null
            return SavedGame(
                result    = p[0],
                moveCount = p[1].toIntOrNull() ?: 0,
                timestamp = p[2].toLongOrNull() ?: 0L,
                movesUci  = if (p.size >= 4) p[3] else "",
            )
        }
    }
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

class GameViewModel(application: Application) : AndroidViewModel(application) {

    val chessGame = ChessGame()
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

    private val _promotionPending = MutableStateFlow<Pair<String, String>?>(null)
    val promotionPending: StateFlow<Pair<String, String>?> = _promotionPending

    private val _isOtbMode = MutableStateFlow(true)
    val isOtbMode: StateFlow<Boolean> = _isOtbMode

    private val _gameHistory = MutableStateFlow<List<SavedGame>>(emptyList())
    val gameHistory: StateFlow<List<SavedGame>> = _gameHistory

    /** True when viewing a loaded history/PGN game (read-only review). */
    private val _isReviewMode = MutableStateFlow(false)
    val isReviewMode: StateFlow<Boolean> = _isReviewMode

    @Volatile private var isGradingPending = false
    @Volatile private var preMoveEval = 0f
    @Volatile private var preMoveLines: List<AnalysisLine> = emptyList()

    /** Prevents multiple newGame resets when first composing the Play screen. */
    private var gameInitialized = false

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
                    searchTimeMs = data[PrefKeys.SEARCH_TIME] ?: 3000,
                    multiPv      = data[PrefKeys.MULTI_PV] ?: 3,
                    threads      = data[PrefKeys.THREADS] ?: 1,
                    playAsWhite  = (data[PrefKeys.PLAY_AS_WHITE] ?: 1) == 1,
                    showArrows   = (data[PrefKeys.SHOW_ARROWS] ?: 1) == 1,
                )
                val raw = data[PrefKeys.GAME_HISTORY] ?: ""
                _gameHistory.value = raw
                    .split("\n")
                    .filter { it.isNotBlank() }
                    .mapNotNull { SavedGame.fromRecord(it) }

                // Restore in-progress game on first load
                if (!gameInitialized) {
                    val savedMoves = data[PrefKeys.CURRENT_GAME_MOVES] ?: ""
                    if (savedMoves.isNotBlank()) {
                        chessGame.loadFromMoves(savedMoves)
                    }
                    gameInitialized = true
                }
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
        // bestmove — marks end of thinking; update arrows ONLY here (stable, definitive result)
        viewModelScope.launch {
            engine.bestMoveFlow.collect { bestMove ->
                _isEngineThinking.value = false

                // Update arrows only on final bestmove — no mid-search flicker
                if (_engineEnabled.value && _prefs.value.showArrows && _analysisLines.value.isNotEmpty()) {
                    updateArrows(_analysisLines.value)
                }

                // Grade the move just played (OTB mode)
                if (_isOtbMode.value && isGradingPending && _analysisLines.value.isNotEmpty()) {
                    isGradingPending = false
                    val postEval = _analysisLines.value.firstOrNull()?.eval ?: _engineEval.value
                    gradeLastMove(postEval)
                }

                // vs-Computer mode: engine makes its reply
                if (!_isOtbMode.value && !gameState.value.isGameOver && !gameState.value.isWhiteTurn) {
                    val from  = bestMove.substring(0, 2)
                    val to    = bestMove.substring(2, 4)
                    val promo = if (bestMove.length >= 5) bestMove[4] else 'q'
                    delay(300)
                    chessGame.tryMove(from, to, promo)
                    runAnalysis()
                }
            }
        }

        // eval — update value only
        viewModelScope.launch {
            engine.evalFlow.collect { eval ->
                _engineEval.value = eval
            }
        }

        // analysis lines — update UI display only; arrows updated only on bestmove above
        viewModelScope.launch {
            engine.analysisFlow.collect { lines ->
                _analysisLines.value = lines
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

    /** Run Stockfish analysis — deep search, minimum 3 s. */
    private fun runAnalysis() {
        if (!engineReady || !_engineEnabled.value) return
        val base = STOCKFISH_LEVELS.getOrElse(_prefs.value.levelIndex) { STOCKFISH_LEVELS[3] }
        val settings = base.copy(
            multiPv      = maxOf(3, _prefs.value.multiPv),
            searchTimeMs = maxOf(3000, base.searchTimeMs), // minimum 3 s for decisive arrows
        )
        engine.applySettings(settings)
        _isEngineThinking.value = true
        engine.startAnalysis(chessGame.getCurrentFen(), settings)
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    fun setOtbMode(otb: Boolean) { _isOtbMode.value = otb }

    fun onSquareTap(square: String) {
        val state = gameState.value
        if (state.isGameOver) return
        if (_isReviewMode.value) return  // no moves in review mode
        if (!_isOtbMode.value && (!state.isWhiteTurn || _isEngineThinking.value)) return

        val prevSelected = state.selectedSquare

        if (prevSelected != null && state.legalMoves.contains(square)) {
            if (chessGame.isPromotionMove(prevSelected, square)) {
                _promotionPending.value = Pair(prevSelected, square)
                chessGame.selectSquare(prevSelected)
                return
            }

            val moved = chessGame.tryMove(prevSelected, square)
            if (moved) {
                chessGame.clearArrows()
                persistCurrentGame()

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
                return
            }
        }
        chessGame.selectSquare(square)
    }

    fun confirmPromotion(promoChar: Char) {
        val pending = _promotionPending.value ?: return
        _promotionPending.value = null

        val moved = chessGame.tryMove(pending.first, pending.second, promoChar)
        if (moved) {
            chessGame.clearArrows()
            persistCurrentGame()

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

    // ── Game control ───────────────────────────────────────────────────────────

    fun newGame(otbMode: Boolean = true) {
        engine.stop()
        _isEngineThinking.value = false
        _engineEval.value = 0f
        _analysisLines.value = emptyList()
        _lastMoveGrade.value = null
        _promotionPending.value = null
        _isReviewMode.value = false
        isGradingPending = false
        _isOtbMode.value = otbMode
        chessGame.reset()

        // Clear persisted game
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { it[PrefKeys.CURRENT_GAME_MOVES] = "" }
        }

        viewModelScope.launch {
            delay(400)
            if (otbMode) runAnalysis()
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
        if (_isReviewMode.value) return
        engine.stop()
        _isEngineThinking.value = false
        _lastMoveGrade.value = null
        chessGame.undoMove()
        chessGame.clearArrows()
        persistCurrentGame()
        if (_engineEnabled.value) runAnalysis()
    }

    fun undoMove() {
        engine.stop()
        _isEngineThinking.value = false
        chessGame.undoMove()
        chessGame.undoMove()
    }

    fun flipBoard() { chessGame.flipBoard() }

    // ── History game loading ───────────────────────────────────────────────────

    /** Load a completed game for review — no new moves allowed. */
    fun loadGameFromHistory(game: SavedGame) {
        engine.stop()
        _isEngineThinking.value = false
        _analysisLines.value = emptyList()
        _lastMoveGrade.value = null
        _promotionPending.value = null
        _isReviewMode.value = true
        _isOtbMode.value = true

        if (game.movesUci.isNotBlank()) {
            chessGame.loadFromMoves(game.movesUci)
        } else {
            chessGame.reset()
        }

        viewModelScope.launch {
            delay(200)
            if (_engineEnabled.value) runAnalysis()
        }
    }

    /** Load a game from a pasted PGN string for analysis. */
    fun loadGameFromPgn(pgn: String): Boolean {
        val ok = chessGame.loadFromPgn(pgn)
        if (ok) {
            engine.stop()
            _isEngineThinking.value = false
            _analysisLines.value = emptyList()
            _lastMoveGrade.value = null
            _promotionPending.value = null
            _isReviewMode.value = true
            _isOtbMode.value = true
            viewModelScope.launch {
                delay(200)
                if (_engineEnabled.value) runAnalysis()
            }
        }
        return ok
    }

    // ── PGN export ────────────────────────────────────────────────────────────

    fun getCurrentGamePgn(): String {
        val result = gameState.value.gameResult ?: "*"
        return chessGame.generatePgn(result)
    }

    fun sharePgn(pgn: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Chess Game PGN")
            putExtra(Intent.EXTRA_TEXT, pgn)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        getApplication<Application>().startActivity(
            Intent.createChooser(intent, "Export PGN").also {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    // ── Game history ───────────────────────────────────────────────────────────

    private fun saveCurrentGame() {
        val state = gameState.value
        val result = state.gameResult ?: return
        val movesUci = chessGame.getMovesAsUci()
        val game = SavedGame(
            result    = result,
            moveCount = state.moves.size,
            timestamp = System.currentTimeMillis(),
            movesUci  = movesUci,
        )
        viewModelScope.launch {
            val current = _gameHistory.value.toMutableList()
            current.add(0, game)
            val kept = current.take(50)
            _gameHistory.value = kept
            val encoded = kept.joinToString("\n") { g -> g.toRecord() }
            getApplication<Application>().dataStore.edit { prefs ->
                prefs[PrefKeys.GAME_HISTORY] = encoded
                prefs[PrefKeys.CURRENT_GAME_MOVES] = ""  // clear in-progress save
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            _gameHistory.value = emptyList()
            getApplication<Application>().dataStore.edit { it[PrefKeys.GAME_HISTORY] = "" }
        }
    }

    // ── Game persistence (crash recovery) ────────────────────────────────────

    private fun persistCurrentGame() {
        if (_isReviewMode.value) return
        viewModelScope.launch {
            val movesUci = chessGame.getMovesAsUci()
            getApplication<Application>().dataStore.edit {
                it[PrefKeys.CURRENT_GAME_MOVES] = movesUci
            }
        }
    }

    // ── Pref saves ─────────────────────────────────────────────────────────────

    fun saveLevel(levelIndex: Int) {
        val settings = STOCKFISH_LEVELS.getOrElse(levelIndex) { STOCKFISH_LEVELS[3] }
        _prefs.value = _prefs.value.copy(
            levelIndex   = levelIndex,
            searchTimeMs = maxOf(3000, settings.searchTimeMs),
            multiPv      = settings.multiPv,
            threads      = settings.threads,
        )
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { prefs ->
                prefs[PrefKeys.LEVEL]       = levelIndex
                prefs[PrefKeys.SEARCH_TIME] = maxOf(3000, settings.searchTimeMs)
                prefs[PrefKeys.MULTI_PV]    = settings.multiPv
                prefs[PrefKeys.THREADS]     = settings.threads
            }
        }
        if (_engineEnabled.value && engineReady) runAnalysis()
    }

    fun saveSearchTime(ms: Int) {
        val clamped = maxOf(3000, ms)
        _prefs.value = _prefs.value.copy(searchTimeMs = clamped)
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { it[PrefKeys.SEARCH_TIME] = clamped }
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
