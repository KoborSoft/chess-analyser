package com.koborsoft.chessanalyser.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.verticalScroll
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.core.content.FileProvider
import com.koborsoft.chessanalyser.GraphNodeUi
import java.io.File
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koborsoft.chessanalyser.GameConfig
import com.koborsoft.chessanalyser.GameMode
import com.koborsoft.chessanalyser.GameViewModel
import com.koborsoft.chessanalyser.R
import com.koborsoft.chessanalyser.TimeControl
import com.koborsoft.chessanalyser.core.Piece
import com.koborsoft.chessanalyser.core.Position
import com.koborsoft.chessanalyser.core.ResultType
import com.koborsoft.chessanalyser.engine.EngineSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    viewModel: GameViewModel,
    onSharePgn: (String) -> Unit,
    currentLanguage: String,
    onLanguageChange: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var showNewGame by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }

    LaunchedEffect(state.recognizeDone) {
        if (state.recognizeDone) viewModel.clearRecognizeDone()
    }

    // Finom, matt-kék színátmenetes háttér (a fehér helyett), téma szerint.
    val bgBrush = if (isSystemInDarkTheme()) {
        Brush.verticalGradient(listOf(Color(0xFF141C26), Color(0xFF1E2A38), Color(0xFF283A4E)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFEDF2F8), Color(0xFFDFE8F2), Color(0xFFD2DEEC)))
    }
    Box(modifier = Modifier.fillMaxSize().background(bgBrush)) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                actions = {
                    LabeledIconButton(
                        icon = Icons.Filled.Add,
                        label = stringResource(R.string.lbl_new),
                        onClick = { showNewGame = true },
                    )
                    LabeledIconButton(
                        icon = Icons.Filled.Analytics,
                        label = stringResource(R.string.lbl_analysis),
                        onClick = { viewModel.setAnalysisMode(!state.analysisMode) },
                        tint = if (state.analysisMode) MaterialTheme.colorScheme.primary
                        else LocalContentColor.current.copy(alpha = 0.4f),
                    )
                    LabeledIconButton(
                        icon = Icons.Filled.Edit,
                        label = stringResource(R.string.lbl_edit),
                        onClick = { viewModel.openEditor() },
                    )
                    LabeledIconButton(
                        icon = Icons.Filled.Settings,
                        label = stringResource(R.string.lbl_settings),
                        onClick = { showSettings = true },
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CompactTopRow(state = state)

            if (state.analysisMode) {
                EvalBar(evalCp = state.evalCp)
            }

            BoardView(
                position = state.position,
                selectedSquare = state.selectedSquare,
                legalTargets = state.legalTargets,
                lastMove = state.lastMove,
                undoneMove = state.undoneMove,
                hintArrows = state.hintArrows,
                threatArrows = state.threatArrows,
                flipped = state.boardFlipped,
                onSquareTap = viewModel::onSquareTap,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                LabeledIconButton(
                    icon = Icons.AutoMirrored.Filled.Undo,
                    label = stringResource(R.string.lbl_undo),
                    onClick = viewModel::undo,
                    enabled = state.sanMoves.isNotEmpty() && !state.engineThinking,
                )
                LabeledIconButton(
                    icon = Icons.Filled.Flag,
                    label = stringResource(R.string.lbl_resign),
                    onClick = viewModel::resign,
                    enabled = !state.result.isOver && state.sanMoves.isNotEmpty(),
                )
                LabeledIconButton(
                    icon = Icons.Filled.Share,
                    label = stringResource(R.string.lbl_share),
                    onClick = { onSharePgn(viewModel.exportPgn()) },
                )
                if (state.analysisMode) {
                    LabeledIconButton(
                        icon = Icons.Filled.Lightbulb,
                        label = stringResource(R.string.lbl_hints),
                        onClick = { viewModel.setShowHints(!state.showHints) },
                        tint = if (state.showHints) Color(0xFF2E7D32)
                        else LocalContentColor.current.copy(alpha = 0.4f),
                    )
                    LabeledIconButton(
                        icon = Icons.Filled.Warning,
                        label = stringResource(R.string.lbl_threats),
                        onClick = { viewModel.setShowThreats(!state.showThreats) },
                        tint = if (state.showThreats) Color(0xFFC62828)
                        else LocalContentColor.current.copy(alpha = 0.4f),
                    )
                }
            }

            if (state.analysisMode) {
                MoveGraph(
                    nodes = state.graphNodes,
                    currentId = state.currentNodeId,
                    onNodeTap = viewModel::gotoNode,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
        }
    }
    }

    if (showNewGame) {
        NewGameDialog(
            initial = state.config,
            onStart = { viewModel.newGame(it); showNewGame = false },
            onDismiss = { showNewGame = false },
        )
    }
    if (showSettings) {
        GameSettingsDialog(
            current = state.config,
            currentLanguage = currentLanguage,
            onLanguageChange = onLanguageChange,
            onHelp = { showSettings = false; showHelp = true },
            onApply = { mode, color, engine ->
                viewModel.applySettings(mode, color, engine)
                showSettings = false
            },
            onDismiss = { showSettings = false },
        )
    }
    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text(stringResource(R.string.help)) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    Text(stringResource(R.string.help_text))
                    Text(stringResource(R.string.project_url))
                    Text(stringResource(R.string.licenses), fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.license_text))
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelp = false }) {
                    Text(stringResource(R.string.ok))
                }
            },
        )
    }
    state.edit?.let { edit ->
        PositionEditor(
            edit = edit,
            recognizing = state.recognizing,
            recognizeError = state.recognizeError,
            onSquareTap = viewModel::onEditSquareTap,
            onBrush = viewModel::setEditBrush,
            onSide = viewModel::setEditSide,
            onClear = viewModel::clearEditBoard,
            onRecognize = viewModel::recognizeFromImage,
            onLoadFile = viewModel::importFile,
            onImportText = viewModel::importText,
            onConfirm = viewModel::confirmEdit,
            onCancel = viewModel::cancelEdit,
        )
    }
    state.pendingPromotion?.let {
        PromotionDialog(
            color = state.position.sideToMove,
            onChoose = viewModel::onPromotionChosen,
            onDismiss = viewModel::dismissPromotion,
        )
    }
}

/**
 * Értékelés-csík a tábla felett: a világos térfél aránya az állás Stockfish-
 * értékelését mutatja, a közepén a számértékkel (gyalogegységben).
 */
@Composable
private fun EvalBar(evalCp: Int?) {
    val fraction = if (evalCp == null) {
        0.5f
    } else {
        (0.5f + evalCp / 1000f).coerceIn(0.04f, 0.96f)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
            .background(Color(0xFF33475C)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction)
                .background(Color(0xFFF4F7FA)),
        )
        Text(
            text = evalCp?.let {
                String.format(java.util.Locale.US, "%+.1f", it / 100f)
            } ?: "…",
            modifier = Modifier
                .align(Alignment.Center)
                .background(Color(0xB3FFFFFF), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF33475C),
        )
    }
}

/** Bábuértékek az anyagi különbséghez: gyalog 1, huszár/futó 3, bástya 5, vezér 9. */
private val PIECE_POINTS = intArrayOf(0, 1, 3, 3, 5, 9, 0)
private val INITIAL_COUNTS = intArrayOf(0, 8, 2, 2, 2, 1, 1)

/** A [by] színű fél által levett ellenfél-bábuk, csökkenő érték szerint. */
private fun capturedPieces(position: Position, by: Int): List<Int> {
    val remaining = IntArray(7)
    for (sq in 0..63) {
        val piece = position.pieceAt(sq)
        if (piece != Piece.NONE && Piece.colorOf(piece) == -by) {
            remaining[Piece.typeOf(piece)]++
        }
    }
    val out = mutableListOf<Int>()
    for (type in intArrayOf(Piece.QUEEN, Piece.ROOK, Piece.BISHOP, Piece.KNIGHT, Piece.PAWN)) {
        repeat((INITIAL_COUNTS[type] - remaining[type]).coerceAtLeast(0)) {
            out.add(Piece.of(-by, type))
        }
    }
    return out
}

private fun materialScore(position: Position, color: Int): Int {
    var score = 0
    for (sq in 0..63) {
        val piece = position.pieceAt(sq)
        if (Piece.colorOf(piece) == color) score += PIECE_POINTS[Piece.typeOf(piece)]
    }
    return score
}

/**
 * Kompakt felső sor: balra a sötét által levett bábuk (és órája), középen a
 * státusz (ki lép / sakk / eredmény), jobbra a világos által levett bábuk
 * (és órája). A pontelőny (+N) az előnyben lévő fél oldalán jelenik meg.
 */
@Composable
private fun CompactTopRow(state: com.koborsoft.chessanalyser.UiState) {
    val tc = state.config.timeControl
    val capturedByBlack = capturedPieces(state.position, Piece.BLACK)
    val capturedByWhite = capturedPieces(state.position, Piece.WHITE)
    val whiteAdvantage = materialScore(state.position, Piece.WHITE) -
        materialScore(state.position, Piece.BLACK)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(horizontalAlignment = Alignment.Start) {
            Text(
                text = capturedByBlack.joinToString("") { pieceChar(it) } +
                    if (whiteAdvantage < 0) " +${-whiteAdvantage}" else "",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (tc != null) {
                val active = !state.result.isOver &&
                    state.position.sideToMove == Piece.BLACK
                Text(
                    text = formatClock(state.blackMs ?: tc.initialMs),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        StatusLine(state = state)
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = capturedByWhite.joinToString("") { pieceChar(it) } +
                    if (whiteAdvantage > 0) " +$whiteAdvantage" else "",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (tc != null) {
                val active = !state.result.isOver &&
                    state.position.sideToMove == Piece.WHITE
                Text(
                    text = formatClock(state.whiteMs ?: tc.initialMs),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

private fun formatClock(ms: Long): String {
    val totalSec = ms / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

@Composable
private fun StatusLine(state: com.koborsoft.chessanalyser.UiState) {
    val text = when {
        state.engineThinking -> stringResource(R.string.thinking)
        state.result.type == ResultType.CHECKMATE ->
            stringResource(
                R.string.checkmate_winner,
                stringResource(if (state.result.winner == Piece.WHITE) R.string.white else R.string.black),
            )
        state.result.type == ResultType.TIMEOUT ->
            stringResource(
                R.string.timeout_winner,
                stringResource(if (state.result.winner == Piece.WHITE) R.string.white else R.string.black),
            )
        state.result.type == ResultType.RESIGNATION ->
            stringResource(
                R.string.resignation_winner,
                stringResource(if (state.result.winner == Piece.WHITE) R.string.white else R.string.black),
            )
        state.result.type == ResultType.STALEMATE -> stringResource(R.string.stalemate)
        state.result.type == ResultType.DRAW_FIFTY -> stringResource(R.string.draw_fifty)
        state.result.type == ResultType.DRAW_REPETITION -> stringResource(R.string.draw_repetition)
        state.result.type == ResultType.DRAW_MATERIAL -> stringResource(R.string.draw_material)
        state.position.inCheck() -> stringResource(R.string.check)
        state.position.sideToMove == Piece.WHITE -> stringResource(R.string.white_to_move)
        else -> stringResource(R.string.black_to_move)
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
}

/**
 * Lépésgráf: fentről lefelé haladó fő ág, oldalra nyíló mellékágakkal.
 * Az aktuális csomópont kiemelt; bármelyik pöttyre koppintva az állás átáll.
 */
@Composable
private fun MoveGraph(
    nodes: List<GraphNodeUi>,
    currentId: Int,
    onNodeTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (nodes.size <= 1) return
    val rowH = 34.dp
    val colW = 84.dp
    val maxRow = nodes.maxOf { it.row }
    val maxCol = nodes.maxOf { it.col }
    val width = colW * (maxCol + 1) + 24.dp
    val height = rowH * (maxRow + 1) + 12.dp
    val textMeasurer = rememberTextMeasurer()
    val dotColor = MaterialTheme.colorScheme.secondary
    val currentColor = MaterialTheme.colorScheme.primary
    val lineColor = MaterialTheme.colorScheme.outline
    val labelStyle = MaterialTheme.typography.labelMedium.copy(
        color = MaterialTheme.colorScheme.onSurface,
    )
    val vScroll = rememberScrollState()
    val density = LocalDensity.current

    // Az aktuális csomópont maradjon látótérben.
    LaunchedEffect(currentId, nodes.size) {
        nodes.firstOrNull { it.id == currentId }?.let { node ->
            val target = with(density) { (rowH * node.row).roundToPx() } - 120
            vScroll.animateScrollTo(target.coerceAtLeast(0))
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(vScroll)
            .horizontalScroll(rememberScrollState()),
    ) {
        Canvas(
            modifier = Modifier
                .size(width, height)
                .pointerInput(nodes) {
                    detectTapGestures { tap ->
                        val rowPx = rowH.toPx()
                        val colPx = colW.toPx()
                        val hit = nodes.minByOrNull { n ->
                            val c = Offset(16.dp.toPx() + n.col * colPx, 14.dp.toPx() + n.row * rowPx)
                            (tap - c).getDistance()
                        } ?: return@detectTapGestures
                        val c = Offset(
                            16.dp.toPx() + hit.col * colPx,
                            14.dp.toPx() + hit.row * rowPx,
                        )
                        if ((tap - c).getDistance() < rowPx) onNodeTap(hit.id)
                    }
                },
        ) {
            val rowPx = rowH.toPx()
            val colPx = colW.toPx()
            fun center(row: Int, col: Int) =
                Offset(16.dp.toPx() + col * colPx, 14.dp.toPx() + row * rowPx)

            for (n in nodes) {
                if (n.row == 0) continue
                val p = center(n.parentRow, n.parentCol)
                val c = center(n.row, n.col)
                if (n.col == n.parentCol) {
                    drawLine(lineColor, p, c, strokeWidth = 2.dp.toPx())
                } else {
                    val corner = Offset(c.x, p.y)
                    drawLine(lineColor, p, corner, strokeWidth = 2.dp.toPx())
                    drawLine(lineColor, corner, c, strokeWidth = 2.dp.toPx())
                }
            }
            for (n in nodes) {
                val c = center(n.row, n.col)
                val isCurrent = n.id == currentId
                if (isCurrent) {
                    drawCircle(currentColor, radius = 10.dp.toPx(), center = c, alpha = 0.25f)
                }
                drawCircle(
                    color = if (isCurrent) currentColor else dotColor,
                    radius = (if (isCurrent) 6.dp else 5.dp).toPx(),
                    center = c,
                )
                if (n.row > 0) {
                    drawText(
                        textMeasurer,
                        n.san,
                        topLeft = Offset(c.x + 10.dp.toPx(), c.y - 9.dp.toPx()),
                        style = labelStyle,
                    )
                }
            }
        }
    }
}

@Composable
private fun PromotionDialog(color: Int, onChoose: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.promotion_title)) },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (type in listOf(Piece.QUEEN, Piece.ROOK, Piece.BISHOP, Piece.KNIGHT)) {
                    TextButton(onClick = { onChoose(type) }) {
                        Text(
                            pieceChar(Piece.of(color, type)),
                            style = MaterialTheme.typography.headlineLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun NewGameDialog(
    initial: GameConfig,
    onStart: (GameConfig) -> Unit,
    onDismiss: () -> Unit,
) {
    var mode by remember { mutableStateOf(initial.mode) }
    var color by remember { mutableStateOf(initial.humanColor) }
    var timeControl by remember { mutableStateOf(initial.timeControl) }

    // A motor paraméterei — a nehézséget közvetlenül ezek állítása adja.
    var eloValue by remember { mutableStateOf(initial.engine.elo.toFloat()) }
    var skillValue by remember { mutableStateOf(initial.engine.skill.toFloat()) }
    var depthValue by remember { mutableStateOf(initial.engine.depth.toFloat()) }
    var timeValue by remember { mutableStateOf(initial.engine.moveTimeMs.toFloat()) }

    fun engineSettings() = EngineSettings(
        elo = eloValue.toInt(),
        skill = skillValue.toInt(),
        depth = depthValue.toInt(),
        moveTimeMs = timeValue.toInt(),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_game)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(stringResource(R.string.opponent), fontWeight = FontWeight.Bold)
                SegChoice(
                    options = listOf(
                        GameMode.HUMAN_VS_ENGINE to stringResource(R.string.vs_engine),
                        GameMode.HUMAN_VS_HUMAN to stringResource(R.string.vs_human),
                    ),
                    selected = mode,
                    onSelect = { mode = it },
                    modifier = Modifier.fillMaxWidth(),
                )

                if (mode == GameMode.HUMAN_VS_ENGINE) {
                    Text(stringResource(R.string.your_color), fontWeight = FontWeight.Bold)
                    SegChoice(
                        options = listOf(
                            Piece.WHITE to stringResource(R.string.white),
                            Piece.BLACK to stringResource(R.string.black),
                        ),
                        selected = color,
                        onSelect = { color = it },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    EngineParamsControls(
                        eloValue = eloValue, onElo = { eloValue = it },
                        skillValue = skillValue, onSkill = { skillValue = it },
                        timeValue = timeValue, onTime = { timeValue = it },
                        depthValue = depthValue, onDepth = { depthValue = it },
                    )
                }

                Text(stringResource(R.string.time_control), fontWeight = FontWeight.Bold)
                SegChoice(
                    options = (listOf<TimeControl?>(null) + TimeControl.PRESETS).map {
                        it to (it?.name ?: stringResource(R.string.no_clock))
                    },
                    selected = timeControl,
                    onSelect = { timeControl = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onStart(
                    GameConfig(
                        mode = mode,
                        humanColor = color,
                        engine = engineSettings(),
                        timeControl = timeControl,
                    ),
                )
            }) { Text(stringResource(R.string.start)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** A motor 4 paraméter-csúszkája + becsült erősség; közös az új játék és a beállítások panelen. */
@Composable
private fun EngineParamsControls(
    eloValue: Float, onElo: (Float) -> Unit,
    skillValue: Float, onSkill: (Float) -> Unit,
    timeValue: Float, onTime: (Float) -> Unit,
    depthValue: Float, onDepth: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.engine_params), fontWeight = FontWeight.Bold)

        Text(
            if (eloValue.toInt() >= EngineSettings.ELO_MAX) {
                stringResource(R.string.elo_unlimited)
            } else {
                stringResource(R.string.elo_value, eloValue.toInt())
            },
        )
        Slider(
            value = eloValue,
            onValueChange = onElo,
            valueRange = EngineSettings.ELO_MIN.toFloat()..EngineSettings.ELO_MAX.toFloat(),
        )

        Text(
            if (skillValue.toInt() >= EngineSettings.SKILL_MAX) {
                stringResource(R.string.skill_full)
            } else {
                stringResource(R.string.skill_value, skillValue.toInt())
            },
        )
        Slider(
            value = skillValue,
            onValueChange = onSkill,
            valueRange = 0f..20f,
            steps = 19,
        )

        Text(stringResource(R.string.time_value, timeValue.toInt()))
        Slider(
            value = timeValue,
            onValueChange = onTime,
            valueRange = 100f..3000f,
        )

        Text(
            if (depthValue.toInt() >= EngineSettings.DEPTH_MAX) {
                stringResource(R.string.depth_unlimited)
            } else {
                stringResource(R.string.depth_value, depthValue.toInt())
            },
        )
        Slider(
            value = depthValue,
            onValueChange = onDepth,
            valueRange = EngineSettings.DEPTH_MIN.toFloat()..EngineSettings.DEPTH_MAX.toFloat(),
            steps = EngineSettings.DEPTH_MAX - EngineSettings.DEPTH_MIN - 1,
        )

        Text(
            stringResource(
                R.string.estimated_strength,
                EngineSettings(
                    elo = eloValue.toInt(),
                    skill = skillValue.toInt(),
                    depth = depthValue.toInt(),
                    moveTimeMs = timeValue.toInt(),
                ).estimatedElo(),
            ),
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * Játszma közbeni beállítások: ellenfél típusa és színe, motorparaméterek
 * (azonnal érvénybe lépnek), valamint a javaslat- és veszélyjelzés kapcsolói.
 */
@Composable
private fun GameSettingsDialog(
    current: GameConfig,
    currentLanguage: String,
    onLanguageChange: (String) -> Unit,
    onHelp: () -> Unit,
    onApply: (GameMode, Int, EngineSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    var mode by remember { mutableStateOf(current.mode) }
    var color by remember { mutableStateOf(current.humanColor) }
    var eloValue by remember { mutableStateOf(current.engine.elo.toFloat()) }
    var skillValue by remember { mutableStateOf(current.engine.skill.toFloat()) }
    var timeValue by remember { mutableStateOf(current.engine.moveTimeMs.toFloat()) }
    var depthValue by remember { mutableStateOf(current.engine.depth.toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(stringResource(R.string.opponent), fontWeight = FontWeight.Bold)
                SegChoice(
                    options = listOf(
                        GameMode.HUMAN_VS_ENGINE to stringResource(R.string.vs_engine),
                        GameMode.HUMAN_VS_HUMAN to stringResource(R.string.vs_human),
                    ),
                    selected = mode,
                    onSelect = { mode = it },
                    modifier = Modifier.fillMaxWidth(),
                )

                if (mode == GameMode.HUMAN_VS_ENGINE) {
                    Text(stringResource(R.string.your_color), fontWeight = FontWeight.Bold)
                    SegChoice(
                        options = listOf(
                            Piece.WHITE to stringResource(R.string.white),
                            Piece.BLACK to stringResource(R.string.black),
                        ),
                        selected = color,
                        onSelect = { color = it },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    EngineParamsControls(
                        eloValue = eloValue, onElo = { eloValue = it },
                        skillValue = skillValue, onSkill = { skillValue = it },
                        timeValue = timeValue, onTime = { timeValue = it },
                        depthValue = depthValue, onDepth = { depthValue = it },
                    )
                }

                Text(stringResource(R.string.language), fontWeight = FontWeight.Bold)
                val languageOptions = listOf(
                    "" to stringResource(R.string.language_auto),
                    "hu" to "🇭🇺 Magyar",
                    "en" to "🇬🇧 English",
                )
                DropdownField(
                    label = stringResource(R.string.language),
                    selectedLabel = languageOptions.firstOrNull { it.first == currentLanguage }
                        ?.second ?: stringResource(R.string.language_auto),
                    options = languageOptions.map { it.second },
                    onSelect = { idx -> onLanguageChange(languageOptions[idx].first) },
                )

                TextButton(onClick = onHelp) {
                    Icon(
                        Icons.AutoMirrored.Filled.HelpOutline,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.help))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApply(
                    mode,
                    color,
                    EngineSettings(
                        elo = eloValue.toInt(),
                        skill = skillValue.toInt(),
                        depth = depthValue.toInt(),
                        moveTimeMs = timeValue.toInt(),
                    ),
                )
            }) { Text(stringResource(R.string.apply)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}


/**
 * Ikongomb rövid felirattal alatta (pici betűvel). A felirat és az ikon színe
 * közös ([tint]); letiltva mindkettő elhalványul.
 */
@Composable
fun LabeledIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = LocalContentColor.current,
) {
    val color = if (enabled) tint else tint.copy(alpha = 0.38f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(22.dp))
        Text(
            label,
            color = color,
            fontSize = 9.sp,
            lineHeight = 10.sp,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

/** Legördülő választómező (combobox): a kijelölt érték + lenyíló menü. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    label: String,
    selectedLabel: String,
    options: List<String>,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEachIndexed { i, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = { onSelect(i); expanded = false },
                )
            }
        }
    }
}

/**
 * Szegmentált választó: a kijelölt szegmens kitöltött és pipát kap, így
 * egyértelmű, melyik az aktív (a FilterChip fordított logikája helyett).
 */
@Composable
fun <T> SegChoice(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        options.forEachIndexed { i, (value, label) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
            ) { Text(label) }
        }
    }
}
