package com.koborsoft.chessanalyser.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koborsoft.chessanalyser.EditState
import com.koborsoft.chessanalyser.R
import com.koborsoft.chessanalyser.core.Piece
import com.koborsoft.chessanalyser.core.Square

private val ED_LIGHT = Color(0xFFD9E2EC)
private val ED_DARK = Color(0xFF7C96B0)
private val ED_SELECTED = Color(0xFF44607C)
private val ED_UNCERTAIN = Color(0xFFE8A317)  // bizonytalan mező jelzése (borostyán)

/**
 * Fotóból felismert állás szerkesztése: ecset (bábu/üres) választása, majd
 * mezőkre koppintás; „ki lép" és tábla-forgatás; végül indítás.
 */
@Composable
fun EditPositionDialog(
    edit: EditState,
    onSquareTap: (Int) -> Unit,
    onBrush: (Int) -> Unit,
    onSide: (Int) -> Unit,
    onFlip: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.edit_position)) },
        text = {
            var showImage by remember { mutableStateOf(false) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.edit_hint), style = MaterialTheme.typography.bodySmall)

                // Tab: szerkesztett állás vagy a forráskép megtekintése.
                if (edit.sourceImage != null) {
                    SegChoice(
                        options = listOf(
                            false to stringResource(R.string.tab_edit),
                            true to stringResource(R.string.tab_photo),
                        ),
                        selected = showImage,
                        onSelect = { showImage = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (showImage && edit.sourceImage != null) {
                    Image(
                        bitmap = edit.sourceImage,
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    EditBoard(edit = edit, onSquareTap = onSquareTap)
                }

                // Ecsetpaletta: törlő + világos + sötét bábuk.
                PaletteRow(
                    label = stringResource(R.string.white),
                    pieces = listOf(
                        Piece.NONE,
                        Piece.of(Piece.WHITE, Piece.PAWN),
                        Piece.of(Piece.WHITE, Piece.KNIGHT),
                        Piece.of(Piece.WHITE, Piece.BISHOP),
                        Piece.of(Piece.WHITE, Piece.ROOK),
                        Piece.of(Piece.WHITE, Piece.QUEEN),
                        Piece.of(Piece.WHITE, Piece.KING),
                    ),
                    brush = edit.brush,
                    onBrush = onBrush,
                )
                PaletteRow(
                    label = stringResource(R.string.black),
                    pieces = listOf(
                        Piece.of(Piece.BLACK, Piece.PAWN),
                        Piece.of(Piece.BLACK, Piece.KNIGHT),
                        Piece.of(Piece.BLACK, Piece.BISHOP),
                        Piece.of(Piece.BLACK, Piece.ROOK),
                        Piece.of(Piece.BLACK, Piece.QUEEN),
                        Piece.of(Piece.BLACK, Piece.KING),
                    ),
                    brush = edit.brush,
                    onBrush = onBrush,
                )

                Text(stringResource(R.string.side_to_move), fontWeight = FontWeight.Bold)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SegChoice(
                        options = listOf(
                            Piece.WHITE to stringResource(R.string.white),
                            Piece.BLACK to stringResource(R.string.black),
                        ),
                        selected = edit.sideToMove,
                        onSelect = onSide,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onFlip) { Text(stringResource(R.string.flip_board)) }
                }

                val warnRes = when (edit.error) {
                    "one_king_each" -> R.string.edit_need_kings
                    "recog_suspect_kings" -> R.string.recog_suspect_kings
                    "recog_suspect_pawn" -> R.string.recog_suspect_pawn
                    else -> null
                }
                if (warnRes != null) {
                    Text(
                        stringResource(warnRes),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.start)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun EditBoard(edit: EditState, onSquareTap: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
        val ranks = if (edit.flipped) 0..7 else 7 downTo 0
        for (r in ranks) {
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val files = if (edit.flipped) 7 downTo 0 else 0..7
                for (f in files) {
                    val sq = Square.of(f, r)
                    val light = (r + f) % 2 == 1
                    val uncertain = sq in edit.uncertain
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .background(if (light) ED_LIGHT else ED_DARK)
                            .then(
                                if (uncertain) Modifier.border(2.dp, ED_UNCERTAIN)
                                else Modifier
                            )
                            .clickable { onSquareTap(sq) },
                        contentAlignment = Alignment.Center,
                    ) {
                        val piece = edit.board[sq]
                        if (piece != Piece.NONE) {
                            Text(pieceChar(piece), fontSize = 26.sp, color = Color.Black)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaletteRow(
    label: String,
    pieces: List<Int>,
    brush: Int,
    onBrush: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (piece in pieces) {
            val selected = piece == brush
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(if (selected) ED_SELECTED else Color(0x22000000))
                    .border(
                        width = if (selected) 2.dp else 0.dp,
                        color = if (selected) ED_SELECTED else Color.Transparent,
                    )
                    .clickable { onBrush(piece) },
                contentAlignment = Alignment.Center,
            ) {
                if (piece == Piece.NONE) {
                    Text("⌫", fontSize = 18.sp, color = if (selected) Color.White else Color.Black)
                } else {
                    Text(pieceChar(piece), fontSize = 24.sp, color = Color.Black)
                }
            }
        }
    }
}
