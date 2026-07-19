package com.koborsoft.chessanalyser

import android.app.Application
import android.content.Context
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.koborsoft.chessanalyser.core.Game
import com.koborsoft.chessanalyser.core.GameNode
import com.koborsoft.chessanalyser.core.GameResult
import com.koborsoft.chessanalyser.core.GameTree
import com.koborsoft.chessanalyser.core.Move
import com.koborsoft.chessanalyser.core.MoveGenerator
import com.koborsoft.chessanalyser.core.Pgn
import com.koborsoft.chessanalyser.core.Piece
import com.koborsoft.chessanalyser.core.Position
import com.koborsoft.chessanalyser.core.Square
import com.koborsoft.chessanalyser.engine.BuiltInEngine
import com.koborsoft.chessanalyser.engine.ChessEngine
import com.koborsoft.chessanalyser.engine.EngineSettings
import com.koborsoft.chessanalyser.engine.ScoredMove
import com.koborsoft.chessanalyser.engine.StockfishEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class GameMode { HUMAN_VS_HUMAN, HUMAN_VS_ENGINE }

/** Időkontroll; null idő = óra nélküli játék. */
data class TimeControl(val name: String, val initialMs: Long, val incrementMs: Long) {
    companion object {
        val PRESETS = listOf(
            TimeControl("1+0", 60_000, 0),
            TimeControl("3+2", 180_000, 2_000),
            TimeControl("5+0", 300_000, 0),
            TimeControl("10+0", 600_000, 0),
            TimeControl("15+10", 900_000, 10_000),
        )
    }
}

data class GameConfig(
    val mode: GameMode = GameMode.HUMAN_VS_ENGINE,
    val humanColor: Int = Piece.WHITE,
    /** A gépi ellenfél paraméterei — a nehézséget közvetlenül ezek adják. */
    val engine: EngineSettings = EngineSettings(),
    val timeControl: TimeControl? = null,
)

/** Egy megjelenítendő nyíl: lépés, ráírt felirat és erősség (0..1). */
data class ArrowUi(val move: Move, val label: String, val strength: Float)

/**
 * Fotóból felismert állás szerkeszthető előnézete. A [board] a 64 mező
 * (a1=0 … h8=63), a [brush] a kijelölt „ecset" (bábu vagy üres) a koppintáshoz.
 */
data class EditState(
    val board: IntArray,
    val sideToMove: Int,
    val flipped: Boolean,
    val brush: Int,
    /** A felismeréshez használt forráskép, hogy a szerkesztő mellett megjeleníthető legyen. */
    val sourceImage: androidx.compose.ui.graphics.ImageBitmap? = null,
    val error: String? = null,
    /** A felismerő által bizonytalannak jelölt mezők (a1=0 … h8=63) — halvány kiemelés. */
    val uncertain: Set<Int> = emptySet(),
)

/** A lépésgráf egy megjelenítendő csomópontja. */
data class GraphNodeUi(
    val id: Int,
    val san: String,
    val row: Int,
    val col: Int,
    val parentRow: Int,
    val parentCol: Int,
    val byWhite: Boolean,
)

data class UiState(
    val position: Position,
    val sanMoves: List<String> = emptyList(),
    val selectedSquare: Int? = null,
    val legalTargets: Set<Int> = emptySet(),
    val lastMove: Move? = null,
    val result: GameResult = GameResult.ONGOING,
    val config: GameConfig = GameConfig(),
    val whiteMs: Long? = null,
    val blackMs: Long? = null,
    val engineThinking: Boolean = false,
    /** Az imént visszavont lépés — a tábla ezt visszafelé animálja. */
    val undoneMove: Move? = null,
    /** Átváltozás alatt álló lépés (from, to), amíg a játékos bábut választ. */
    val pendingPromotion: Pair<Int, Int>? = null,
    val boardFlipped: Boolean = false,
    val usingStockfish: Boolean = false,
    /** Javasolt lépések nyilai, jóság szerint csökkenő sorrendben. */
    val hintArrows: List<ArrowUi> = emptyList(),
    /** Az ellenfél legerősebb (legveszélyesebb) lépéseinek nyilai. */
    val threatArrows: List<ArrowUi> = emptyList(),
    val showHints: Boolean = false,
    val showThreats: Boolean = false,
    /**
     * Analízis üzemmód: gráf, értékelés-csík és nyilak láthatók, az óra áll.
     * Kikapcsolva tiszta játékmód: fut az óra, elemzés nem történik.
     */
    val analysisMode: Boolean = true,
    /** Az állás Stockfish-értékelése centigyalogban, világos szemszögéből. */
    val evalCp: Int? = null,
    /** A lépésgráf csomópontjai és az aktuális csomópont azonosítója. */
    val graphNodes: List<GraphNodeUi> = emptyList(),
    val currentNodeId: Int = 0,
    /** Offline állás-felismerés állapota (fotóból, beépített CNN-nel). */
    val recognizing: Boolean = false,
    val recognizeError: String? = null,
    val recognizeDone: Boolean = false,
    /** Fotó-felismerés utáni szerkeszthető előnézet; null, ha nem szerkesztünk. */
    val edit: EditState? = null,
)

class GameViewModel(app: Application) : AndroidViewModel(app) {

    /** A játszma elágazásokkal (lépésgráf); a [game] a kiválasztott ág lineárisan. */
    private var tree = GameTree()
    private var game = Game()
    private var engine: ChessEngine? = null
    private var clockJob: Job? = null
    private var engineJob: Job? = null
    private var hintJob: Job? = null

    /**
     * A jelzésekhez két saját, teljes erejű Stockfish elemző-folyamat fut
     * (MultiPV, javaslat + veszély párhuzamosan), hogy ne zavarják az
     * ellenfélként játszó motort; ha nincs Stockfish, a beépített motor pontoz.
     */
    private var hintAnalysisEngine: StockfishEngine? = null
    private var hintAnalysisTried = false
    private var threatAnalysisEngine: StockfishEngine? = null
    private var threatAnalysisTried = false
    private val fallbackAnalysis = BuiltInEngine(EngineSettings(elo = 1500))

    private suspend fun hintAnalysis(): StockfishEngine? {
        if (!hintAnalysisTried) {
            hintAnalysisTried = true
            hintAnalysisEngine = withContext(Dispatchers.IO) {
                StockfishEngine.createIfAvailable(getApplication(), EngineSettings())
            }
        }
        return hintAnalysisEngine
    }

    private suspend fun threatAnalysis(): StockfishEngine? {
        if (!threatAnalysisTried) {
            threatAnalysisTried = true
            threatAnalysisEngine = withContext(Dispatchers.IO) {
                StockfishEngine.createIfAvailable(getApplication(), EngineSettings())
            }
        }
        return threatAnalysisEngine
    }

    /** A kiválasztott ág lineáris játszmájának újraépítése a fából. */
    private fun syncFromTree() {
        val g = Game(tree.rootPosition)
        for (node in tree.pathTo(tree.current)) {
            node.move?.let { g.play(it) }
        }
        game = g
    }

    /** A fa elrendezése a gráf-nézethez: fő ág lefelé, mellékágak új oszlopban. */
    private fun buildGraph(): List<GraphNodeUi> {
        val out = mutableListOf<GraphNodeUi>()
        var nextFreeCol = 1
        fun layout(node: GameNode, row: Int, col: Int, parentRow: Int, parentCol: Int) {
            if (col >= nextFreeCol) nextFreeCol = col + 1
            val move = node.move
            val byWhite = move != null &&
                Piece.colorOf(node.position.pieceAt(move.to)) == Piece.WHITE
            out.add(GraphNodeUi(node.id, node.san ?: "•", row, col, parentRow, parentCol, byWhite))
            node.children.forEachIndexed { i, child ->
                val childCol = if (i == 0) col else nextFreeCol++
                layout(child, row + 1, childCol, row, col)
            }
        }
        layout(tree.root, 0, 0, 0, 0)
        return out
    }

    private val _state = MutableStateFlow(UiState(position = game.position))
    val state: StateFlow<UiState> = _state

    private val prefs = app.getSharedPreferences("sakk", Context.MODE_PRIVATE)

    init {
        restoreAutoSave()
        _state.value = _state.value.copy(
            showHints = prefs.getBoolean(KEY_HINTS, false),
            showThreats = prefs.getBoolean(KEY_THREATS, false),
            analysisMode = prefs.getBoolean(KEY_ANALYSIS, true),
        )
        startClockIfNeeded()
        maybeEngineMove()
        updateIndicators()
    }

    /** A szerkesztő megnyitása az AKTUÁLIS állással (alapból ezt szerkesztheted). */
    fun openEditor() {
        val pos = game.position
        _state.value = _state.value.copy(
            recognizeError = null,
            edit = EditState(
                board = pos.board.copyOf(),
                sideToMove = pos.sideToMove,
                flipped = _state.value.boardFlipped,
                brush = Piece.NONE,
            ),
        )
    }

    /** A szerkesztő tábláját üresre állítja (nulláról rögzítéshez). */
    fun clearEditBoard() {
        val e = _state.value.edit ?: return
        _state.value = _state.value.copy(
            recognizeError = null,
            edit = e.copy(board = IntArray(64), uncertain = emptySet(), error = null),
        )
    }

    /**
     * A nyitott szerkesztő tábláját feltölti egy képről felismert állással.
     * A tájolást a szerkesztő „Ki lép" értéke adja (a szabály: a lépő van alul);
     * ha rossz, a felhasználó átállítja a „Ki lép"-et és újra felismer. A művelet
     * ismételhető (más képpel vagy ugyanazzal). Alacsony megbízhatóságnál a jelenlegi
     * tábla marad, csak hibaüzenet jelenik meg.
     */
    fun recognizeFromImage(uri: android.net.Uri) {
        val e = _state.value.edit ?: return
        if (_state.value.recognizing) return
        val sideToMove = e.sideToMove
        _state.value = _state.value.copy(recognizing = true, recognizeError = null)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ctx = getApplication<Application>()
                val bitmap = ctx.contentResolver.openInputStream(uri).use {
                    android.graphics.BitmapFactory.decodeStream(it)
                } ?: throw java.io.IOException("A kép nem olvasható")

                val maxSide = maxOf(bitmap.width, bitmap.height)
                val scaled = if (maxSide > 1280) {
                    val scale = 1280f / maxSide
                    android.graphics.Bitmap.createScaledBitmap(
                        bitmap,
                        (bitmap.width * scale).toInt(),
                        (bitmap.height * scale).toInt(),
                        true,
                    )
                } else {
                    bitmap
                }

                // Offline CNN-felismerés a TELJES felbontású képen (a downscale
                // csak az előnézeti forráskép).
                val recog = com.koborsoft.chessanalyser.recognizer.CnnRecognizer
                    .recognize(ctx, bitmap)
                // Ha nem találtunk megbízhatóan sakktáblát, ne dobjunk zagyvát:
                // a jelenlegi tábla marad, csak hibaüzenet.
                if (recog.boardConfidence < BOARD_CONF_MIN) {
                    val msg = ctx.getString(R.string.recog_no_board)
                    withContext(Dispatchers.Main) {
                        _state.value = _state.value.copy(recognizing = false, recognizeError = msg)
                    }
                    return@launch
                }
                // A CNN a képet fehér-szemszögűként olvassa (a kép teteje = 8. sor).
                // A szabály: a LÉPŐ fél van alul. Ezért ha fekete lép, a beolvasott
                // táblát 180°-kal elforgatjuk (a bábuk a valós soraikra kerülnek).
                var board = placementToBoard(recog.placement)
                var uncertain = buildSet {
                    for (i in 0 until 8) for (c in 0 until 8) {
                        if (recog.confidences[i * 8 + c] < 0.55f) add((7 - i) * 8 + c)
                    }
                }
                if (sideToMove == Piece.BLACK) {
                    val rot = IntArray(64)
                    for (sq in 0..63) rot[63 - sq] = board[sq]
                    board = rot
                    uncertain = uncertain.map { 63 - it }.toSet()
                }
                val sourceImage = scaled.asImageBitmap()
                withContext(Dispatchers.Main) {
                    val cur = _state.value.edit
                    if (cur == null) return@withContext  // közben bezárták
                    _state.value = _state.value.copy(
                        recognizing = false,
                        edit = cur.copy(
                            board = board,
                            // A nézet a lépő szemszögére áll, hogy egyezzen a fotóval.
                            flipped = sideToMove == Piece.BLACK,
                            sourceImage = sourceImage,
                            error = recog.reason,
                            uncertain = uncertain,
                        ),
                    )
                }
            } catch (ex: Exception) {
                android.util.Log.e("CnnRecog", "recognizeFromImage hiba", ex)
                _state.value = _state.value.copy(
                    recognizing = false,
                    recognizeError = ex.message ?: "Ismeretlen hiba",
                )
            }
        }
    }

    /** FEN bábu-elhelyezésből 64 elemű tábla (a1=0 … h8=63); hibás mezőket kihagyja. */
    private fun placementToBoard(placement: String): IntArray {
        val board = IntArray(64)
        val ranks = placement.split("/")
        for ((i, rankStr) in ranks.withIndex()) {
            if (i > 7) break
            val r = 7 - i
            var f = 0
            for (c in rankStr) {
                if (f > 7) break
                if (c.isDigit()) {
                    f += c - '0'
                } else {
                    val type = Piece.typeFromLetter(c)
                    if (type != Piece.NONE) {
                        val color = if (c.isUpperCase()) Piece.WHITE else Piece.BLACK
                        board[Square.of(f, r)] = Piece.of(color, type)
                    }
                    f++
                }
            }
        }
        return board
    }

    // --- Szerkeszthető előnézet ---

    fun onEditSquareTap(sq: Int) {
        val e = _state.value.edit ?: return
        val newBoard = e.board.copyOf()
        newBoard[sq] = e.brush
        _state.value = _state.value.copy(edit = e.copy(board = newBoard, error = null))
    }

    fun setEditBrush(piece: Int) {
        val e = _state.value.edit ?: return
        _state.value = _state.value.copy(edit = e.copy(brush = piece))
    }

    /**
     * „Ki lép" beállítása: kinek a köre + a KÖVETKEZŐ felismerés tájolási tippje
     * (a szabály: a lépő van alul). A meglévő táblát NEM forgatja — normál
     * szerkesztésnél az rossz lenne; a tájolást a felismerés alkalmazza, és ha
     * téves, a „Ki lép" átállítása után újra fel kell ismerni.
     */
    fun setEditSide(color: Int) {
        val e = _state.value.edit ?: return
        _state.value = _state.value.copy(edit = e.copy(sideToMove = color))
    }

    /**
     * „Forgatás" a szerkesztőben = NÉZET-forgatás (mint a fő táblán): a másik
     * szemszögből mutatja a táblát, a bábukat NEM pakolja át (az állás nem változik).
     */
    fun flipEditBoard() {
        val e = _state.value.edit ?: return
        _state.value = _state.value.copy(edit = e.copy(flipped = !e.flipped))
    }

    fun cancelEdit() {
        _state.value = _state.value.copy(edit = null, recognizeDone = false)
    }

    /** A szerkesztett állás elfogadása és betöltése; hibát jelez, ha szabálytalan. */
    fun confirmEdit() {
        val e = _state.value.edit ?: return
        val whiteKings = e.board.count { it == Piece.of(Piece.WHITE, Piece.KING) }
        val blackKings = e.board.count { it == Piece.of(Piece.BLACK, Piece.KING) }
        if (whiteKings != 1 || blackKings != 1) {
            _state.value = _state.value.copy(edit = e.copy(error = "one_king_each"))
            return
        }
        val pos = Position(
            board = e.board.copyOf(),
            sideToMove = e.sideToMove,
            castling = 0,
            epSquare = -1,
            halfmoveClock = 0,
            fullmoveNumber = 1,
        )
        _state.value = _state.value.copy(edit = null, recognizeDone = true)
        loadRecognizedPosition(pos)
    }

    private fun loadRecognizedPosition(pos: Position) {
        engineJob?.cancel()
        tree = GameTree(pos)
        syncFromTree()
        _state.value = _state.value.copy(
            position = game.position,
            sanMoves = emptyList(),
            selectedSquare = null,
            legalTargets = emptySet(),
            lastMove = null,
            undoneMove = null,
            hintArrows = emptyList(),
            threatArrows = emptyList(),
            graphNodes = buildGraph(),
            currentNodeId = tree.current.id,
            result = game.result(),
            recognizing = false,
            recognizeDone = true,
            // A fő tábla is a lépő szemszögéből (egyezzen a fotóval/szerkesztővel).
            boardFlipped = pos.sideToMove == Piece.BLACK,
        )
        autoSave()
        maybeEngineMove()
        updateIndicators()
    }

    fun clearRecognizeDone() {
        _state.value = _state.value.copy(recognizeDone = false, recognizeError = null)
    }

    /**
     * Analízis be/ki. Bekapcsolva megáll az óra és elindul az elemzés;
     * kikapcsolva a futó elemzések leállnak, és (időkontrollnál) újra
     * ketyeg az óra.
     */
    fun setAnalysisMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ANALYSIS, enabled).apply()
        if (enabled) {
            _state.value = _state.value.copy(analysisMode = true)
            updateIndicators()
        } else {
            hintJob?.cancel()
            hintStreamJob?.cancel()
            threatStreamJob?.cancel()
            _state.value = _state.value.copy(
                analysisMode = false,
                hintArrows = emptyList(),
                threatArrows = emptyList(),
            )
            // Ha épp a gép jönne, játékmódra váltva azonnal lép.
            maybeEngineMove()
        }
    }

    /**
     * A jelzés-kapcsolók nem indítanak új elemzést: az aktuális állás kész
     * (vagy éppen készülő) eredményét mutatják meg vagy rejtik el; elemzés
     * csak akkor indul, ha ehhez az álláshoz még nem futott.
     */
    fun setShowHints(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HINTS, enabled).apply()
        _state.value = _state.value.copy(
            showHints = enabled,
            hintArrows = if (enabled) cachedHints else emptyList(),
        )
        if (enabled && analysedFen == game.position.fen() &&
            cachedHints.isEmpty() && hintStreamJob?.isActive != true
        ) {
            startHintStream(game)
        }
    }

    fun setShowThreats(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_THREATS, enabled).apply()
        _state.value = _state.value.copy(
            showThreats = enabled,
            threatArrows = if (enabled) cachedThreats else emptyList(),
        )
        if (enabled && analysedFen == game.position.fen() &&
            cachedThreats.isEmpty() && threatStreamJob?.isActive != true
        ) {
            startThreatStream(game)
        }
    }

    /**
     * Nyíl-felirat: a lépés utáni Stockfish-értékelés gyalogban, a lépő fél
     * szemszögéből (pl. +0.9, -1.5); matt: M3, mattot kapó lépés: -M3.
     * Az erősség (szín + vastagság) a statikus értékeléshez mért javulás
     * aránya a legjobb lépéshez képest.
     */
    private fun toArrows(moves: List<ScoredMove>, moverBaseCp: Int?): List<ArrowUi> {
        if (moves.isEmpty()) return emptyList()
        val base = moverBaseCp ?: moves.first().score
        val bestGain = moves.first().score - base
        return moves.map { sm ->
            val label = when {
                sm.score > 90_000 -> "M${100_000 - sm.score}"
                sm.score < -90_000 -> "-M${-100_000 - sm.score}"
                else -> formatEvalLabel(sm.score / 100f)
            }
            val strength = if (bestGain <= 0) {
                if (sm.score == moves.first().score) 1f else 0f
            } else {
                ((sm.score - base).toFloat() / bestGain).coerceIn(0f, 1f)
            }
            ArrowUi(sm.move, label, strength)
        }
    }

    private fun formatEvalLabel(v: Float): String {
        val s = String.format(Locale.US, "%+.1f", v)
        return if (s == "+0.0" || s == "-0.0") "0.0" else s
    }

    /**
     * Javaslat- és veszélyjelzés frissítése: a javaslatok a lépni következő
     * (emberi) fél legjobb lépései, a veszélyek az ellenfél legerősebb
     * válaszai — mindkettő motor-pontszám szerint.
     */
    /** Álláshoz kötött elemzés-gyorsítótár: kapcsolgatásra nem számol újra. */
    private var analysedFen: String? = null
    private var cachedHints: List<ArrowUi> = emptyList()
    private var cachedThreats: List<ArrowUi> = emptyList()
    private var moverBaseCache: Int? = null
    private var hintStreamJob: Job? = null
    private var threatStreamJob: Job? = null

    /** Állásváltozáskor hívódik: értékelés-csík + (bekapcsolt) elemzések indítása. */
    private fun updateIndicators() {
        hintJob?.cancel()
        hintStreamJob?.cancel()
        threatStreamJob?.cancel()
        analysedFen = null
        cachedHints = emptyList()
        cachedThreats = emptyList()
        moverBaseCache = null

        // Analízis üzemmódon kívül nem fut elemzés.
        if (!_state.value.analysisMode) return

        val s = _state.value
        val currentGame = game
        hintJob = viewModelScope.launch {
            val sf = hintAnalysis()

            // Értékelés-csík: minden állásnál frissül (világos szemszöge).
            val whiteCp = sf?.staticEval(currentGame)
            _state.value = _state.value.copy(evalCp = whiteCp)

            // Analízis módban minden állás elemezhető (a felhasználó bármelyik
            // fél helyett léphet), ezért csak a játszma végén nincs elemzés.
            if (s.result.isOver) return@launch

            moverBaseCache = whiteCp?.let {
                if (currentGame.position.sideToMove == Piece.WHITE) it else -it
            }
            analysedFen = currentGame.position.fen()

            if (s.showHints) startHintStream(currentGame)
            if (s.showThreats) startThreatStream(currentGame)
        }
    }

    /**
     * Javaslat-elemzés: mindig teljes erővel, 5 mp-ig, félmásodpercenként
     * frissülő eredménnyel; az eredmény a gyorsítótárba is kerül.
     */
    private fun startHintStream(currentGame: Game) {
        if (hintStreamJob?.isActive == true) return
        hintStreamJob = viewModelScope.launch {
            val sf = hintAnalysis()
            val base = moverBaseCache
            fun deliver(moves: List<ScoredMove>) {
                val arrows = toArrows(moves, base)
                cachedHints = arrows
                if (_state.value.showHints) {
                    _state.value = _state.value.copy(hintArrows = arrows)
                }
            }
            if (sf != null) {
                sf.analyseStream(
                    currentGame, HINT_COUNT, ANALYSE_TOTAL_MS, ANALYSE_UPDATE_MS,
                ) { deliver(it) }
            } else {
                deliver(fallbackAnalysis.scoredMoves(currentGame, HINT_COUNT))
            }
        }
    }

    /** Veszély-elemzés: az ellenfél legjobb lépései, ha ő léphetne most. */
    private fun startThreatStream(currentGame: Game) {
        if (threatStreamJob?.isActive == true) return
        // Sakkban állva az ellenfél "lépései" nem értelmezhetők; ilyenkor
        // maga a sakk a veszély, amit a tábla már jelez.
        if (currentGame.position.inCheck()) return
        threatStreamJob = viewModelScope.launch {
            val opponentToMove = Position(
                currentGame.position.board.copyOf(), -currentGame.position.sideToMove,
                currentGame.position.castling, -1, 0, 1,
            )
            val threatGame = Game(opponentToMove)
            val base = moverBaseCache?.let { -it }
            fun deliver(moves: List<ScoredMove>) {
                val arrows = toArrows(moves, base)
                cachedThreats = arrows
                if (_state.value.showThreats) {
                    _state.value = _state.value.copy(threatArrows = arrows)
                }
            }
            val sf = threatAnalysis()
            if (sf != null) {
                sf.analyseStream(
                    threatGame, THREAT_COUNT, ANALYSE_TOTAL_MS, ANALYSE_UPDATE_MS,
                ) { deliver(it) }
            } else {
                deliver(fallbackAnalysis.scoredMoves(threatGame, THREAT_COUNT))
            }
        }
    }

    fun newGame(config: GameConfig) {
        engineJob?.cancel()
        engine?.close()
        tree = GameTree()
        syncFromTree()
        engine = if (config.mode == GameMode.HUMAN_VS_ENGINE) createEngine(config) else null
        _state.value = UiState(
            position = game.position,
            graphNodes = buildGraph(),
            currentNodeId = tree.current.id,
            config = config,
            whiteMs = config.timeControl?.initialMs,
            blackMs = config.timeControl?.initialMs,
            boardFlipped = config.mode == GameMode.HUMAN_VS_ENGINE &&
                config.humanColor == Piece.BLACK,
            usingStockfish = engine is StockfishEngine,
        )
        startClockIfNeeded()
        maybeEngineMove()
        updateIndicators()
    }

    private fun createEngine(config: GameConfig): ChessEngine =
        StockfishEngine.createIfAvailable(getApplication(), config.engine)
            ?: BuiltInEngine(config.engine)

    fun onSquareTap(sq: Int) {
        val s = _state.value
        if (s.result.isOver || s.pendingPromotion != null || s.engineThinking) return
        // Analízis módban a felhasználó mindkét fél helyett léphet (a gép
        // állásában is ő lép); játékmódban csak a saját színével.
        if (!s.analysisMode && s.config.mode == GameMode.HUMAN_VS_ENGINE &&
            game.position.sideToMove != s.config.humanColor
        ) return

        val piece = game.position.pieceAt(sq)
        when {
            s.selectedSquare == null || sq !in s.legalTargets -> {
                if (Piece.colorOf(piece) == game.position.sideToMove && sq != s.selectedSquare) {
                    val targets = MoveGenerator.legalMovesFrom(game.position, sq)
                        .map { it.to }.toSet()
                    _state.value = s.copy(selectedSquare = sq, legalTargets = targets)
                } else {
                    _state.value = s.copy(selectedSquare = null, legalTargets = emptySet())
                }
            }
            else -> {
                val from = s.selectedSquare
                val candidates = MoveGenerator.legalMovesFrom(game.position, from)
                    .filter { it.to == sq }
                if (candidates.any { it.promotion != 0 }) {
                    _state.value = s.copy(pendingPromotion = from to sq)
                } else {
                    candidates.firstOrNull()?.let { playHumanMove(it) }
                }
            }
        }
    }

    fun onPromotionChosen(pieceType: Int) {
        val s = _state.value
        val (from, to) = s.pendingPromotion ?: return
        _state.value = s.copy(pendingPromotion = null)
        MoveGenerator.legalMovesFrom(game.position, from)
            .firstOrNull { it.to == to && it.promotion == pieceType }
            ?.let { playHumanMove(it) }
    }

    fun dismissPromotion() {
        _state.value = _state.value.copy(pendingPromotion = null)
    }

    private fun playHumanMove(move: Move) {
        if (!tree.play(move)) return
        syncFromTree()
        addIncrement(-game.position.sideToMove)
        publish()
        autoSave()
        maybeEngineMove()
    }

    /**
     * Ugrás a gráf egy csomópontjára; az állás átáll, a gép nem lép magától —
     * ha a gép jönne, analízis módban a felhasználó lép helyette.
     */
    fun gotoNode(id: Int) {
        if (_state.value.engineThinking) return
        val node = tree.nodeById(id) ?: return
        if (node === tree.current) return
        tree.goto(node)
        syncFromTree()
        publish()
        autoSave()
    }

    private fun maybeEngineMove() {
        val s = _state.value
        if (s.config.mode != GameMode.HUMAN_VS_ENGINE) return
        if (s.result.isOver || game.result().isOver) return
        if (game.position.sideToMove == s.config.humanColor) return
        val eng = engine ?: createEngine(s.config).also {
            engine = it
            _state.value = _state.value.copy(usingStockfish = it is StockfishEngine)
        }

        _state.value = _state.value.copy(engineThinking = true)
        engineJob = viewModelScope.launch {
            val move = eng.bestMove(game)
            if (move != null && tree.play(move)) {
                syncFromTree()
                addIncrement(-game.position.sideToMove)
            }
            _state.value = _state.value.copy(engineThinking = false)
            publish()
            autoSave()
        }
    }

    /** Visszavonás: gép ellen a teljes lépéspárt vonja vissza; az ág megmarad. */
    fun undo() {
        if (_state.value.engineThinking) return
        val s = _state.value
        val undone = tree.current.move ?: return
        tree.undo()
        if (s.config.mode == GameMode.HUMAN_VS_ENGINE &&
            tree.current.position.sideToMove != s.config.humanColor
        ) {
            tree.undo()
        }
        syncFromTree()
        publish()
        _state.value = _state.value.copy(undoneMove = undone)
        autoSave()
    }

    /**
     * Játszma közbeni beállítás: ellenfél típusa, szín és motorparaméterek.
     * Azonnal érvénybe lép; ha az új felállásban a gép következik, lép.
     */
    fun applySettings(mode: GameMode, humanColor: Int, settings: EngineSettings) {
        engineJob?.cancel()
        engine?.close()
        engine = null
        _state.value = _state.value.copy(
            config = _state.value.config.copy(
                mode = mode,
                humanColor = humanColor,
                engine = settings,
            ),
            engineThinking = false,
        )
        autoSave()
        maybeEngineMove()
        updateIndicators()
    }

    fun resign() {
        val s = _state.value
        val loser = if (s.config.mode == GameMode.HUMAN_VS_ENGINE) {
            s.config.humanColor
        } else {
            game.position.sideToMove
        }
        game.endByResignation(loser)
        publish()
        autoSave()
    }

    fun flipBoard() {
        _state.value = _state.value.copy(boardFlipped = !_state.value.boardFlipped)
    }

    fun exportPgn(): String {
        val date = SimpleDateFormat("yyyy.MM.dd", Locale.US).format(Date())
        val s = _state.value
        val (white, black) = when {
            s.config.mode != GameMode.HUMAN_VS_ENGINE -> "?" to "?"
            s.config.humanColor == Piece.WHITE -> "?" to engineName()
            else -> engineName() to "?"
        }
        return Pgn.export(game, mapOf("Date" to date, "White" to white, "Black" to black))
    }

    fun importPgn(pgn: String): Boolean {
        val imported = Pgn.import(pgn) ?: return false
        engineJob?.cancel()
        tree = GameTree(imported.startPosition)
        imported.moves.forEach { tree.play(it) }
        syncFromTree()
        _state.value = UiState(
            position = game.position,
            sanMoves = game.sanMoves,
            lastMove = game.moves.lastOrNull(),
            graphNodes = buildGraph(),
            currentNodeId = tree.current.id,
            result = game.result(),
            config = GameConfig(mode = GameMode.HUMAN_VS_HUMAN),
        )
        autoSave()
        return true
    }

    fun currentFen(): String = game.position.fen()

    private fun engineName(): String {
        val elo = _state.value.config.engine.estimatedElo()
        return if (_state.value.usingStockfish) "Stockfish (~$elo)" else "Beépített motor (~$elo)"
    }

    private fun publish() {
        _state.value = _state.value.copy(
            position = game.position,
            sanMoves = game.sanMoves.toList(),
            selectedSquare = null,
            legalTargets = emptySet(),
            lastMove = game.moves.lastOrNull(),
            undoneMove = null,
            hintArrows = emptyList(),
            threatArrows = emptyList(),
            graphNodes = buildGraph(),
            currentNodeId = tree.current.id,
            result = game.result(),
        )
        updateIndicators()
    }

    // --- Sakkóra ---

    private fun startClockIfNeeded() {
        clockJob?.cancel()
        if (_state.value.config.timeControl == null) return
        clockJob = viewModelScope.launch {
            while (true) {
                delay(100)
                val s = _state.value
                // Analízis közben az óra áll.
                if (s.result.isOver || game.moves.isEmpty() || s.analysisMode) continue
                val side = game.position.sideToMove
                val remaining = (if (side == Piece.WHITE) s.whiteMs else s.blackMs) ?: continue
                val updated = (remaining - 100).coerceAtLeast(0)
                _state.value = if (side == Piece.WHITE) {
                    s.copy(whiteMs = updated)
                } else {
                    s.copy(blackMs = updated)
                }
                if (updated == 0L) {
                    game.endByTimeout(side)
                    publish()
                    autoSave()
                }
            }
        }
    }

    private fun addIncrement(justMoved: Int) {
        val tc = _state.value.config.timeControl ?: return
        val s = _state.value
        _state.value = if (justMoved == Piece.WHITE) {
            s.copy(whiteMs = s.whiteMs?.plus(tc.incrementMs))
        } else {
            s.copy(blackMs = s.blackMs?.plus(tc.incrementMs))
        }
    }

    // --- Automatikus mentés: a játszma ÉS a konfiguráció is megőrződik ---

    fun autoSave() {
        val s = _state.value
        prefs.edit()
            .putString(KEY_PGN, exportPgn())
            .putBoolean(KEY_VS_ENGINE, s.config.mode == GameMode.HUMAN_VS_ENGINE)
            .putInt(KEY_COLOR, s.config.humanColor)
            .putInt(KEY_ELO, s.config.engine.elo)
            .putInt(KEY_SKILL, s.config.engine.skill)
            .putInt(KEY_DEPTH, s.config.engine.depth)
            .putInt(KEY_TIME, s.config.engine.moveTimeMs)
            .putString(KEY_TC, s.config.timeControl?.name)
            .putLong(KEY_WHITE_MS, s.whiteMs ?: -1)
            .putLong(KEY_BLACK_MS, s.blackMs ?: -1)
            .apply()
    }

    private fun restoreAutoSave() {
        val pgn = prefs.getString(KEY_PGN, null) ?: return
        val saved = Pgn.import(pgn) ?: return
        tree = GameTree(saved.startPosition)
        saved.moves.forEach { tree.play(it) }
        syncFromTree()
        val tc = TimeControl.PRESETS.firstOrNull { it.name == prefs.getString(KEY_TC, null) }
        val config = GameConfig(
            mode = if (prefs.getBoolean(KEY_VS_ENGINE, false)) {
                GameMode.HUMAN_VS_ENGINE
            } else {
                GameMode.HUMAN_VS_HUMAN
            },
            humanColor = prefs.getInt(KEY_COLOR, Piece.WHITE),
            engine = EngineSettings(
                elo = prefs.getInt(KEY_ELO, EngineSettings.ELO_MAX),
                skill = prefs.getInt(KEY_SKILL, EngineSettings.SKILL_MAX),
                depth = prefs.getInt(KEY_DEPTH, EngineSettings.DEPTH_MAX),
                moveTimeMs = prefs.getInt(KEY_TIME, 1000),
            ),
            timeControl = tc,
        )
        _state.value = UiState(
            position = game.position,
            sanMoves = game.sanMoves,
            lastMove = game.moves.lastOrNull(),
            graphNodes = buildGraph(),
            currentNodeId = tree.current.id,
            result = game.result(),
            config = config,
            whiteMs = prefs.getLong(KEY_WHITE_MS, -1).takeIf { it >= 0 } ?: tc?.initialMs,
            blackMs = prefs.getLong(KEY_BLACK_MS, -1).takeIf { it >= 0 } ?: tc?.initialMs,
            boardFlipped = config.mode == GameMode.HUMAN_VS_ENGINE &&
                config.humanColor == Piece.BLACK,
        )
    }

    override fun onCleared() {
        engine?.close()
        hintAnalysisEngine?.close()
        threatAnalysisEngine?.close()
    }

    private companion object {
        const val KEY_PGN = "autosave_pgn"
        const val KEY_VS_ENGINE = "cfg_vs_engine"
        const val KEY_COLOR = "cfg_color"
        const val KEY_ELO = "cfg_elo"
        const val KEY_SKILL = "cfg_skill"
        const val KEY_DEPTH = "cfg_depth"
        const val KEY_TIME = "cfg_time"
        const val KEY_TC = "cfg_tc"
        const val KEY_WHITE_MS = "cfg_white_ms"
        const val KEY_BLACK_MS = "cfg_black_ms"
        const val KEY_HINTS = "cfg_hints"
        const val KEY_THREATS = "cfg_threats"
        const val KEY_ANALYSIS = "cfg_analysis"
        const val HINT_COUNT = 4
        const val THREAT_COUNT = 3
        const val ANALYSE_TOTAL_MS = 5000
        const val ANALYSE_UPDATE_MS = 500L

        /**
         * A tábla-lokalizáció minimális megbízhatósága (sakktábla-korreláció).
         * Ez alatt nem találtunk tiszta táblát: a mérésekben a jó táblák 0,39–0,52
         * körül vannak, a „nincs tábla" esetek 0,1 alatt.
         */
        const val BOARD_CONF_MIN = 0.25f
    }
}
