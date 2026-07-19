package com.koborsoft.chessanalyser.core

import kotlin.math.abs

/**
 * Bábuk kódolása egész számként: pozitív = világos, negatív = sötét, 0 = üres.
 * A mezők indexelése: a1 = 0, b1 = 1, ... h8 = 63 (sor * 8 + oszlop).
 */
object Piece {
    const val NONE = 0
    const val PAWN = 1
    const val KNIGHT = 2
    const val BISHOP = 3
    const val ROOK = 4
    const val QUEEN = 5
    const val KING = 6

    const val WHITE = 1
    const val BLACK = -1

    fun of(color: Int, type: Int): Int = color * type
    fun typeOf(piece: Int): Int = abs(piece)
    fun colorOf(piece: Int): Int = piece.compareTo(0)

    /** Angol bábubetű (SAN/FEN): P, N, B, R, Q, K. */
    fun letter(type: Int): Char = "PNBRQK"[type - 1]

    fun typeFromLetter(c: Char): Int = when (c.uppercaseChar()) {
        'P' -> PAWN; 'N' -> KNIGHT; 'B' -> BISHOP
        'R' -> ROOK; 'Q' -> QUEEN; 'K' -> KING
        else -> NONE
    }
}

object Square {
    fun of(file: Int, rank: Int): Int = rank * 8 + file
    fun file(sq: Int): Int = sq % 8
    fun rank(sq: Int): Int = sq / 8
    fun name(sq: Int): String = "${'a' + file(sq)}${'1' + rank(sq)}"

    /** Mezőnév ("e4") indexszé; érvénytelen névre -1. */
    fun parse(s: String): Int {
        if (s.length != 2) return -1
        val f = s[0] - 'a'
        val r = s[1] - '1'
        return if (f in 0..7 && r in 0..7) of(f, r) else -1
    }
}

data class Move(
    val from: Int,
    val to: Int,
    /** Átváltozásnál az új bábu típusa (KNIGHT..QUEEN), egyébként 0. */
    val promotion: Int = 0,
    val isEnPassant: Boolean = false,
    val isCastling: Boolean = false,
) {
    /** UCI formátum, pl. "e2e4", "e7e8q". */
    override fun toString(): String =
        Square.name(from) + Square.name(to) +
            (if (promotion != 0) Piece.letter(promotion).lowercaseChar().toString() else "")
}
