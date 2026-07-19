package com.koborsoft.chessanalyser.core

/**
 * Lépésgenerálás: előbb ál-legális (pszeudo) lépések, majd szűrés arra,
 * hogy a saját király nem maradhat sakkban.
 */
object MoveGenerator {

    fun legalMoves(pos: Position): List<Move> {
        val side = pos.sideToMove
        return pseudoLegalMoves(pos).filter { m ->
            val next = pos.applyMove(m)
            !next.isSquareAttacked(next.kingSquare(side), -side)
        }
    }

    fun legalMovesFrom(pos: Position, from: Int): List<Move> =
        legalMoves(pos).filter { it.from == from }

    fun pseudoLegalMoves(pos: Position): List<Move> {
        val moves = ArrayList<Move>(48)
        val side = pos.sideToMove
        for (sq in 0..63) {
            val piece = pos.board[sq]
            if (piece == Piece.NONE || Piece.colorOf(piece) != side) continue
            when (Piece.typeOf(piece)) {
                Piece.PAWN -> pawnMoves(pos, sq, side, moves)
                Piece.KNIGHT -> stepMoves(pos, sq, side, Position.KNIGHT_DELTAS, moves)
                Piece.KING -> {
                    stepMoves(pos, sq, side, Position.KING_DELTAS, moves)
                    castlingMoves(pos, sq, side, moves)
                }
                Piece.BISHOP -> slideMoves(pos, sq, side, Position.BISHOP_DIRS, moves)
                Piece.ROOK -> slideMoves(pos, sq, side, Position.ROOK_DIRS, moves)
                Piece.QUEEN -> {
                    slideMoves(pos, sq, side, Position.ROOK_DIRS, moves)
                    slideMoves(pos, sq, side, Position.BISHOP_DIRS, moves)
                }
            }
        }
        return moves
    }

    private fun pawnMoves(pos: Position, sq: Int, side: Int, out: MutableList<Move>) {
        val r = Square.rank(sq)
        val f = Square.file(sq)
        val startRank = if (side == Piece.WHITE) 1 else 6
        val promoRank = if (side == Piece.WHITE) 7 else 0

        // Előrelépés
        val oneAhead = sq + 8 * side
        if (pos.board[oneAhead] == Piece.NONE) {
            addPawnMove(sq, oneAhead, promoRank, out)
            val twoAhead = sq + 16 * side
            if (r == startRank && pos.board[twoAhead] == Piece.NONE) {
                out.add(Move(sq, twoAhead))
            }
        }

        // Ütések átlósan, illetve en passant
        for (df in intArrayOf(-1, 1)) {
            val nf = f + df
            if (nf !in 0..7) continue
            val target = Square.of(nf, r + side)
            val captured = pos.board[target]
            if (captured != Piece.NONE && Piece.colorOf(captured) == -side) {
                addPawnMove(sq, target, promoRank, out)
            } else if (target == pos.epSquare) {
                out.add(Move(sq, target, isEnPassant = true))
            }
        }
    }

    private fun addPawnMove(from: Int, to: Int, promoRank: Int, out: MutableList<Move>) {
        if (Square.rank(to) == promoRank) {
            for (type in intArrayOf(Piece.QUEEN, Piece.ROOK, Piece.BISHOP, Piece.KNIGHT)) {
                out.add(Move(from, to, promotion = type))
            }
        } else {
            out.add(Move(from, to))
        }
    }

    private fun stepMoves(
        pos: Position, sq: Int, side: Int,
        deltas: List<Pair<Int, Int>>, out: MutableList<Move>,
    ) {
        val r = Square.rank(sq)
        val f = Square.file(sq)
        for ((dr, df) in deltas) {
            val nr = r + dr; val nf = f + df
            if (nr !in 0..7 || nf !in 0..7) continue
            val target = Square.of(nf, nr)
            if (Piece.colorOf(pos.board[target]) != side) out.add(Move(sq, target))
        }
    }

    private fun slideMoves(
        pos: Position, sq: Int, side: Int,
        dirs: Array<IntArray>, out: MutableList<Move>,
    ) {
        val r = Square.rank(sq)
        val f = Square.file(sq)
        for (dir in dirs) {
            var nr = r + dir[0]; var nf = f + dir[1]
            while (nr in 0..7 && nf in 0..7) {
                val target = Square.of(nf, nr)
                val piece = pos.board[target]
                if (piece == Piece.NONE) {
                    out.add(Move(sq, target))
                } else {
                    if (Piece.colorOf(piece) == -side) out.add(Move(sq, target))
                    break
                }
                nr += dir[0]; nf += dir[1]
            }
        }
    }

    private fun castlingMoves(pos: Position, kingSq: Int, side: Int, out: MutableList<Move>) {
        val home = if (side == Piece.WHITE) 4 else 60
        if (kingSq != home) return
        val kingBit = if (side == Piece.WHITE) 1 else 4
        val queenBit = if (side == Piece.WHITE) 2 else 8

        // Sánc közben a király nem állhat sakkban, és nem haladhat át támadott mezőn.
        if (pos.castling and kingBit != 0 &&
            pos.board[home + 1] == Piece.NONE &&
            pos.board[home + 2] == Piece.NONE &&
            !pos.isSquareAttacked(home, -side) &&
            !pos.isSquareAttacked(home + 1, -side) &&
            !pos.isSquareAttacked(home + 2, -side)
        ) {
            out.add(Move(home, home + 2, isCastling = true))
        }

        if (pos.castling and queenBit != 0 &&
            pos.board[home - 1] == Piece.NONE &&
            pos.board[home - 2] == Piece.NONE &&
            pos.board[home - 3] == Piece.NONE &&
            !pos.isSquareAttacked(home, -side) &&
            !pos.isSquareAttacked(home - 1, -side) &&
            !pos.isSquareAttacked(home - 2, -side)
        ) {
            out.add(Move(home, home - 2, isCastling = true))
        }
    }

    /** Perft: az adott mélységig elérhető állások száma (a lépésgenerálás tesztje). */
    fun perft(pos: Position, depth: Int): Long {
        if (depth == 0) return 1
        var nodes = 0L
        for (m in legalMoves(pos)) {
            nodes += if (depth == 1) 1 else perft(pos.applyMove(m), depth - 1)
        }
        return nodes
    }
}
