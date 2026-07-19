package com.koborsoft.chessanalyser.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
 * Teljes képernyős állásszerkesztő. Alapból az aktuális állást szerkeszted;
 * feltöltheted egy képről felismert állással (akár többször, más képpel vagy
 * újra ugyanazzal), vagy nulláról („Üres tábla"). Bábu-paletta + koppintás,
 * „Ki lép" (egyben a felismerés tájolási tippje), nézet-forgatás, végül Betöltés.
 */
@Composable
fun PositionEditor(
    edit: EditState,
    recognizing: Boolean,
    recognizeError: String?,
    onSquareTap: (Int) -> Unit,
    onBrush: (Int) -> Unit,
    onSide: (Int) -> Unit,
    onFlip: () -> Unit,
    onClear: () -> Unit,
    onRecognize: (Uri) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri -> uri?.let(onRecognize) }
    var showImage by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            // Fejléc
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.editor_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onCancel) {
                    Icon(Icons.Filled.Close, stringResource(R.string.cancel))
                }
            }

            // Görgethető szerkesztő-terület
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.editor_hint), style = MaterialTheme.typography.bodySmall)

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

                // Ki lép + nézet-forgatás
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

                // Feltöltés: felismerés képről / üres tábla
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { galleryLauncher.launch("image/*") },
                        enabled = !recognizing,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.PhotoCamera, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.editor_recognize))
                    }
                    OutlinedButton(onClick = onClear, enabled = !recognizing) {
                        Text(stringResource(R.string.editor_clear))
                    }
                }

                // Állapot / hibák
                if (recognizing) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.recognizing))
                    }
                }
                recognizeError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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

            // Fix alsó gombsor
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
                Button(onClick = onConfirm) { Text(stringResource(R.string.editor_load)) }
            }
        }
    }
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
