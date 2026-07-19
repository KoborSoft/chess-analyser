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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
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
    onLoadFile: (Uri) -> String?,
    onImportText: (String) -> String?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri -> uri?.let(onRecognize) }
    var showImage by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf(false) }
    var showPaste by remember { mutableStateOf(false) }
    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) importError = (onLoadFile(uri) == null) }

    if (showPaste) {
        PasteDialog(onImportText = onImportText, onDismiss = { showPaste = false })
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        // safeDrawingPadding: a tartalom ne kerüljön a státusz-/navigációs sáv
        // vagy a kamerakivágás alá (a Surface háttere kitölti az egész képernyőt).
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Fejléc
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.editor_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onCancel) {
                    Icon(Icons.Filled.Close, stringResource(R.string.cancel))
                }
            }

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

            // Tábla / forráskép — a maradék helyet tölti ki, így a paletta és a
            // gombok görgetés nélkül is láthatók maradnak.
            BoxWithConstraints(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val side = minOf(maxWidth, maxHeight)
                if (showImage && edit.sourceImage != null) {
                    Image(
                        bitmap = edit.sourceImage,
                        contentDescription = null,
                        modifier = Modifier.size(side),
                    )
                } else {
                    EditBoard(edit = edit, onSquareTap = onSquareTap, modifier = Modifier.size(side))
                }
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

            // Egy sorban: oldalválasztó (paraszt-ikonok) + forgatás + betöltések + üres
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SideButton(
                    glyph = pieceChar(Piece.of(Piece.WHITE, Piece.PAWN)),
                    label = stringResource(R.string.white),
                    selected = edit.sideToMove == Piece.WHITE,
                    onClick = { onSide(Piece.WHITE) },
                )
                SideButton(
                    glyph = pieceChar(Piece.of(Piece.BLACK, Piece.PAWN)),
                    label = stringResource(R.string.black),
                    selected = edit.sideToMove == Piece.BLACK,
                    onClick = { onSide(Piece.BLACK) },
                )
                LabeledIconButton(
                    icon = Icons.Filled.SwapVert,
                    label = stringResource(R.string.flip_board),
                    onClick = onFlip,
                )
                LabeledIconButton(
                    icon = Icons.Filled.PhotoCamera,
                    label = stringResource(R.string.editor_lbl_recognize),
                    onClick = { galleryLauncher.launch("image/*") },
                    enabled = !recognizing,
                )
                LabeledIconButton(
                    icon = Icons.Filled.FileOpen,
                    label = stringResource(R.string.editor_lbl_file),
                    onClick = {
                        importError = false
                        fileLauncher.launch(arrayOf("*/*"))
                    },
                    enabled = !recognizing,
                )
                LabeledIconButton(
                    icon = Icons.Filled.ContentPaste,
                    label = stringResource(R.string.editor_lbl_string),
                    onClick = { showPaste = true },
                    enabled = !recognizing,
                )
                LabeledIconButton(
                    icon = Icons.Filled.Delete,
                    label = stringResource(R.string.editor_lbl_clear),
                    onClick = onClear,
                    enabled = !recognizing,
                )
            }
            if (importError) {
                Text(
                    stringResource(R.string.paste_fail),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
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

            // Fix alsó gombsor: Mégse / Betöltés — ikon + pici felirat
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LabeledIconButton(
                    icon = Icons.Filled.Close,
                    label = stringResource(R.string.cancel),
                    onClick = onCancel,
                )
                LabeledIconButton(
                    icon = Icons.Filled.Check,
                    label = stringResource(R.string.editor_load),
                    onClick = onConfirm,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** Oldalválasztó gomb: paraszt-glif + pici felirat; a kiválasztott kiemelve. */
@Composable
private fun SideButton(glyph: String, label: String, selected: Boolean, onClick: () -> Unit) {
    val labelColor = if (selected) MaterialTheme.colorScheme.primary
    else LocalContentColor.current.copy(alpha = 0.5f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(glyph, fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface)
        Text(label, fontSize = 9.sp, color = labelColor, maxLines = 1)
    }
}

/** FEN/PGN szöveg beillesztő párbeszéd (a vágólapról előtöltve). */
@Composable
private fun PasteDialog(onImportText: (String) -> String?, onDismiss: () -> Unit) {
    val clip = LocalClipboardManager.current
    var text by remember { mutableStateOf(clip.getText()?.text ?: "") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.paste_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.paste_hint), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = false },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 6,
                )
                if (error) {
                    Text(
                        stringResource(R.string.paste_fail),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (onImportText(text) == null) error = true else onDismiss()
            }) { Text(stringResource(R.string.editor_load)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun EditBoard(edit: EditState, onSquareTap: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
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
