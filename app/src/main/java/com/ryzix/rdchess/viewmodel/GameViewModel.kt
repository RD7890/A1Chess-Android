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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

enum class MoveGrade(val label: String, val symbol: String, val colorHex: Long, val iconFilename: String) {
    BRILLIANT("Brilliant", "!!", 0xFF00BCD4, "Brilliant.png"),
    BEST("Best Move", "!",  0xFF4CAF50, "Best.png"),
    GOOD("Good",     "+",  0xFF8BC34A, "Excellent.png"),
    INACCURACY("Inaccuracy", "?!", 0xFFFF9800, "Inaccuracy.png"),
    MISTAKE("Mistake", "?", 0xFFFF5722, "Mistake.png"),
    BLUNDER("Blunder", "??", 0xFFF44336, "Blunder.png"),
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
    /** Move grades keyed by GameState.currentMoveIndex at the time each move was played. */
    val grades: Map<Int, MoveGrade> = emptyMap(),
) {
    /**
     * Returns a pipe-delimited record with 5 fields.
     * Field 5 = grades encoded as "idx:GRADE_NAME;idx:GRADE_NAME;…"
     * movesUci and grade names must not contain pipes.
     */
    fun toRecord(): String {
        val gradesEncoded = grades.entries.joinToString(";") { "${it.key}:${it.value.name}" }
        return "$result|$moveCount|$timestamp|$movesUci|$gradesEncoded"
    }

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
            val p = s.split("|", limit = 5)
            if (p.size < 3) return null
            val grades: Map<Int, MoveGrade> = if (p.size >= 5 && p[4].isNotBlank()) {
                p[4].split(";").mapNotNull { entry ->
                    val parts = entry.split(":")
                    if (parts.size == 2) {
                        val idx   = parts[0].toIntOrNull() ?: return@mapNotNull null
                        val grade = runCatching { MoveGrade.valueOf(parts[1]) }.getOrNull()
                            ?: return@mapNotNull null
                        idx to grade
                    } else null
                }.toMap()
            } else emptyMap()
            return SavedGame(
                result    = p[0],
                moveCount = p[1].toIntOrNull() ?: 0,
                timestamp = p[2].toLongOrNull() ?: 0L,
                movesUci  = if (p.size >= 4) p[3] else "",
                grades    = grades,
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

    /** Manages engine binary discovery and Stockfish download. */
    val engineManager = EngineManager(application)

    private val _selectedEngineType = MutableStateFlow(EngineType.RYZIX)
    val selectedEngineType: StateFlow<EngineType> = _selectedEngineType

    private val _isStockfishAvailable = MutableStateFlow(false)
    val isStockfishAvailable: StateFlow<Boolean> = _isStockfishAvailable

    /** null = idle, 0-100 = download progress percent. */
    private val _downloadProgress = MutableStateFlow<Int?>(null)
    val downloadProgress: StateFlow<Int?> = _downloadProgress

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

    /** Which color the human player is playing as (true = White, false = Black). */
    private val _playerIsWhite = MutableStateFlow(true)
    val playerIsWhite: StateFlow<Boolean> = _playerIsWhite

    private val _gameHistory = MutableStateFlow<List<SavedGame>>(emptyList())
    val gameHistory: StateFlow<List<SavedGame>> = _gameHistory

    /** True when viewing a loaded history/PGN game (read-only review). */
    private val _isReviewMode = MutableStateFlow(false)
    val isReviewMode: StateFlow<Boolean> = _isReviewMode

    @Volatile private var isGradingPending = false
    @Volatile private var preMoveEval = 0f
    @Volatile private var preMoveLines: List<AnalysisLine> = emptyList()

    /**
     * Grades stored by move index so they survive back/forward navigation.
     * Key = GameState.currentMoveIndex at the time the move was played.
     */
    private val moveGrades = mutableMapOf<Int, MoveGradeResult>()

    /** True only while the engine is computing its reply move (triggerEngineMove was called).
     *  Distinct from _isEngineThinking which also covers analysis. Used to block player taps
     *  only when it is genuinely the engine's turn to move, not during post-move analysis. */
    @Volatile private var engineMoveInFlight = false

    /** Prevents multiple newGame resets when first composing the Play screen. */
    private var gameInitialized = false

    // External storage dirs — created once on init, used for PGN file saving.
    // These land in Android/data/<package>/files/games/ which is visible in file managers.
    private val gamesDir: File? by lazy {
        getApplication<Application>().getExternalFilesDir("games")?.also { it.mkdirs() }
    }
    private val backupsDir: File? by lazy {
        getApplication<Application>().getExternalFilesDir("backups")?.also { it.mkdirs() }
    }

    init {
        // Detect available engines before starting
        _isStockfishAvailable.value = engineManager.isStockfishAvailable()
        // Default to Ryzix (bundled). Fall back to Stockfish if Ryzix binary is absent.
        if (!engineManager.isRyzixAvailable() && engineManager.isStockfishAvailable()) {
            _selectedEngineType.value = EngineType.STOCKFISH
        }
        loadPrefs()
        initEngine()
        collectEngineOutput()
        initAppFolders()
    }

    /** Create all app-specific external folders so they appear in file managers immediately. */
    private fun initAppFolders() {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            // Android/data/<package>/files/games/  — PGN files
            app.getExternalFilesDir("games")?.mkdirs()
            // Android/data/<package>/files/backups/ — history backups
            app.getExternalFilesDir("backups")?.mkdirs()
            // Android/data/<package>/files/media/  — sounds, assets mirror
            app.getExternalFilesDir("media")?.mkdirs()
            // Android/obb/<package>/               — OBB slot (engine expansion)
            app.obbDir?.mkdirs()
            // Android/media/<package>/             — shared media slot
            app.externalMediaDirs.firstOrNull()?.mkdirs()
        }
    }

    /**
     * Write a PGN file to Android/data/<package>/files/games/<timestamp>_<result>.pgn
     * Runs on IO dispatcher. Silent on failure (external storage may be unavailable).
     */
    private suspend fun savePgnFile(game: SavedGame) = withContext(Dispatchers.IO) {
        try {
            val dir = gamesDir ?: return@withContext
            val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(game.timestamp))
            val resultTag = game.result.replace("/", "-")
            val file = File(dir, "${dateStr}_${resultTag}.pgn")
            file.writeText(game.generatePgn())
        } catch (_: Exception) { }
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
            val binary = engineManager.getBinaryFor(_selectedEngineType.value)
            engineReady = engine.init(binary)
            _engineAvailable.value = engineReady
            if (engineReady) {
                // Ryzix doesn't support MultiPV / Skill Level UCI options — only apply to Stockfish
                if (_selectedEngineType.value == EngineType.STOCKFISH) {
                    val settings = STOCKFISH_LEVELS.getOrElse(_prefs.value.levelIndex) { STOCKFISH_LEVELS[3] }
                    engine.applySettings(settings)
                }
                delay(300)
                runAnalysis()
            }
        }
    }

    /** Switch the active engine. Downloads Stockfish first if it isn't available yet. */
    fun selectEngine(type: EngineType) {
        if (type == EngineType.STOCKFISH && !engineManager.isStockfishAvailable()) {
            downloadStockfish()
            return
        }
        if (_selectedEngineType.value == type) return
        _selectedEngineType.value = type
        engine.quit()
        engineReady = false
        _engineAvailable.value = false
        _analysisLines.value = emptyList()
        initEngine()
    }

    /** Download Stockfish in the background and switch to it on success. */
    fun downloadStockfish() {
        if (_downloadProgress.value != null) return   // already in progress
        viewModelScope.launch {
            _downloadProgress.value = 0
            val success = engineManager.downloadStockfish { pct -> _downloadProgress.value = pct }
            _downloadProgress.value = null
            _isStockfishAvailable.value = engineManager.isStockfishAvailable()
            if (success) selectEngine(EngineType.STOCKFISH)
        }
    }

    private fun collectEngineOutput() {
        // bestmove — genuine analysis completion (stop-for-restart bestmoves are suppressed in engine)
        viewModelScope.launch {
            engine.bestMoveFlow.collect { bestMove ->
                _isEngineThinking.value = false
                engineMoveInFlight = false   // always clear — move is done regardless of whose turn it was

                // Update arrows on final bestmove
                if (_engineEnabled.value && _prefs.value.showArrows && _analysisLines.value.isNotEmpty()) {
                    updateArrows(_analysisLines.value)
                }

                // Grade the move just played (OTB mode, Stockfish only — Ryzix has no multipv)
                if (_isOtbMode.value && isGradingPending && _analysisLines.value.isNotEmpty()
                        && _selectedEngineType.value == EngineType.STOCKFISH) {
                    isGradingPending = false
                    val postEval = _analysisLines.value.firstOrNull()?.eval ?: _engineEval.value
                    gradeLastMove(postEval)
                }

                // vs-Computer mode: engine makes its reply after the human moves
                if (!_isOtbMode.value && !gameState.value.isGameOver) {
                    val isEngineTurn = if (_playerIsWhite.value)
                        !gameState.value.isWhiteTurn   // engine plays Black
                    else
                        gameState.value.isWhiteTurn    // engine plays White

                    if (isEngineTurn) {
                        val from  = bestMove.substring(0, 2)
                        val to    = bestMove.substring(2, 4)
                        val promo = if (bestMove.length >= 5) bestMove[4] else 'q'
                        delay(300)
                        chessGame.tryMove(from, to, promo)
                        runAnalysis()
                    }
                }
            }
        }

        // eval — update value only
        viewModelScope.launch {
            engine.evalFlow.collect { eval ->
                _engineEval.value = eval
            }
        }

        // analysis lines — update UI; arrows updated only on bestmove above
        viewModelScope.launch {
            engine.analysisFlow.collect { lines ->
                _analysisLines.value = lines
            }
        }
    }

    // ── Move grading ──────────────────────────────────────────────────────────

    private fun gradeLastMove(postEval: Float) {
        // Bug fix: if preMoveLines was empty when the move was made (engine hadn't finished
        // yet), we have no reliable best-move reference — skip grading entirely rather than
        // producing a meaningless result.
        if (preMoveLines.isEmpty()) return

        // cpLoss: Stockfish reports eval from the side-to-move's perspective.
        // preMoveEval is from the mover's POV (+ve = good for mover).
        // postEval is from the OPPONENT's POV after the move (+ve = good for opponent).
        // So net gain for the mover = preMoveEval + postEval (postEval is negative when
        // the opponent is losing, i.e. the mover is still winning).
        // A positive cpLoss means the mover LOST ground; negative means they GAINED.
        val cpLoss = preMoveEval + postEval

        val preBestUci = preMoveLines.firstOrNull()?.move ?: ""
        val lastMove   = gameState.value.moves.lastOrNull()
        val playedUci  = if (lastMove != null) "${lastMove.from}${lastMove.to}" else ""

        // Guard: if preBestUci is blank the comparison is meaningless.
        val isBestMove = preBestUci.isNotBlank() &&
                playedUci.take(4) == preBestUci.take(4)

        val grade = when {
            // Best: played the exact top engine move with negligible loss.
            isBestMove && cpLoss <= 0.10f -> MoveGrade.BEST
            // Good: any move that keeps the evaluation very close (includes near-best alts).
            // Brilliant is NOT auto-assigned here — real brilliancies need sacrifice detection
            // which requires deeper analysis; auto-assigning it on cpLoss alone was wrong.
            cpLoss <= 0.30f              -> MoveGrade.GOOD
            cpLoss <= 0.75f              -> MoveGrade.INACCURACY
            cpLoss <= 1.50f              -> MoveGrade.MISTAKE
            else                         -> MoveGrade.BLUNDER
        }

        val result = MoveGradeResult(
            grade = grade, playedUci = playedUci, bestUci = preBestUci, cpLoss = cpLoss,
        )
        // Store keyed by current move index so back/forward navigation can restore it
        moveGrades[gameState.value.currentMoveIndex] = result
        _lastMoveGrade.value = result
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

    /** Run Stockfish analysis — deep search, minimum 3 s for decisive arrows. */
    private fun runAnalysis() {
        if (!engineReady || !_engineEnabled.value) return
        val base = STOCKFISH_LEVELS.getOrElse(_prefs.value.levelIndex) { STOCKFISH_LEVELS[3] }
        val settings = base.copy(
            multiPv      = maxOf(3, _prefs.value.multiPv),
            searchTimeMs = maxOf(3000, base.searchTimeMs),
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
        if (_isReviewMode.value) return

        // In vs-computer mode, only allow taps on the player's own turn.
        // Block on engineMoveInFlight (not _isEngineThinking) so the player can still
        // interact during post-move analysis without being locked out.
        if (!_isOtbMode.value) {
            val isPlayerTurn = if (_playerIsWhite.value) state.isWhiteTurn else !state.isWhiteTurn
            if (!isPlayerTurn || engineMoveInFlight) return
        }

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
                    _engineEval.value = 0f
                    runAnalysis()
                    if (gameState.value.isGameOver) saveCurrentGame()
                } else {
                    _analysisLines.value = emptyList()
                    _engineEval.value = 0f
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
                _engineEval.value = 0f
                runAnalysis()
                if (gameState.value.isGameOver) saveCurrentGame()
            } else {
                _analysisLines.value = emptyList()
                _engineEval.value = 0f
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
        engineMoveInFlight = true
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

    /**
     * Start a new game.
     * @param otbMode  true = Over-the-Board (both sides human / analysis), false = vs computer
     * @param playerIsWhite  which color the human plays (only relevant when otbMode = false)
     */
    fun newGame(otbMode: Boolean = true, playerIsWhite: Boolean = true) {
        engine.stop()
        _isEngineThinking.value = false
        engineMoveInFlight = false
        _engineEval.value = 0f
        _analysisLines.value = emptyList()
        _lastMoveGrade.value = null
        _promotionPending.value = null
        _isReviewMode.value = false
        isGradingPending = false
        moveGrades.clear()
        _isOtbMode.value = otbMode
        _playerIsWhite.value = playerIsWhite
        chessGame.reset()
        chessGame.clearArrows()   // clear immediately — don't wait for engine to finish

        // Board orientation: flip when Black is at bottom.
        // In OTB mode playerIsWhite controls which side faces you (white=false → black at bottom).
        // In vs-computer mode same logic applies.
        chessGame.setFlipped(!playerIsWhite)

        // Clear persisted game
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { it[PrefKeys.CURRENT_GAME_MOVES] = "" }
        }

        viewModelScope.launch {
            delay(400)
            if (otbMode) {
                runAnalysis()
            } else if (!playerIsWhite) {
                // Player is Black — engine plays White's first move
                triggerEngineMove()
            } else {
                // Player is White — show analysis, wait for player
                runAnalysis()
            }
        }
    }

    fun navigateBack() {
        engine.stop()
        _isEngineThinking.value = false
        isGradingPending = false   // cancel stale grade — post-nav analysis is a different position
        chessGame.navigateBack()
        chessGame.clearArrows()
        // Restore the stored grade for the move now at the current index
        _lastMoveGrade.value = moveGrades[chessGame.state.value.currentMoveIndex]
        if (_engineEnabled.value) runAnalysis()
    }

    fun navigateForward() {
        isGradingPending = false   // same reason as navigateBack
        chessGame.navigateForward()
        chessGame.clearArrows()
        // Restore the stored grade for the move now at the current index
        _lastMoveGrade.value = moveGrades[chessGame.state.value.currentMoveIndex]
        if (_engineEnabled.value) runAnalysis()
    }

    fun undoOtbMove() {
        if (_isReviewMode.value) return
        engine.stop()
        _isEngineThinking.value = false
        isGradingPending = false
        val undoneIndex = chessGame.state.value.currentMoveIndex
        chessGame.undoMove()
        // In vs-computer mode, undo the engine's move too so player is back at their turn
        if (!_isOtbMode.value) chessGame.undoMove()
        moveGrades.remove(undoneIndex)   // discard grade for the undone move
        _lastMoveGrade.value = moveGrades[chessGame.state.value.currentMoveIndex]
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
        isGradingPending = false
        moveGrades.clear()

        if (game.movesUci.isNotBlank()) {
            chessGame.loadFromMoves(game.movesUci)
        } else {
            chessGame.reset()
        }
        chessGame.clearArrows()

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
            isGradingPending = false
            moveGrades.clear()
            chessGame.clearArrows()
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

    // ── Active-game helpers ───────────────────────────────────────────────────

    /** True when a live (non-review, non-over) game with at least one move is in progress. */
    fun hasActiveGame(): Boolean =
        gameState.value.moves.isNotEmpty() &&
        !gameState.value.isGameOver &&
        !_isReviewMode.value

    /**
     * Save the current in-progress game to history with result = "*".
     * Replaces any previous "*" save so there is only ever one in-progress slot.
     * Grades are included so they survive app restarts.
     */
    fun saveGameInProgress() {
        val movesUci = chessGame.getMovesAsUci()
        if (movesUci.isBlank()) return
        val state = gameState.value
        val game = SavedGame(
            result    = "*",
            moveCount = state.moves.size,
            timestamp = System.currentTimeMillis(),
            movesUci  = movesUci,
            grades    = moveGrades.mapValues { it.value.grade },
        )
        viewModelScope.launch {
            val current = _gameHistory.value.toMutableList()
            current.removeAll { it.result == "*" }   // keep only one in-progress slot
            current.add(0, game)
            // No cap — every game is kept forever
            _gameHistory.value = current
            val encoded = current.joinToString("\n") { g -> g.toRecord() }
            getApplication<Application>().dataStore.edit { prefs ->
                prefs[PrefKeys.GAME_HISTORY]       = encoded
                prefs[PrefKeys.CURRENT_GAME_MOVES] = ""
            }
        }
    }

    /**
     * Resume an in-progress game that was saved to history.
     * Loads the moves, removes the "*" history entry, and lets the player continue.
     */
    fun continueGameFromHistory(game: SavedGame) {
        engine.stop()
        _isEngineThinking.value = false
        _analysisLines.value = emptyList()
        _lastMoveGrade.value = null
        _promotionPending.value = null
        _isReviewMode.value = false
        _isOtbMode.value = true   // resume as Pass & Play (both sides human), not vs AI
        isGradingPending = false
        moveGrades.clear()

        if (game.movesUci.isNotBlank()) {
            chessGame.loadFromMoves(game.movesUci)
        } else {
            chessGame.reset()
        }
        chessGame.clearArrows()

        // Remove the in-progress entry from history — it's live again
        viewModelScope.launch {
            val current = _gameHistory.value.toMutableList()
            val idx = current.indexOfFirst { it.timestamp == game.timestamp }
            if (idx >= 0) current.removeAt(idx)
            _gameHistory.value = current
            val encoded = current.joinToString("\n") { g -> g.toRecord() }
            getApplication<Application>().dataStore.edit { prefs ->
                prefs[PrefKeys.GAME_HISTORY]       = encoded
                prefs[PrefKeys.CURRENT_GAME_MOVES] = game.movesUci
            }
            delay(300)
            if (_engineEnabled.value) runAnalysis()
        }
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
            grades    = moveGrades.mapValues { it.value.grade },
        )
        viewModelScope.launch {
            val current = _gameHistory.value.toMutableList()
            current.add(0, game)
            // No cap — every completed game is kept forever
            _gameHistory.value = current
            val encoded = current.joinToString("\n") { g -> g.toRecord() }
            getApplication<Application>().dataStore.edit { prefs ->
                prefs[PrefKeys.GAME_HISTORY] = encoded
                prefs[PrefKeys.CURRENT_GAME_MOVES] = ""  // clear in-progress save
            }
            // Also write a PGN file to Android/data/<package>/files/games/
            savePgnFile(game)
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
