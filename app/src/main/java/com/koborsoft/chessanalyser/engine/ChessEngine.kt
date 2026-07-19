package com.koborsoft.chessanalyser.engine

import com.koborsoft.chessanalyser.core.Game
import com.koborsoft.chessanalyser.core.Move
import kotlin.math.ln
import kotlin.math.roundToInt

/** Gépi ellenfél. A [bestMove] hosszan futhat, ezért felfüggeszthető. */
interface ChessEngine {
    suspend fun bestMove(game: Game): Move?
    fun close() {}
}

/** Pontozott lépés a javaslatokhoz; a [score] centigyalogban értendő. */
data class ScoredMove(val move: Move, val score: Int)

/**
 * A motor erejét meghatározó paraméterek — a nehézséget közvetlenül ezek
 * állításával adja meg a játékos.
 *
 * - [elo]: UCI_Elo korlát (1320–3190). A maximumra állítva nincs korlátozás.
 * - [skill]: Stockfish Skill Level (0–20). 20 = teljes erő; az Elo-korlát
 *   alsó határa alatti gyengítést ez adja.
 * - [depth]: ha megadva, a motor legfeljebb ilyen mélyen számol.
 * - [moveTimeMs]: ha nincs mélység megadva, lépésenként ennyi ideig gondolkodik.
 */
data class EngineSettings(
    val elo: Int = ELO_MAX,
    val skill: Int = SKILL_MAX,
    /** Mélység-korlát; a [DEPTH_MAX] értéken nincs korlátozás. */
    val depth: Int = DEPTH_MAX,
    val moveTimeMs: Int = 1000,
) {
    /** A maximumra állított csúszka = nincs korlátozás. */
    val eloLimited: Boolean get() = elo < ELO_MAX
    val depthLimited: Boolean get() = depth < DEPTH_MAX

    /**
     * Durva becslés a beállítások eredő játékerejére.
     *
     * Alap: az Elo-korlát és a Skill Level szerinti erő közül a kisebb
     * (a Skill ~ 850 + 115·szint tapasztalati képlettel). Ezt módosítja a
     * gondolkodási keret: az idő- és a mélységkorlát hatása közül a
     * szigorúbb érvényesül (a keresés annál áll meg, amelyik előbb
     * teljesül) — 1 mp-hez képest az idő minden feleződése kb. −60 Elo,
     * a 20-as mélységhez képest szintenként kb. −45 Elo.
     * Tájékoztató jellegű, nem mérés!
     */
    fun estimatedElo(): Int {
        val skillElo = 850 + skill * 115
        val base = minOf(if (eloLimited) elo else Int.MAX_VALUE, skillElo)
        val halvings = ln(moveTimeMs.coerceAtLeast(50) / 1000.0) / ln(2.0)
        val timeAdjust = (60 * halvings).roundToInt().coerceIn(-300, 60)
        val depthAdjust = if (depthLimited) (depth.coerceAtMost(20) - 20) * 45 else 0
        return (base + minOf(timeAdjust, depthAdjust)).coerceIn(600, 3400)
    }

    companion object {
        const val ELO_MIN = 1320
        const val ELO_MAX = 3190
        const val SKILL_MIN = 0
        const val SKILL_MAX = 20
        const val DEPTH_MIN = 1
        const val DEPTH_MAX = 30
    }
}
