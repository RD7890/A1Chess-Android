package com.ryzix.rdchess.chess

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date

data class ChessMove(
    val from: String,
    val to: String,
    val san: String,
    val fen: String,
    val isCapture: Boolean = false,
    val promotion: String? = null,
)

data class Arrow(
    val from: String,
    val to: String,
    val color: ArrowColor = ArrowColor.GREEN,
)

enum class ArrowColor { GREEN, BLUE, RED, YELLOW }

const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

data class GameState(
    val fen: String = START_FEN,
    val moves: List<ChessMove> = emptyList(),
    val selectedSquare: String? = null,
    val legalMoves: List<String> = emptyList(),
    val lastMove: Pair<String, String>? = null,
    val arrows: List<Arrow> = emptyList(),
    val isWhiteTurn: Boolean = true,
    val isGameOver: Boolean = false,
    val gameResult: String? = null,
    val isFlipped: Boolean = false,
    val currentMoveIndex: Int = -1,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val checkedKingSquare: String? = null,
)

class ChessGame {
    private val board = Board()
    private val moveHistory = mutableListOf<ChessMove>()
    private val redoStack = mutableListOf<Triple<String, String, Char>>()

    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state

    init {
        board.loadFromFen(START_FEN)
        updateState()
    }

    fun reset(fen: String = START_FEN) {
        board.loadFromFen(fen)
        moveHistory.clear()
        redoStack.clear()
        updateState()
    }

    fun selectSquare(squareName: String): Boolean {
        val square = Square.valueOf(squareName.uppercase())
        val current = _state.value

        if (current.selectedSquare == squareName) {
            _state.value = current.copy(selectedSquare = null, legalMoves = emptyList())
            return false
        }

        if (current.selectedSquare != null) {
            val moved = tryMove(current.selectedSquare!!, squareName)
            if (moved) return true
        }

        val piece = board.getPiece(square)
        if (piece == Piece.NONE) {
            _state.value = current.copy(selectedSquare = null, legalMoves = emptyList())
            return false
        }

        val isCurrentTurnPiece = piece.pieceSide == board.sideToMove
        if (!isCurrentTurnPiece) {
            _state.value = current.copy(selectedSquare = null, legalMoves = emptyList())
            return false
        }

        val legal = board.legalMoves()
            .filter { it.from == square }
            .map { it.to.value().lowercase() }

        _state.value = current.copy(
            selectedSquare = squareName,
            legalMoves = legal,
        )
        return false
    }

    fun isPromotionMove(fromSq: String, toSq: String): Boolean {
        return try {
            val from = Square.valueOf(fromSq.uppercase())
            val to   = Square.valueOf(toSq.uppercase())
            val piece = board.getPiece(from)
            if (piece != Piece.WHITE_PAWN && piece != Piece.BLACK_PAWN) return false
            val lm = board.legalMoves().filter { it.from == from && it.to == to }
            lm.size > 1
        } catch (e: Exception) { false }
    }

    fun tryMove(fromSq: String, toSq: String, promoChar: Char = 'q'): Boolean {
        val from = Square.valueOf(fromSq.uppercase())
        val to   = Square.valueOf(toSq.uppercase())

        val legalMoves = board.legalMoves().filter { it.from == from && it.to == to }
        if (legalMoves.isEmpty()) return false

        val move = if (legalMoves.size > 1) {
            val targetPiece = when (promoChar.lowercaseChar()) {
                'r' -> if (board.sideToMove == Side.WHITE) Piece.WHITE_ROOK   else Piece.BLACK_ROOK
                'b' -> if (board.sideToMove == Side.WHITE) Piece.WHITE_BISHOP else Piece.BLACK_BISHOP
                'n' -> if (board.sideToMove == Side.WHITE) Piece.WHITE_KNIGHT else Piece.BLACK_KNIGHT
                else -> if (board.sideToMove == Side.WHITE) Piece.WHITE_QUEEN else Piece.BLACK_QUEEN
            }
            legalMoves.firstOrNull { it.promotion == targetPiece } ?: legalMoves.first()
        } else legalMoves.first()

        val isCapture = board.getPiece(to) != Piece.NONE ||
            (board.getPiece(from).pieceType == PieceType.PAWN &&
             from.value()[0] != to.value()[0] && board.getPiece(to) == Piece.NONE)

        val san = computeSan(board, move)
        board.doMove(move)

        // Append check/mate suffix
        val suffix = when {
            board.isMated      -> "#"
            board.isKingAttacked -> "+"
            else               -> ""
        }

        val chessMove = ChessMove(
            from = fromSq,
            to = toSq,
            san = san + suffix,
            fen = board.fen,
            isCapture = isCapture,
            promotion = move.promotion.takeIf { it != Piece.NONE }?.name,
        )
        moveHistory.add(chessMove)
        redoStack.clear()
        updateState()
        return true
    }

    fun navigateBack(): Boolean {
        if (moveHistory.isEmpty()) return false
        val last = moveHistory.removeLast()
        redoStack.add(Triple(last.from, last.to, last.promotion?.firstOrNull() ?: 'q'))
        board.undoMove()
        updateState()
        return true
    }

    fun navigateForward(): Boolean {
        if (redoStack.isEmpty()) return false
        val (from, to, promo) = redoStack.removeLast()
        return tryMove(from, to, promo)
    }

    fun undoMove(): Boolean {
        if (moveHistory.isEmpty()) return false
        board.undoMove()
        moveHistory.removeLastOrNull()
        redoStack.clear()
        updateState()
        return true
    }

    fun getCurrentFen(): String = board.fen

    fun isWhiteTurn(): Boolean = board.sideToMove == Side.WHITE

    fun getPieceAt(squareName: String): Piece {
        return try {
            board.getPiece(Square.valueOf(squareName.uppercase()))
        } catch (e: Exception) {
            Piece.NONE
        }
    }

    fun getAllPieces(): Map<String, Piece> {
        val result = mutableMapOf<String, Piece>()
        for (sq in Square.values()) {
            if (sq == Square.NONE) continue
            val piece = board.getPiece(sq)
            if (piece != Piece.NONE) {
                result[sq.value().lowercase()] = piece
            }
        }
        return result
    }

    fun isInCheck(): Boolean = board.isKingAttacked

    fun getLegalMovesFrom(squareName: String): List<String> {
        val square = Square.valueOf(squareName.uppercase())
        return board.legalMoves()
            .filter { it.from == square }
            .map { it.to.value().lowercase() }
    }

    fun flipBoard() {
        _state.value = _state.value.copy(isFlipped = !_state.value.isFlipped)
    }

    fun setArrows(arrows: List<Arrow>) {
        _state.value = _state.value.copy(arrows = arrows)
    }

    fun addArrow(from: String, to: String, color: ArrowColor = ArrowColor.GREEN) {
        val current = _state.value.arrows.toMutableList()
        val existing = current.indexOfFirst { it.from == from && it.to == to }
        if (existing >= 0) current.removeAt(existing) else current.add(Arrow(from, to, color))
        _state.value = _state.value.copy(arrows = current)
    }

    fun clearArrows() {
        _state.value = _state.value.copy(arrows = emptyList())
    }

    // ── Persistence helpers ────────────────────────────────────────────────────

    /** Returns all moves as a compact UCI string: "e2e4,e7e5,g1f3,..." */
    fun getMovesAsUci(): String {
        return moveHistory.joinToString(",") { m ->
            val promo = m.promotion?.firstOrNull()?.lowercaseChar()
            if (promo != null) "${m.from}${m.to}$promo"
            else "${m.from}${m.to}"
        }
    }

    /** Replays a UCI move string to restore a saved game. */
    fun loadFromMoves(movesUci: String) {
        board.loadFromFen(START_FEN)
        moveHistory.clear()
        redoStack.clear()
        if (movesUci.isBlank()) {
            updateState()
            return
        }
        movesUci.split(",").forEach { uci ->
            if (uci.length >= 4) {
                val from = uci.substring(0, 2)
                val to   = uci.substring(2, 4)
                val promo = if (uci.length >= 5) uci[4] else 'q'
                tryMove(from, to, promo)
            }
        }
    }

    /** Generates a standard PGN string from the current game. */
    fun generatePgn(result: String = "*"): String {
        val date = SimpleDateFormat("yyyy.MM.dd").format(Date())
        val sb = StringBuilder()
        sb.appendLine("[Event \"A1Chess Game\"]")
        sb.appendLine("[Site \"A1Chess Android\"]")
        sb.appendLine("[Date \"$date\"]")
        sb.appendLine("[Round \"-\"]")
        sb.appendLine("[White \"Player\"]")
        sb.appendLine("[Black \"Player\"]")
        sb.appendLine("[Result \"$result\"]")
        sb.appendLine()

        val moves = moveHistory.toList()
        val moveSb = StringBuilder()
        moves.forEachIndexed { index, move ->
            if (index % 2 == 0) moveSb.append("${index / 2 + 1}. ")
            moveSb.append("${move.san} ")
        }
        moveSb.append(result)
        sb.append(moveSb.toString().trim())
        return sb.toString()
    }

    /**
     * Loads a game from a PGN string (SAN moves).
     * Returns true on success, false if parsing failed.
     */
    fun loadFromPgn(pgn: String): Boolean {
        return try {
            // Strip headers and comments
            val lines = pgn.lines().filter { !it.trimStart().startsWith("[") }
            val text = lines.joinToString(" ")
                .replace(Regex("\\{[^}]*\\}"), " ") // remove {comments}
                .replace(Regex(";[^\n]*"), " ")       // remove ;comments

            // Extract move tokens — skip move numbers and result strings
            val tokens = text.split(Regex("\\s+"))
                .map { it.trim() }
                .filter { tok ->
                    tok.isNotBlank() &&
                    !tok.matches(Regex("\\d+\\.+")) &&   // move numbers
                    tok !in setOf("1-0", "0-1", "1/2-1/2", "*")
                }

            if (tokens.isEmpty()) return false

            board.loadFromFen(START_FEN)
            moveHistory.clear()
            redoStack.clear()

            for (san in tokens) {
                val move = sanToMove(san) ?: return false
                val from = move.from.value().lowercase()
                val to   = move.to.value().lowercase()
                val promoChar = if (move.promotion != Piece.NONE) {
                    when(move.promotion.pieceType) {
                        PieceType.QUEEN  -> 'q'
                        PieceType.ROOK   -> 'r'
                        PieceType.BISHOP -> 'b'
                        PieceType.KNIGHT -> 'n'
                        else             -> 'q'
                    }
                } else 'q'
                if (!tryMove(from, to, promoChar)) return false
            }

            updateState()
            true
        } catch (e: Exception) {
            false
        }
    }

    // ── SAN generation ─────────────────────────────────────────────────────────

    private fun computeSan(board: Board, move: Move): String {
        val from = move.from
        val to   = move.to
        val piece = board.getPiece(from)

        // Castling — king moves 2 files
        if (piece.pieceType == PieceType.KING) {
            val fromFile = from.value()[0] - 'A'
            val toFile   = to.value()[0] - 'A'
            if (kotlin.math.abs(fromFile - toFile) >= 2) {
                return if (toFile > fromFile) "O-O" else "O-O-O"
            }
        }

        val toStr = to.value().lowercase()

        // En passant: pawn captures to empty square on different file
        val isCapture = board.getPiece(to) != Piece.NONE ||
            (piece.pieceType == PieceType.PAWN &&
             from.value()[0] != to.value()[0] &&
             board.getPiece(to) == Piece.NONE)

        if (piece.pieceType == PieceType.PAWN) {
            val promoSuffix = if (move.promotion != Piece.NONE) {
                "=" + when(move.promotion.pieceType) {
                    PieceType.QUEEN  -> "Q"; PieceType.ROOK   -> "R"
                    PieceType.BISHOP -> "B"; PieceType.KNIGHT -> "N"
                    else             -> "Q"
                }
            } else ""
            return if (isCapture) {
                "${from.value()[0].lowercaseChar()}x$toStr$promoSuffix"
            } else {
                "$toStr$promoSuffix"
            }
        }

        val pieceChar = when(piece.pieceType) {
            PieceType.KNIGHT -> "N"; PieceType.BISHOP -> "B"
            PieceType.ROOK   -> "R"; PieceType.QUEEN  -> "Q"
            PieceType.KING   -> "K"; else              -> ""
        }

        // Disambiguation: find other pieces of the same type/side that can reach 'to'
        val ambiguous = board.legalMoves().filter { m ->
            m != move && m.to == to &&
            board.getPiece(m.from).pieceType == piece.pieceType &&
            board.getPiece(m.from).pieceSide == piece.pieceSide
        }

        val disambig = when {
            ambiguous.isEmpty() -> ""
            ambiguous.all { it.from.value()[0] != from.value()[0] } ->
                from.value()[0].lowercaseChar().toString()
            ambiguous.all { it.from.value()[1] != from.value()[1] } ->
                from.value()[1].toString()
            else -> from.value().lowercase()
        }

        val captureX = if (isCapture) "x" else ""
        return "$pieceChar$disambig$captureX$toStr"
    }

    // ── SAN → Move (for PGN import) ────────────────────────────────────────────

    private fun sanToMove(san: String): Move? {
        val lm = board.legalMoves()
        val s = san.trimEnd('+', '#', '!', '?').trim()
        if (s.isBlank()) return null

        // Castling
        if (s == "O-O" || s == "0-0") {
            return lm.firstOrNull { m ->
                board.getPiece(m.from).pieceType == PieceType.KING &&
                (m.to.value() == "G1" || m.to.value() == "G8")
            }
        }
        if (s == "O-O-O" || s == "0-0-0") {
            return lm.firstOrNull { m ->
                board.getPiece(m.from).pieceType == PieceType.KING &&
                (m.to.value() == "C1" || m.to.value() == "C8")
            }
        }

        // Promotion: e8=Q, exd8=Q, e8Q
        val promoRegex = Regex("([a-hA-H])([x×]?)([a-hA-H][18])=?([QRBNqrbn])")
        val promoMatch = promoRegex.find(s)
        if (promoMatch != null) {
            val toStr = promoMatch.groupValues[3].uppercase()
            val promoChar = promoMatch.groupValues[4][0].uppercaseChar()
            val toSq = try { Square.valueOf(toStr) } catch (e: Exception) { return null }
            val side = board.sideToMove
            val promoPiece = when(promoChar) {
                'Q' -> if (side == Side.WHITE) Piece.WHITE_QUEEN  else Piece.BLACK_QUEEN
                'R' -> if (side == Side.WHITE) Piece.WHITE_ROOK   else Piece.BLACK_ROOK
                'B' -> if (side == Side.WHITE) Piece.WHITE_BISHOP else Piece.BLACK_BISHOP
                'N' -> if (side == Side.WHITE) Piece.WHITE_KNIGHT else Piece.BLACK_KNIGHT
                else -> return null
            }
            return lm.firstOrNull { it.to == toSq && it.promotion == promoPiece }
        }

        val isPieceMov = s[0].isUpperCase() && s[0] != 'O'
        val pieceType = if (isPieceMov) when(s[0]) {
            'N' -> PieceType.KNIGHT; 'B' -> PieceType.BISHOP; 'R' -> PieceType.ROOK
            'Q' -> PieceType.QUEEN;  'K' -> PieceType.KING;   else -> PieceType.PAWN
        } else PieceType.PAWN

        val stripped = (if (isPieceMov) s.substring(1) else s)
            .replace("x", "").replace("×", "")
        if (stripped.length < 2) return null

        val toStr = stripped.takeLast(2).uppercase()
        val disambig = stripped.dropLast(2).lowercase()

        val toSq = try { Square.valueOf(toStr) } catch (e: Exception) { return null }

        return lm.firstOrNull { m ->
            m.to == toSq &&
            board.getPiece(m.from).pieceType == pieceType &&
            (disambig.isEmpty() || m.from.value().lowercase().contains(disambig))
        }
    }

    private fun updateState() {
        val isOver = board.isMated || board.isDraw || board.isStaleMate
        val result = when {
            board.isMated     -> if (board.sideToMove == Side.WHITE) "0-1" else "1-0"
            board.isDraw      -> "1/2-1/2"
            board.isStaleMate -> "1/2-1/2"
            else              -> null
        }
        val lastMove = moveHistory.lastOrNull()?.let { it.from to it.to }

        val checkedKingSquare = if (board.isKingAttacked) {
            findKingSquare(board.sideToMove)
        } else null

        _state.value = _state.value.copy(
            fen = board.fen,
            moves = moveHistory.toList(),
            selectedSquare = null,
            legalMoves = emptyList(),
            lastMove = lastMove,
            isWhiteTurn = board.sideToMove == Side.WHITE,
            isGameOver = isOver,
            gameResult = result,
            currentMoveIndex = moveHistory.size - 1,
            canGoBack = moveHistory.isNotEmpty(),
            canGoForward = redoStack.isNotEmpty(),
            checkedKingSquare = checkedKingSquare,
        )
    }

    private fun findKingSquare(side: Side): String? {
        val kingPiece = if (side == Side.WHITE) Piece.WHITE_KING else Piece.BLACK_KING
        for (sq in Square.values()) {
            if (sq == Square.NONE) continue
            if (board.getPiece(sq) == kingPiece) return sq.value().lowercase()
        }
        return null
    }
}
