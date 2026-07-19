package com.koborsoft.chessanalyser.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koborsoft.chessanalyser.ArrowUi
import com.koborsoft.chessanalyser.core.Move
import com.koborsoft.chessanalyser.core.Piece
import com.koborsoft.chessanalyser.core.Position
import com.koborsoft.chessanalyser.core.Square

/** Matt-kék táblaszínek — az app egységes színvilágának alapja. */
private val LIGHT_SQUARE = Color(0xFFD9E2EC)
private val DARK_SQUARE = Color(0xFF7C96B0)
private val SELECTED = Color(0x8020A020)
private val CHECK = Color(0x80E03030)

/**
 * Az utolsó lépés kiemelése: a lépő fél (borostyán = világos, lila = sötét)
 * ÉS a mező alapszíne szerint külön hangolt árnyalat, hogy a kék táblán
 * mindkét mezőszínen azonos erősségűnek hasson.
 */
private val WHITE_MOVE_ON_LIGHT = Color(0xFFEBD289)
private val WHITE_MOVE_ON_DARK = Color(0xFFC8A954)
private val BLACK_MOVE_ON_LIGHT = Color(0xFFC7B0DC)
private val BLACK_MOVE_ON_DARK = Color(0xFF9880BA)

/**
 * A nyilak színmélysége ÉS vastagsága a lépés erősségével arányos (0..1),
 * a pontos értéket a nyílra írt szám adja meg. A szélsőségek tompítva: a
 * legerősebb sem agresszív, a leggyengébb is jól látható (a skálát a szám adja).
 */
private val HINT_STRONG = Color(0xDA2E7D32)
private val HINT_WEAK = Color(0xB866BB6A)
private val THREAT_STRONG = Color(0xDAC62828)
private val THREAT_WEAK = Color(0xB8EF5350)

private const val ANIM_MS = 180

private val PIECE_CHARS = mapOf(
    Piece.of(Piece.WHITE, Piece.KING) to "♔",
    Piece.of(Piece.WHITE, Piece.QUEEN) to "♕",
    Piece.of(Piece.WHITE, Piece.ROOK) to "♖",
    Piece.of(Piece.WHITE, Piece.BISHOP) to "♗",
    Piece.of(Piece.WHITE, Piece.KNIGHT) to "♘",
    Piece.of(Piece.WHITE, Piece.PAWN) to "♙",
    Piece.of(Piece.BLACK, Piece.KING) to "♚",
    Piece.of(Piece.BLACK, Piece.QUEEN) to "♛",
    Piece.of(Piece.BLACK, Piece.ROOK) to "♜",
    Piece.of(Piece.BLACK, Piece.BISHOP) to "♝",
    Piece.of(Piece.BLACK, Piece.KNIGHT) to "♞",
    Piece.of(Piece.BLACK, Piece.PAWN) to "♟",
)

fun pieceChar(piece: Int): String = PIECE_CHARS[piece] ?: ""

/** A mező bal felső sarka cellaegységben (oszlop, sor a képernyő tetejétől). */
private fun cellOf(sq: Int, flipped: Boolean): Offset {
    val col = if (flipped) 7 - Square.file(sq) else Square.file(sq)
    val row = if (flipped) Square.rank(sq) else 7 - Square.rank(sq)
    return Offset(col.toFloat(), row.toFloat())
}

@Composable
fun BoardView(
    position: Position,
    selectedSquare: Int?,
    legalTargets: Set<Int>,
    lastMove: Move?,
    undoneMove: Move?,
    hintArrows: List<ArrowUi>,
    threatArrows: List<ArrowUi>,
    flipped: Boolean,
    onSquareTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val checkedKing = if (position.inCheck()) position.kingSquare(position.sideToMove) else -1
    val lastMoveByWhite = lastMove?.let {
        Piece.colorOf(position.pieceAt(it.to)) == Piece.WHITE
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth().aspectRatio(1f)) {
        val cell = maxWidth / 8

        // Alsó réteg: mezők, kiemelések, koordináták, koppintás
        Column(modifier = Modifier.fillMaxSize()) {
            val ranks = if (flipped) 0..7 else 7 downTo 0
            for (r in ranks) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val files = if (flipped) 7 downTo 0 else 0..7
                    for (f in files) {
                        val sq = Square.of(f, r)
                        SquareCell(
                            isLight = (r + f) % 2 == 1,
                            isSelected = sq == selectedSquare,
                            isTarget = sq in legalTargets,
                            isOccupied = position.pieceAt(sq) != Piece.NONE,
                            lastMoveByWhite = if (lastMove != null &&
                                (sq == lastMove.from || sq == lastMove.to)
                            ) lastMoveByWhite else null,
                            isCheckedKing = sq == checkedKing,
                            fileLabel = if (r == if (flipped) 7 else 0) ('a' + f).toString() else null,
                            rankLabel = if (f == if (flipped) 7 else 0) ('1' + r).toString() else null,
                            onTap = { onSquareTap(sq) },
                            modifier = Modifier.weight(1f).fillMaxSize(),
                        )
                    }
                }
            }
        }

        // Felső réteg: bábuk; az utoljára lépett bábu (sáncnál a bástya is)
        // animálva — visszavonásnál visszafelé játszódik a lépés.
        val pieceSize = with(androidx.compose.ui.platform.LocalDensity.current) {
            (cell.toPx() * 0.62f).toSp()
        }
        val rookFromTo = lastMove?.takeIf { it.isCastling }?.let {
            if (it.to > it.from) (it.from + 3) to (it.from + 1)
            else (it.from - 4) to (it.from - 1)
        }
        // Visszavont sáncnál a bástya a sarokmezőre csúszik vissza.
        val undoneRook = undoneMove?.takeIf { it.isCastling }?.let {
            if (it.to > it.from) (it.from + 1) to (it.from + 3)
            else (it.from - 1) to (it.from - 4)
        }
        for (sq in 0..63) {
            val piece = position.pieceAt(sq)
            if (piece == Piece.NONE) continue
            val animateFrom = when {
                undoneMove != null -> when (sq) {
                    undoneMove.from -> undoneMove.to
                    undoneRook?.second -> undoneRook.first
                    else -> null
                }
                lastMove != null && sq == lastMove.to -> lastMove.from
                rookFromTo != null && sq == rookFromTo.second -> rookFromTo.first
                else -> null
            }
            key(sq, piece) {
                val target = cellOf(sq, flipped)
                val anim = remember(lastMove, undoneMove, flipped) {
                    Animatable(
                        if (animateFrom != null) cellOf(animateFrom, flipped) else target,
                        Offset.VectorConverter,
                    )
                }
                LaunchedEffect(lastMove, undoneMove, flipped) {
                    if (anim.value != target) {
                        anim.animateTo(target, tween(ANIM_MS, easing = FastOutSlowInEasing))
                    }
                }
                Box(
                    modifier = Modifier
                        .offset(x = cell * anim.value.x, y = cell * anim.value.y)
                        .size(cell),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = pieceChar(piece), fontSize = pieceSize, color = Color.Black)
                }
            }
        }

        // Legfelső réteg: javaslat- (zöld) és veszély- (piros) nyilak számokkal.
        if (hintArrows.isNotEmpty() || threatArrows.isNotEmpty()) {
            val labelStyle = TextStyle(
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                shadow = Shadow(Color.Black, blurRadius = 6f),
            )
            val textMeasurer = rememberTextMeasurer()
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cellPx = size.width / 8f
                fun center(sq: Int): Offset {
                    val c = cellOf(sq, flipped)
                    return Offset((c.x + 0.5f) * cellPx, (c.y + 0.5f) * cellPx)
                }
                fun drawAll(arrows: List<ArrowUi>, strong: Color, weak: Color) {
                    for (arrow in arrows.asReversed()) {
                        val weakness = 1f - arrow.strength
                        drawArrow(
                            center(arrow.move.from), center(arrow.move.to),
                            lerp(strong, weak, weakness), cellPx, weakness,
                        )
                    }
                    for (arrow in arrows) {
                        val from = center(arrow.move.from)
                        val to = center(arrow.move.to)
                        val delta = to - from
                        val len = delta.getDistance().coerceAtLeast(1f)
                        val perp = Offset(-delta.y / len, delta.x / len)
                        val mid = Offset(
                            (from.x + to.x) / 2f + perp.x * cellPx * 0.22f,
                            (from.y + to.y) / 2f + perp.y * cellPx * 0.22f,
                        )
                        val layout = textMeasurer.measure(AnnotatedString(arrow.label), labelStyle)
                        drawText(
                            layout,
                            topLeft = Offset(
                                mid.x - layout.size.width / 2f,
                                mid.y - layout.size.height / 2f,
                            ),
                        )
                    }
                }
                drawAll(threatArrows, THREAT_STRONG, THREAT_WEAK)
                drawAll(hintArrows, HINT_STRONG, HINT_WEAK)
            }
        }
    }
}

/**
 * Nyíl rajzolása két mezőközéppont közé. A [weakness] (0 = a legerősebb
 * lépés, 1 = a leggyengébb) a vastagságot szabályozza.
 */
private fun DrawScope.drawArrow(
    from: Offset,
    to: Offset,
    color: Color,
    cellPx: Float,
    weakness: Float,
) {
    val delta = to - from
    val length = delta.getDistance()
    if (length < 1f) return
    val unit = Offset(delta.x / length, delta.y / length)
    // Tompított tartomány: a legerősebb sem túl vastag, a leggyengébb is látható.
    val width = cellPx * (0.15f - 0.05f * weakness)
    val headLength = width * 2.4f
    val lineEnd = Offset(to.x - unit.x * headLength, to.y - unit.y * headLength)
    val perp = Offset(-unit.y, unit.x)

    drawLine(color, from, lineEnd, strokeWidth = width, cap = StrokeCap.Round)
    val head = Path().apply {
        moveTo(to.x, to.y)
        lineTo(lineEnd.x + perp.x * width * 1.6f, lineEnd.y + perp.y * width * 1.6f)
        lineTo(lineEnd.x - perp.x * width * 1.6f, lineEnd.y - perp.y * width * 1.6f)
        close()
    }
    drawPath(head, color)
}

@Composable
private fun SquareCell(
    isLight: Boolean,
    isSelected: Boolean,
    isTarget: Boolean,
    isOccupied: Boolean,
    lastMoveByWhite: Boolean?,
    isCheckedKing: Boolean,
    fileLabel: String?,
    rankLabel: String?,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val base = when {
        lastMoveByWhite == true -> if (isLight) WHITE_MOVE_ON_LIGHT else WHITE_MOVE_ON_DARK
        lastMoveByWhite == false -> if (isLight) BLACK_MOVE_ON_LIGHT else BLACK_MOVE_ON_DARK
        else -> if (isLight) LIGHT_SQUARE else DARK_SQUARE
    }
    val labelColor = if (isLight) DARK_SQUARE else LIGHT_SQUARE

    Box(
        modifier = modifier
            .background(base)
            .let { m ->
                when {
                    isSelected -> m.background(SELECTED)
                    isCheckedKing -> m.background(CHECK)
                    else -> m
                }
            }
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        rankLabel?.let {
            Text(
                it, fontSize = 10.sp, color = labelColor, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopStart).padding(2.dp),
            )
        }
        fileLabel?.let {
            Text(
                it, fontSize = 10.sp, color = labelColor, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp),
            )
        }
        if (isTarget) {
            Box(
                modifier = Modifier
                    .size(if (isOccupied) 20.dp else 12.dp)
                    .background(SELECTED, CircleShape),
            )
        }
    }
}
