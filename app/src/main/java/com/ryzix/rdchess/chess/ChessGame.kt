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
)

data class Arrow(
    val from: String,
    val to: String,
    val color: ArrowColor = ArrowColor.GREEN,
)

enum class ArrowColor { GREEN, BLUE, RED, YELLOW }

data class GameState(
    val fen: String = Board.DEFAULT_POSITION,
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
)

class ChessGame {
    private val board = Board()
    private val moveHistory = mutableListOf<ChessMove>()

    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state

    init {
        board.loadFromFen(Board.DEFAULT_POSITION)
        updateState()
    }

    fun reset(fen: String = Board.DEFAULT_POSITION) {
        board.loadFromFen(fen)
        moveHistory.clear()
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

        val isCurrentTurnPiece = (piece.pieceSide == Side.WHITE) == board.sideToMove == Side.WHITE
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

    fun tryMove(fromSq: String, toSq: String): Boolean {
        val from = Square.valueOf(fromSq.uppercase())
        val to = Square.valueOf(toSq.uppercase())

        val legalMoves = board.legalMoves().filter { it.from == from && it.to == to }
        if (legalMoves.isEmpty()) return false

        val move = if (legalMoves.size > 1) {
            legalMoves.firstOrNull { it.promotion == Piece.WHITE_QUEEN || it.promotion == Piece.BLACK_QUEEN }
                ?: legalMoves.first()
        } else legalMoves.first()

        val isCapture = board.getPiece(to) != Piece.NONE || move.toString().contains("x")
        val san = getSan(move)

        board.doMove(move)

        val chessMove = ChessMove(
            from = fromSq,
            to = toSq,
            san = san,
            fen = board.fen,
            isCapture = isCapture,
        )
        moveHistory.add(chessMove)
        updateState()
        return true
    }

    fun undoMove(): Boolean {
        if (moveHistory.isEmpty()) return false
        board.undoMove()
        moveHistory.removeLastOrNull()
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
            board.isMated -> if (board.sideToMove == Side.WHITE) "0-1" else "1-0"
            board.isDraw -> "1/2-1/2"
            board.isStaleMate -> "1/2-1/2"
            else -> null
        }

        val lastMove = moveHistory.lastOrNull()?.let { it.from to it.to }

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
        )
    }

    private fun getSan(move: Move): String {
        return try {
            move.toString()
        } catch (e: Exception) {
            move.toString()
        }
    }
}
