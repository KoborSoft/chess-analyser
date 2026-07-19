package com.koborsoft.chessanalyser.engine

import android.content.Context
import com.koborsoft.chessanalyser.core.Game
import com.koborsoft.chessanalyser.core.Move
import com.koborsoft.chessanalyser.core.San
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * Stockfish illesztés UCI protokollon keresztül.
 *
 * A bináris "libstockfish.so" néven az app/src/main/jniLibs/<abi>/ mappából
 * kerül a csomagba; a rendszer a natív könyvtárak közé telepíti, ahonnan
 * futtatható. (Stockfish: GPLv3 — lásd a licencképernyőt.)
 */
class StockfishEngine private constructor(
    binaryPath: String,
    private val settings: EngineSettings,
) : ChessEngine {

    private val process: Process = ProcessBuilder(binaryPath).redirectErrorStream(true).start()
    private val writer = OutputStreamWriter(process.outputStream)
    private val reader = BufferedReader(InputStreamReader(process.inputStream))

    /** Egyszerre csak egy parancs-folyam futhat a motoron. */
    private val mutex = Mutex()

    init {
        send("uci")
        waitFor("uciok")
        if (settings.eloLimited) {
            val elo = settings.elo.coerceIn(EngineSettings.ELO_MIN, EngineSettings.ELO_MAX)
            send("setoption name UCI_LimitStrength value true")
            send("setoption name UCI_Elo value $elo")
        }
        if (settings.skill < EngineSettings.SKILL_MAX) {
            val skill = settings.skill.coerceAtLeast(EngineSettings.SKILL_MIN)
            send("setoption name Skill Level value $skill")
        }
        send("isready")
        waitFor("readyok")
    }

    /**
     * Folyamatos MultiPV elemzés: [totalMs] ideig számol, és [updateMs]
     * időközönként meghívja az [onUpdate]-et az addigi legjobb [multiPv]
     * lépéssel (pontszám centigyalogban, a lépő fél szemszögéből).
     * A hívó korutinjának megszakításakor leállítja a keresést.
     */
    suspend fun analyseStream(
        game: Game,
        multiPv: Int,
        totalMs: Int,
        updateMs: Long,
        onUpdate: (List<ScoredMove>) -> Unit,
    ) = withContext(Dispatchers.IO) {
        mutex.withLock { analyseStreamLocked(game, multiPv, totalMs, updateMs, onUpdate) }
    }

    private fun kotlinx.coroutines.CoroutineScope.analyseStreamLocked(
        game: Game,
        multiPv: Int,
        totalMs: Int,
        updateMs: Long,
        onUpdate: (List<ScoredMove>) -> Unit,
    ) {
        send("setoption name MultiPV value $multiPv")
        val movesPart =
            if (game.moves.isEmpty()) ""
            else " moves " + game.moves.joinToString(" ") { it.toString() }
        send("position fen ${game.startPosition.fen()}$movesPart")
        send("go movetime $totalMs")

        val best = HashMap<Int, Pair<String, Int>>()
        var lastPush = System.currentTimeMillis()
        var stopSent = false
        try {
            while (true) {
                val line = reader.readLine() ?: break
                if (line.startsWith("bestmove")) break

                if (!isActive && !stopSent) {
                    send("stop")
                    stopSent = true
                }

                if (line.startsWith("info") && " pv " in line) {
                    val tokens = line.split(" ")
                    val pvIndex = tokens.indexOf("pv")
                    val move = tokens.getOrNull(pvIndex + 1)
                    val mpv = tokens.indexOf("multipv")
                        .takeIf { it >= 0 }?.let { tokens[it + 1].toIntOrNull() } ?: 1
                    val scoreIndex = tokens.indexOf("score")
                    val score = if (scoreIndex >= 0) {
                        when (tokens.getOrNull(scoreIndex + 1)) {
                            "cp" -> tokens[scoreIndex + 2].toIntOrNull()
                            "mate" -> tokens[scoreIndex + 2].toIntOrNull()?.let { n ->
                                if (n > 0) 100_000 - n else -100_000 - n
                            }
                            else -> null
                        }
                    } else {
                        null
                    }
                    if (move != null && score != null) best[mpv] = move to score
                }

                if (isActive && System.currentTimeMillis() - lastPush >= updateMs) {
                    lastPush = System.currentTimeMillis()
                    onUpdate(snapshot(best, game))
                }
            }
        } finally {
            send("setoption name MultiPV value 1")
        }
        if (isActive) onUpdate(snapshot(best, game))
    }

    private fun snapshot(best: Map<Int, Pair<String, Int>>, game: Game): List<ScoredMove> =
        best.values
            .mapNotNull { (uci, score) ->
                San.fromUci(game.position, uci)?.let { ScoredMove(it, score) }
            }
            .sortedByDescending { it.score }

    /**
     * A Stockfish statikus állás-értékelése ("eval" parancs), centigyalogban,
     * VILÁGOS szemszögéből. Sakkban álló királynál (vagy hibánál) null.
     */
    suspend fun staticEval(game: Game): Int? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val movesPart =
                if (game.moves.isEmpty()) ""
                else " moves " + game.moves.joinToString(" ") { it.toString() }
            send("position fen ${game.startPosition.fen()}$movesPart")
            send("eval")
            var result: Int? = null
            var lines = 0
            while (lines++ < 300) {
                val line = reader.readLine() ?: break
                if ("Final evaluation" in line) {
                    result = Regex("""[-+]\d+\.\d+""").find(line)
                        ?.value?.toFloatOrNull()?.times(100)?.toInt()
                    break
                }
            }
            result
        }
    }

    override suspend fun bestMove(game: Game): Move? = withContext(Dispatchers.IO) {
        val movesPart =
            if (game.moves.isEmpty()) ""
            else " moves " + game.moves.joinToString(" ") { it.toString() }
        send("position fen ${game.startPosition.fen()}$movesPart")
        // Idő- és mélységkorlát együtt: a keresés annál áll meg, amelyik előbb teljesül.
        val go = buildString {
            append("go movetime ${settings.moveTimeMs}")
            if (settings.depthLimited) append(" depth ${settings.depth}")
        }
        send(go)
        val line = waitFor("bestmove") ?: return@withContext null
        val uci = line.split(" ").getOrNull(1) ?: return@withContext null
        San.fromUci(game.position, uci)
    }

    private fun send(cmd: String) {
        writer.write(cmd + "\n")
        writer.flush()
    }

    private fun waitFor(prefix: String): String? {
        while (true) {
            val line = reader.readLine() ?: return null
            if (line.startsWith(prefix)) return line
        }
    }

    override fun close() {
        runCatching { send("quit") }
        runCatching { process.destroy() }
    }

    companion object {
        fun binaryPath(context: Context): String? =
            File(context.applicationInfo.nativeLibraryDir, "libstockfish.so")
                .takeIf { it.canExecute() }?.absolutePath

        /** null, ha a Stockfish bináris nincs a csomagban vagy nem indul el. */
        fun createIfAvailable(context: Context, settings: EngineSettings): StockfishEngine? =
            binaryPath(context)?.let {
                runCatching { StockfishEngine(it, settings) }.getOrNull()
            }
    }
}
