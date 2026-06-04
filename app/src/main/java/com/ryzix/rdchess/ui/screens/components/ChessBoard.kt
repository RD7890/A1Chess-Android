package com.ryzix.rdchess.ui.screens.components

import android.graphics.Paint
import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import com.caverock.androidsvg.SVG
import com.github.bhlangonijr.chesslib.Piece
import com.ryzix.rdchess.chess.Arrow
import com.ryzix.rdchess.chess.ArrowColor
import com.ryzix.rdchess.chess.GameState
import com.ryzix.rdchess.ui.theme.*

private fun fileIndex(file: Char) = file - 'a'
private fun rankIndex(rank: Char) = rank - '1'

private fun squareToColRow(sq: String, flipped: Boolean): Pair<Int, Int> {
    val file = sq[0]
    val rank = sq[1]
    val col = if (flipped) 7 - fileIndex(file) else fileIndex(file)
    val row = if (flipped) rankIndex(rank) else 7 - rankIndex(rank)
    return col to row
}

private fun offsetToSquare(x: Float, y: Float, squareSize: Float, flipped: Boolean): String {
    val col = (x / squareSize).toInt().coerceIn(0, 7)
    val row = (y / squareSize).toInt().coerceIn(0, 7)
    val file = if (flipped) ('h' - col) else ('a' + col)
    val rank = if (flipped) ('1' + row) else ('8' - row)
    return "$file$rank"
}

@Composable
fun ChessBoard(
    modifier: Modifier = Modifier,
    state: GameState,
    onSquareTap: (String) -> Unit,
    showCoordinates: Boolean = true,
) {
    val context = LocalContext.current
    val svgCache = remember { mutableMapOf<String, SVG?>() }
    val bitmapCache = remember { mutableMapOf<String, ImageBitmap?>() }

    fun getSvg(assetName: String): SVG? {
        return svgCache.getOrPut(assetName) {
            try {
                context.assets.open("pieces/staunty/$assetName.svg").use { SVG.getFromInputStream(it) }
            } catch (e: Exception) { null }
        }
    }

    fun getBitmap(piece: Piece, size: Int): ImageBitmap? {
        val key = "${piece.name}_$size"
        return bitmapCache.getOrPut(key) {
            val assetName = pieceAssetName(piece) ?: return@getOrPut null
            val svg = getSvg(assetName) ?: return@getOrPut null
            svg.documentWidth = size.toFloat()
            svg.documentHeight = size.toFloat()
            val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            svg.renderToCanvas(canvas)
            bmp.asImageBitmap()
        }
    }

    Canvas(
        modifier = modifier.pointerInput(state.isFlipped) {
            detectTapGestures { offset ->
                val squareSize = size.width / 8f
                val sq = offsetToSquare(offset.x, offset.y, squareSize, state.isFlipped)
                onSquareTap(sq)
            }
        }
    ) {
        val squareSize = size.width / 8f
        val sqInt = squareSize.toInt()

        for (row in 0..7) {
            for (col in 0..7) {
                val isLight = (row + col) % 2 == 0
                val x = col * squareSize
                val y = row * squareSize

                val file = if (state.isFlipped) ('h' - col) else ('a' + col)
                val rank = if (state.isFlipped) ('1' + row) else ('8' - row)
                val sq = "$file$rank"

                val isLastMoveFrom = state.lastMove?.first == sq
                val isLastMoveTo = state.lastMove?.second == sq
                val isSelected = state.selectedSquare == sq
                val isLegal = state.legalMoves.contains(sq)

                val squareColor = when {
                    isSelected -> if (isLight) Color(0xFFf6f669) else Color(0xFFbaca2b)
                    isLastMoveFrom || isLastMoveTo -> if (isLight) Color(0xFFf6f669).copy(alpha = 0.8f) else Color(0xFFbaca2b).copy(alpha = 0.8f)
                    isLight -> BoardLightGreen
                    else -> BoardDarkGreen
                }

                drawRect(color = squareColor, topLeft = Offset(x, y), size = Size(squareSize, squareSize))

                if (isLegal) {
                    val piece = state.fen.let { getPieceAtSquare(it, sq) }
                    if (piece != null) {
                        drawCircle(
                            color = BoardMoveDotCapture.copy(alpha = 0.35f),
                            radius = squareSize / 2f - 2f,
                            center = Offset(x + squareSize / 2, y + squareSize / 2),
                            style = Stroke(width = squareSize * 0.08f),
                        )
                    } else {
                        drawCircle(
                            color = BoardMoveDot,
                            radius = squareSize * 0.16f,
                            center = Offset(x + squareSize / 2, y + squareSize / 2),
                        )
                    }
                }

                if (showCoordinates) {
                    drawIntoCanvas { canvas ->
                        val paint = android.graphics.Paint().apply {
                            color = if (isLight) android.graphics.Color.argb(180, 90, 130, 50)
                            else android.graphics.Color.argb(180, 190, 210, 150)
                            textSize = squareSize * 0.18f
                            isAntiAlias = true
                        }
                        if (col == 0) {
                            canvas.nativeCanvas.drawText(
                                "$rank", x + 3f, y + squareSize * 0.22f, paint
                            )
                        }
                        if (row == 7) {
                            val fm = paint.fontMetrics
                            canvas.nativeCanvas.drawText(
                                "$file",
                                x + squareSize - paint.measureText("$file") - 3f,
                                y + squareSize - 3f,
                                paint,
                            )
                        }
                    }
                }
            }
        }

        // Draw pieces
        state.let {
            val piecesOnBoard = parseFenPieces(it.fen)
            piecesOnBoard.forEach { (sq, piece) ->
                val (col, row) = squareToColRow(sq, it.isFlipped)
                val bmp = getBitmap(piece, sqInt)
                if (bmp != null) {
                    drawImage(
                        image = bmp,
                        topLeft = Offset(col * squareSize, row * squareSize),
                    )
                }
            }
        }

        // Draw arrows
        state.arrows.forEach { arrow ->
            drawArrow(arrow, squareSize, state.isFlipped)
        }
    }
}

private fun DrawScope.drawArrow(arrow: Arrow, squareSize: Float, flipped: Boolean) {
    val (fromCol, fromRow) = squareToColRow(arrow.from, flipped)
    val (toCol, toRow) = squareToColRow(arrow.to, flipped)

    val fromCenter = Offset(
        fromCol * squareSize + squareSize / 2,
        fromRow * squareSize + squareSize / 2,
    )
    val toCenter = Offset(
        toCol * squareSize + squareSize / 2,
        toRow * squareSize + squareSize / 2,
    )

    val color = when (arrow.color) {
        ArrowColor.GREEN -> BoardArrowGreen
        ArrowColor.BLUE -> Color(0xCC0066CC)
        ArrowColor.RED -> Color(0xCCCC0000)
        ArrowColor.YELLOW -> Color(0xCCCCA000)
    }

    val dx = toCenter.x - fromCenter.x
    val dy = toCenter.y - fromCenter.y
    val len = kotlin.math.sqrt(dx * dx + dy * dy)
    val ux = dx / len
    val uy = dy / len

    val arrowHeadLen = squareSize * 0.35f
    val shaftEnd = Offset(toCenter.x - ux * arrowHeadLen * 0.6f, toCenter.y - uy * arrowHeadLen * 0.6f)

    drawLine(
        color = color,
        start = Offset(fromCenter.x + ux * squareSize * 0.25f, fromCenter.y + uy * squareSize * 0.25f),
        end = shaftEnd,
        strokeWidth = squareSize * 0.13f,
        cap = StrokeCap.Round,
    )

    val perpX = -uy
    val perpY = ux
    val tip = toCenter
    val base1 = Offset(shaftEnd.x + perpX * arrowHeadLen * 0.45f, shaftEnd.y + perpY * arrowHeadLen * 0.45f)
    val base2 = Offset(shaftEnd.x - perpX * arrowHeadLen * 0.45f, shaftEnd.y - perpY * arrowHeadLen * 0.45f)

    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(base1.x, base1.y)
        lineTo(base2.x, base2.y)
        close()
    }
    drawPath(path, color = color)
}

private fun pieceAssetName(piece: Piece): String? = when (piece) {
    Piece.WHITE_KING   -> "wK"
    Piece.WHITE_QUEEN  -> "wQ"
    Piece.WHITE_ROOK   -> "wR"
    Piece.WHITE_BISHOP -> "wB"
    Piece.WHITE_KNIGHT -> "wN"
    Piece.WHITE_PAWN   -> "wP"
    Piece.BLACK_KING   -> "bK"
    Piece.BLACK_QUEEN  -> "bQ"
    Piece.BLACK_ROOK   -> "bR"
    Piece.BLACK_BISHOP -> "bB"
    Piece.BLACK_KNIGHT -> "bN"
    Piece.BLACK_PAWN   -> "bP"
    else -> null
}

private fun parseFenPieces(fen: String): Map<String, Piece> {
    val result = mutableMapOf<String, Piece>()
    val fenBoard = fen.split(" ").firstOrNull() ?: return result
    val rows = fenBoard.split("/")
    rows.forEachIndexed { rowIdx, row ->
        val rank = 8 - rowIdx
        var fileIdx = 0
        for (ch in row) {
            if (ch.isDigit()) {
                fileIdx += ch.digitToInt()
            } else {
                val file = 'a' + fileIdx
                val sq = "$file$rank"
                val piece = charToPiece(ch)
                if (piece != Piece.NONE) result[sq] = piece
                fileIdx++
            }
        }
    }
    return result
}

private fun getPieceAtSquare(fen: String, square: String): Piece? {
    val pieces = parseFenPieces(fen)
    return pieces[square]
}

private fun charToPiece(c: Char): Piece = when (c) {
    'K' -> Piece.WHITE_KING
    'Q' -> Piece.WHITE_QUEEN
    'R' -> Piece.WHITE_ROOK
    'B' -> Piece.WHITE_BISHOP
    'N' -> Piece.WHITE_KNIGHT
    'P' -> Piece.WHITE_PAWN
    'k' -> Piece.BLACK_KING
    'q' -> Piece.BLACK_QUEEN
    'r' -> Piece.BLACK_ROOK
    'b' -> Piece.BLACK_BISHOP
    'n' -> Piece.BLACK_KNIGHT
    'p' -> Piece.BLACK_PAWN
    else -> Piece.NONE
}
