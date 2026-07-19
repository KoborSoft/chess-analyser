package com.koborsoft.chessanalyser.engine

import com.koborsoft.chessanalyser.core.Game
import com.koborsoft.chessanalyser.core.Move
import com.koborsoft.chessanalyser.core.MoveGenerator
import com.koborsoft.chessanalyser.core.Piece
import com.koborsoft.chessanalyser.core.Position
import com.koborsoft.chessanalyser.core.Square
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * Beépített tartalék motor arra az esetre, ha a Stockfish bináris nincs
 * a csomagban: negamax alfa-béta vágással, anyag + pozíciós értékeléssel.
 * Ereje messze elmarad a Stockfishtől, de azonnal játszható ellenfél.
 */
class BuiltInEngine(settings: EngineSettings) : ChessEngine {

    /** A beállításokból becsült erő leképezése keresési mélységre. */
    private val maxDepth: Int = when {
        settings.estimatedElo() < 1000 -> 1
        settings.estimatedElo() < 1400 -> 2
        settings.estimatedElo() < 1900 -> 3
        settings.estimatedElo() < 2400 -> 4
        else -> 5
    }
    private val randomPercent: Int = if (settings.estimatedElo() < 1200) 20 else 0

    /** A legjobb [count] lépés pontszámmal, jóság szerint csökkenő sorrendben. */
    suspend fun scoredMoves(game: Game, count: Int): List<ScoredMove> =
        withContext(Dispatchers.Default) {
            val pos = game.position
            MoveGenerator.legalMoves(pos)
                .map { m ->
                    ScoredMove(
                        m,
                        -negamax(pos.applyMove(m), maxDepth - 1, Int.MIN_VALUE + 1, Int.MAX_VALUE),
                    )
                }
                .sortedByDescending { it.score }
                .take(count)
        }

    override suspend fun bestMove(game: Game): Move? = withContext(Dispatchers.Default) {
        val pos = game.position
        val moves = MoveGenerator.legalMoves(pos)
        if (moves.isEmpty()) return@withContext null

        // Alacsony szinten némi véletlenszerűség, hogy emberibb ellenfél legyen.
        if (Random.nextInt(100) < randomPercent) {
            return@withContext moves.random()
        }

        var best = moves.first()
        var alpha = Int.MIN_VALUE + 1
        for (m in moves.shuffled()) {
            val score = -negamax(pos.applyMove(m), maxDepth - 1, Int.MIN_VALUE + 1, -alpha)
            if (score > alpha) {
                alpha = score
                best = m
            }
        }
        best
    }

    private fun negamax(pos: Position, depth: Int, alpha: Int, beta: Int): Int {
        val moves = MoveGenerator.legalMoves(pos)
        if (moves.isEmpty()) {
            return if (pos.inCheck()) -MATE + (maxDepth - depth) else 0
        }
        if (pos.halfmoveClock >= 100) return 0
        if (depth <= 0) return evaluate(pos) * pos.sideToMove

        var a = alpha
        var best = Int.MIN_VALUE + 1
        for (m in moves) {
            val score = -negamax(pos.applyMove(m), depth - 1, -beta, -a)
            if (score > best) best = score
            if (best > a) a = best
            if (a >= beta) break
        }
        return best
    }

    /** Világos szemszögéből vett értékelés centigyalogban. */
    private fun evaluate(pos: Position): Int {
        var score = 0
        for (sq in 0..63) {
            val piece = pos.board[sq]
            if (piece == Piece.NONE) continue
            val color = Piece.colorOf(piece)
            val type = Piece.typeOf(piece)
            // Sötét bábunál a táblázatot függőlegesen tükrözzük.
            val idx = if (color == Piece.WHITE) sq else Square.of(Square.file(sq), 7 - Square.rank(sq))
            score += color * (PIECE_VALUE[type] + PST[type][idx])
        }
        return score
    }

    companion object {
        private const val MATE = 1_000_000

        private val PIECE_VALUE = intArrayOf(0, 100, 320, 330, 500, 900, 0)

        // Egyszerűsített pozíciós táblázatok (világos szemszögéből, a1 = 0. index).
        private val PAWN_PST = intArrayOf(
            0, 0, 0, 0, 0, 0, 0, 0,
            5, 10, 10, -20, -20, 10, 10, 5,
            5, -5, -10, 0, 0, -10, -5, 5,
            0, 0, 0, 20, 20, 0, 0, 0,
            5, 5, 10, 25, 25, 10, 5, 5,
            10, 10, 20, 30, 30, 20, 10, 10,
            50, 50, 50, 50, 50, 50, 50, 50,
            0, 0, 0, 0, 0, 0, 0, 0,
        )
        private val KNIGHT_PST = intArrayOf(
            -50, -40, -30, -30, -30, -30, -40, -50,
            -40, -20, 0, 5, 5, 0, -20, -40,
            -30, 5, 10, 15, 15, 10, 5, -30,
            -30, 0, 15, 20, 20, 15, 0, -30,
            -30, 5, 15, 20, 20, 15, 5, -30,
            -30, 0, 10, 15, 15, 10, 0, -30,
            -40, -20, 0, 0, 0, 0, -20, -40,
            -50, -40, -30, -30, -30, -30, -40, -50,
        )
        private val BISHOP_PST = intArrayOf(
            -20, -10, -10, -10, -10, -10, -10, -20,
            -10, 5, 0, 0, 0, 0, 5, -10,
            -10, 10, 10, 10, 10, 10, 10, -10,
            -10, 0, 10, 10, 10, 10, 0, -10,
            -10, 5, 5, 10, 10, 5, 5, -10,
            -10, 0, 5, 10, 10, 5, 0, -10,
            -10, 0, 0, 0, 0, 0, 0, -10,
            -20, -10, -10, -10, -10, -10, -10, -20,
        )
        private val ROOK_PST = intArrayOf(
            0, 0, 0, 5, 5, 0, 0, 0,
            -5, 0, 0, 0, 0, 0, 0, -5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            5, 10, 10, 10, 10, 10, 10, 5,
            0, 0, 0, 0, 0, 0, 0, 0,
        )
        private val QUEEN_PST = intArrayOf(
            -20, -10, -10, -5, -5, -10, -10, -20,
            -10, 0, 5, 0, 0, 0, 0, -10,
            -10, 5, 5, 5, 5, 5, 0, -10,
            0, 0, 5, 5, 5, 5, 0, -5,
            -5, 0, 5, 5, 5, 5, 0, -5,
            -10, 0, 5, 5, 5, 5, 0, -10,
            -10, 0, 0, 0, 0, 0, 0, -10,
            -20, -10, -10, -5, -5, -10, -10, -20,
        )
        private val KING_PST = intArrayOf(
            20, 30, 10, 0, 0, 10, 30, 20,
            20, 20, 0, 0, 0, 0, 20, 20,
            -10, -20, -20, -20, -20, -20, -20, -10,
            -20, -30, -30, -40, -40, -30, -30, -20,
            -30, -40, -40, -50, -50, -40, -40, -30,
            -30, -40, -40, -50, -50, -40, -40, -30,
            -30, -40, -40, -50, -50, -40, -40, -30,
            -30, -40, -40, -50, -50, -40, -40, -30,
        )

        private val PST = arrayOf(
            IntArray(64), PAWN_PST, KNIGHT_PST, BISHOP_PST, ROOK_PST, QUEEN_PST, KING_PST,
        )
    }
}
