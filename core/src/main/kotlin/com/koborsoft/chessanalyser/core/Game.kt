package com.koborsoft.chessanalyser.core

enum class ResultType { ONGOING, CHECKMATE, STALEMATE, DRAW_FIFTY, DRAW_REPETITION, DRAW_MATERIAL, TIMEOUT, RESIGNATION }

/** A játszma kimenetele. [winner] a győztes színe, döntetlennél/folyamatban 0. */
data class GameResult(val type: ResultType, val winner: Int = 0) {
    val isOver: Boolean get() = type != ResultType.ONGOING
    companion object {
        val ONGOING = GameResult(ResultType.ONGOING)
    }
}

/**
 * Egy teljes játszma: álláslista, lépéstörténet, visszavonás, játékvég-szabályok.
 * Az állások megváltoztathatatlanok, így az undo csak a lista rövidítése.
 */
class Game(val startPosition: Position = Position.initial()) {

    private val _positions = mutableListOf(startPosition)
    private val _moves = mutableListOf<Move>()
    private val _sanMoves = mutableListOf<String>()

    /** Külső ok miatti játékvég (időtúllépés, feladás), egyébként null. */
    private var externalResult: GameResult? = null

    val position: Position get() = _positions.last()
    val moves: List<Move> get() = _moves
    val sanMoves: List<String> get() = _sanMoves
    val positions: List<Position> get() = _positions

    fun legalMoves(): List<Move> = MoveGenerator.legalMoves(position)

    /** Lépés végrehajtása; false, ha a lépés nem szabályos vagy a játszma véget ért. */
    fun play(move: Move): Boolean {
        if (result().isOver) return false
        val legal = legalMoves().firstOrNull {
            it.from == move.from && it.to == move.to && it.promotion == move.promotion
        } ?: return false
        _sanMoves.add(San.toSan(position, legal))
        _moves.add(legal)
        _positions.add(position.applyMove(legal))
        return true
    }

    fun playSan(san: String): Boolean {
        val move = San.fromSan(position, san) ?: return false
        return play(move)
    }

    /** Utolsó lépés visszavonása; false, ha nincs mit visszavonni. */
    fun undo(): Boolean {
        if (_moves.isEmpty()) return false
        externalResult = null
        _moves.removeAt(_moves.lastIndex)
        _sanMoves.removeAt(_sanMoves.lastIndex)
        _positions.removeAt(_positions.lastIndex)
        return true
    }

    fun endByTimeout(loser: Int) {
        if (!result().isOver) externalResult = GameResult(ResultType.TIMEOUT, -loser)
    }

    fun endByResignation(loser: Int) {
        if (!result().isOver) externalResult = GameResult(ResultType.RESIGNATION, -loser)
    }

    fun result(): GameResult {
        externalResult?.let { return it }
        val pos = position
        if (MoveGenerator.legalMoves(pos).isEmpty()) {
            return if (pos.inCheck()) {
                GameResult(ResultType.CHECKMATE, -pos.sideToMove)
            } else {
                GameResult(ResultType.STALEMATE)
            }
        }
        if (pos.halfmoveClock >= 100) return GameResult(ResultType.DRAW_FIFTY)
        if (repetitionCount(pos.repetitionKey()) >= 3) return GameResult(ResultType.DRAW_REPETITION)
        if (isInsufficientMaterial(pos)) return GameResult(ResultType.DRAW_MATERIAL)
        return GameResult.ONGOING
    }

    private fun repetitionCount(key: String): Int =
        _positions.count { it.repetitionKey() == key }

    companion object {
        /**
         * Anyaghiány miatti döntetlen: K–K, K+F–K, K+H–K, valamint K+F–K+F
         * azonos színű mezőn álló futókkal.
         */
        fun isInsufficientMaterial(pos: Position): Boolean {
            val minorSquares = mutableListOf<Int>()
            var knights = 0
            var bishops = 0
            for (sq in 0..63) {
                when (Piece.typeOf(pos.board[sq])) {
                    Piece.NONE, Piece.KING -> {}
                    Piece.KNIGHT -> { knights++; minorSquares.add(sq) }
                    Piece.BISHOP -> { bishops++; minorSquares.add(sq) }
                    else -> return false
                }
            }
            return when {
                minorSquares.size <= 1 -> true
                knights == 0 && bishops == 2 -> {
                    val colorOf = { sq: Int -> (Square.rank(sq) + Square.file(sq)) % 2 }
                    colorOf(minorSquares[0]) == colorOf(minorSquares[1])
                }
                else -> false
            }
        }
    }
}
