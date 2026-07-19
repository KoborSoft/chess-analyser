package com.koborsoft.chessanalyser.core

import kotlin.math.abs

/**
 * Egy sakkállás minden adata. Megváltoztathatatlan: az [applyMove] új példányt ad
 * vissza, így a lépéstörténet és a visszavonás egyszerűen kezelhető.
 *
 * Sáncjogok bitjei a [castling] mezőben:
 * 1 = világos rövid, 2 = világos hosszú, 4 = sötét rövid, 8 = sötét hosszú.
 */
class Position(
    val board: IntArray,
    val sideToMove: Int,
    val castling: Int,
    /** En passant célmező indexe, vagy -1 ha nincs. */
    val epSquare: Int,
    val halfmoveClock: Int,
    val fullmoveNumber: Int,
) {
    fun pieceAt(sq: Int): Int = board[sq]

    fun kingSquare(color: Int): Int {
        val king = Piece.of(color, Piece.KING)
        for (sq in 0..63) if (board[sq] == king) return sq
        throw IllegalStateException("Nincs király a táblán: $color")
    }

    fun inCheck(color: Int = sideToMove): Boolean =
        isSquareAttacked(kingSquare(color), -color)

    /** Támadja-e a [by] színű fél az [sq] mezőt? */
    fun isSquareAttacked(sq: Int, by: Int): Boolean {
        val r = Square.rank(sq)
        val f = Square.file(sq)

        // Gyalog: a by színű gyalog az sq-t átlósan, előrefelé üti.
        val pr = r - by
        if (pr in 0..7) {
            val pawn = Piece.of(by, Piece.PAWN)
            if (f > 0 && board[Square.of(f - 1, pr)] == pawn) return true
            if (f < 7 && board[Square.of(f + 1, pr)] == pawn) return true
        }

        for ((dr, df) in KNIGHT_DELTAS) {
            val nr = r + dr; val nf = f + df
            if (nr in 0..7 && nf in 0..7 &&
                board[Square.of(nf, nr)] == Piece.of(by, Piece.KNIGHT)
            ) return true
        }

        for ((dr, df) in KING_DELTAS) {
            val nr = r + dr; val nf = f + df
            if (nr in 0..7 && nf in 0..7 &&
                board[Square.of(nf, nr)] == Piece.of(by, Piece.KING)
            ) return true
        }

        if (attackedAlongRays(r, f, ROOK_DIRS, by, Piece.ROOK)) return true
        if (attackedAlongRays(r, f, BISHOP_DIRS, by, Piece.BISHOP)) return true
        return false
    }

    private fun attackedAlongRays(
        r: Int, f: Int, dirs: Array<IntArray>, by: Int, sliderType: Int,
    ): Boolean {
        for ((dr, df) in dirs.map { it[0] to it[1] }) {
            var nr = r + dr; var nf = f + df
            while (nr in 0..7 && nf in 0..7) {
                val piece = board[Square.of(nf, nr)]
                if (piece != Piece.NONE) {
                    if (Piece.colorOf(piece) == by &&
                        (Piece.typeOf(piece) == sliderType || Piece.typeOf(piece) == Piece.QUEEN)
                    ) return true
                    break
                }
                nr += dr; nf += df
            }
        }
        return false
    }

    /** A lépés végrehajtása; az eredmény az ellenfél lépésére váró új állás. */
    fun applyMove(m: Move): Position {
        val b = board.copyOf()
        val piece = b[m.from]
        val color = Piece.colorOf(piece)
        val isPawn = Piece.typeOf(piece) == Piece.PAWN
        val isCapture = b[m.to] != Piece.NONE || m.isEnPassant

        b[m.to] = if (m.promotion != 0) Piece.of(color, m.promotion) else piece
        b[m.from] = Piece.NONE

        if (m.isEnPassant) b[m.to - 8 * color] = Piece.NONE

        if (m.isCastling) {
            if (m.to > m.from) { // rövid sánc
                b[m.from + 1] = b[m.from + 3]
                b[m.from + 3] = Piece.NONE
            } else { // hosszú sánc
                b[m.from - 1] = b[m.from - 4]
                b[m.from - 4] = Piece.NONE
            }
        }

        val newEp = if (isPawn && abs(m.to - m.from) == 16) m.from + 8 * color else -1
        val newCastling = castling and castlingMask(m.from) and castlingMask(m.to)
        val newClock = if (isPawn || isCapture) 0 else halfmoveClock + 1
        val newFullmove = if (color == Piece.BLACK) fullmoveNumber + 1 else fullmoveNumber

        return Position(b, -sideToMove, newCastling, newEp, newClock, newFullmove)
    }

    /**
     * Az ismétlődés-vizsgálathoz használt kulcs: a FEN óramezők nélkül.
     * Két állás akkor azonos, ha a bábuk, a lépő fél, a sáncjogok és az
     * en passant lehetőség is azonos.
     */
    fun repetitionKey(): String = fen().split(" ").take(4).joinToString(" ")

    fun fen(): String {
        val sb = StringBuilder()
        for (r in 7 downTo 0) {
            var empty = 0
            for (f in 0..7) {
                val piece = board[Square.of(f, r)]
                if (piece == Piece.NONE) {
                    empty++
                } else {
                    if (empty > 0) { sb.append(empty); empty = 0 }
                    val c = Piece.letter(Piece.typeOf(piece))
                    sb.append(if (Piece.colorOf(piece) == Piece.WHITE) c else c.lowercaseChar())
                }
            }
            if (empty > 0) sb.append(empty)
            if (r > 0) sb.append('/')
        }
        sb.append(' ').append(if (sideToMove == Piece.WHITE) 'w' else 'b').append(' ')
        if (castling == 0) {
            sb.append('-')
        } else {
            if (castling and 1 != 0) sb.append('K')
            if (castling and 2 != 0) sb.append('Q')
            if (castling and 4 != 0) sb.append('k')
            if (castling and 8 != 0) sb.append('q')
        }
        sb.append(' ').append(if (epSquare >= 0) Square.name(epSquare) else "-")
        sb.append(' ').append(halfmoveClock).append(' ').append(fullmoveNumber)
        return sb.toString()
    }

    companion object {
        const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

        val KNIGHT_DELTAS = arrayOf(
            intArrayOf(-2, -1), intArrayOf(-2, 1), intArrayOf(-1, -2), intArrayOf(-1, 2),
            intArrayOf(1, -2), intArrayOf(1, 2), intArrayOf(2, -1), intArrayOf(2, 1),
        ).map { it[0] to it[1] }

        val KING_DELTAS = arrayOf(
            intArrayOf(-1, -1), intArrayOf(-1, 0), intArrayOf(-1, 1), intArrayOf(0, -1),
            intArrayOf(0, 1), intArrayOf(1, -1), intArrayOf(1, 0), intArrayOf(1, 1),
        ).map { it[0] to it[1] }

        val ROOK_DIRS = arrayOf(
            intArrayOf(-1, 0), intArrayOf(1, 0), intArrayOf(0, -1), intArrayOf(0, 1),
        )
        val BISHOP_DIRS = arrayOf(
            intArrayOf(-1, -1), intArrayOf(-1, 1), intArrayOf(1, -1), intArrayOf(1, 1),
        )

        fun initial(): Position = fromFen(START_FEN)

        fun fromFen(fen: String): Position {
            val parts = fen.trim().split(Regex("\\s+"))
            require(parts.size >= 4) { "Érvénytelen FEN: $fen" }

            val board = IntArray(64)
            val ranks = parts[0].split("/")
            require(ranks.size == 8) { "Érvénytelen FEN-tábla: ${parts[0]}" }
            for ((i, rankStr) in ranks.withIndex()) {
                val r = 7 - i
                var f = 0
                for (c in rankStr) {
                    if (c.isDigit()) {
                        f += c - '0'
                    } else {
                        require(f < 8) { "Érvénytelen FEN-sor: $rankStr" }
                        val type = Piece.typeFromLetter(c)
                        require(type != Piece.NONE) { "Ismeretlen bábu: $c" }
                        val color = if (c.isUpperCase()) Piece.WHITE else Piece.BLACK
                        board[Square.of(f, r)] = Piece.of(color, type)
                        f++
                    }
                }
                require(f == 8) { "Érvénytelen FEN-sor: $rankStr" }
            }

            val side = if (parts[1] == "w") Piece.WHITE else Piece.BLACK
            var castling = 0
            for (c in parts[2]) when (c) {
                'K' -> castling = castling or 1
                'Q' -> castling = castling or 2
                'k' -> castling = castling or 4
                'q' -> castling = castling or 8
            }
            val ep = if (parts[3] == "-") -1 else Square.parse(parts[3])
            val clock = parts.getOrNull(4)?.toIntOrNull() ?: 0
            val fullmove = parts.getOrNull(5)?.toIntOrNull() ?: 1

            return Position(board, side, castling, ep, clock, fullmove)
        }

        /** Elveszik a sáncjog, ha az adott sarok- vagy királymező érintett. */
        private fun castlingMask(sq: Int): Int = when (sq) {
            0 -> 0b1101   // a1
            4 -> 0b1100   // e1
            7 -> 0b1110   // h1
            56 -> 0b0111  // a8
            60 -> 0b0011  // e8
            63 -> 0b1011  // h8
            else -> 0b1111
        }
    }
}
