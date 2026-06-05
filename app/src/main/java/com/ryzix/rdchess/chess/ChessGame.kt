package com.ryzix.rdchess.chess

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.PieceType
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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
    /** Square of the king currently in check (null if not in check). */
    val checkedKingSquare: String? = null,
)

class ChessGame {
    private val board = Board()
    private val moveHistory = mutableListOf<ChessMove>()
    private val redoStack = mutableListOf<Triple<String, String, Char>>() // from, to, promoChar

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

    /**
     * Returns true if moving [fromSq] → [toSq] is a pawn promotion
     * (multiple legal moves exist because of piece choices).
     */
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

    /**
     * Execute a move. [promoChar] selects the promotion piece when applicable:
     *   'q'=Queen, 'r'=Rook, 'b'=Bishop, 'n'=Knight.
     */
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

        val isCapture = board.getPiece(to) != Piece.NONE
        val san = getSan(move)

        board.doMove(move)

        val chessMove = ChessMove(
            from = fromSq,
            to = toSq,
            san = san,
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

    private fun updateState() {
        val isOver = board.isMated || board.isDraw || board.isStaleMate
        val result = when {
            board.isMated    -> if (board.sideToMove == Side.WHITE) "0-1" else "1-0"
            board.isDraw     -> "1/2-1/2"
            board.isStaleMate -> "1/2-1/2"
            else             -> null
        }
        val lastMove = moveHistory.lastOrNull()?.let { it.from to it.to }

        // Find the king square that's in check (includes checkmate)
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

    private fun getSan(move: Move): String {
        return try { move.toString() } catch (e: Exception) { move.toString() }
    }
}
