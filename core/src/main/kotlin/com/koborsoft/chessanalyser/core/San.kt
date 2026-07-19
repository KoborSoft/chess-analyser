package com.koborsoft.chessanalyser.core

/** Algebrai jelölés (SAN) előállítása és beolvasása. */
object San {

    fun toSan(pos: Position, move: Move): String {
        val piece = pos.board[move.from]
        val type = Piece.typeOf(piece)
        val isCapture = pos.board[move.to] != Piece.NONE || move.isEnPassant

        val body = when {
            move.isCastling && move.to > move.from -> "O-O"
            move.isCastling -> "O-O-O"
            type == Piece.PAWN -> buildString {
                if (isCapture) append('a' + Square.file(move.from)).append('x')
                append(Square.name(move.to))
                if (move.promotion != 0) append('=').append(Piece.letter(move.promotion))
            }
            else -> buildString {
                append(Piece.letter(type))
                append(disambiguation(pos, move, type))
                if (isCapture) append('x')
                append(Square.name(move.to))
            }
        }

        val next = pos.applyMove(move)
        val suffix = when {
            !next.inCheck() -> ""
            MoveGenerator.legalMoves(next).isEmpty() -> "#"
            else -> "+"
        }
        return body + suffix
    }

    /**
     * Ha több azonos típusú bábu is léphet a célmezőre, a SAN a kiinduló
     * oszloppal, sorral vagy mindkettővel egyértelműsít.
     */
    private fun disambiguation(pos: Position, move: Move, type: Int): String {
        val others = MoveGenerator.legalMoves(pos).filter {
            it.to == move.to && it.from != move.from &&
                Piece.typeOf(pos.board[it.from]) == type
        }
        if (others.isEmpty()) return ""
        val sameFile = others.any { Square.file(it.from) == Square.file(move.from) }
        val sameRank = others.any { Square.rank(it.from) == Square.rank(move.from) }
        return when {
            !sameFile -> "${'a' + Square.file(move.from)}"
            !sameRank -> "${'1' + Square.rank(move.from)}"
            else -> Square.name(move.from)
        }
    }

    /** SAN beolvasása: a szabályos lépések közül keresi az egyezőt; null, ha nincs. */
    fun fromSan(pos: Position, san: String): Move? {
        val cleaned = san.trim().trimEnd('+', '#', '!', '?')
        if (cleaned.isEmpty()) return null
        return MoveGenerator.legalMoves(pos).firstOrNull { m ->
            toSan(pos, m).trimEnd('+', '#') == cleaned
        } ?: fromUci(pos, cleaned)
    }

    /** UCI formátumú lépés ("e2e4", "e7e8q") beolvasása. */
    fun fromUci(pos: Position, uci: String): Move? {
        if (uci.length !in 4..5) return null
        val from = Square.parse(uci.substring(0, 2))
        val to = Square.parse(uci.substring(2, 4))
        if (from < 0 || to < 0) return null
        val promo = if (uci.length == 5) Piece.typeFromLetter(uci[4]) else 0
        return MoveGenerator.legalMoves(pos).firstOrNull {
            it.from == from && it.to == to && it.promotion == promo
        }
    }
}

/** PGN export és import. */
object Pgn {

    fun export(game: Game, headers: Map<String, String> = emptyMap()): String {
        val result = when (game.result().type) {
            ResultType.ONGOING -> "*"
            ResultType.STALEMATE, ResultType.DRAW_FIFTY,
            ResultType.DRAW_REPETITION, ResultType.DRAW_MATERIAL -> "1/2-1/2"
            else -> if (game.result().winner == Piece.WHITE) "1-0" else "0-1"
        }

        val allHeaders = LinkedHashMap<String, String>()
        allHeaders["Event"] = headers["Event"] ?: "?"
        allHeaders["Site"] = headers["Site"] ?: "?"
        allHeaders["Date"] = headers["Date"] ?: "????.??.??"
        allHeaders["Round"] = headers["Round"] ?: "?"
        allHeaders["White"] = headers["White"] ?: "?"
        allHeaders["Black"] = headers["Black"] ?: "?"
        allHeaders["Result"] = result
        if (game.startPosition.fen() != Position.START_FEN) {
            allHeaders["SetUp"] = "1"
            allHeaders["FEN"] = game.startPosition.fen()
        }
        headers.forEach { (k, v) -> if (k !in allHeaders) allHeaders[k] = v }

        val sb = StringBuilder()
        allHeaders.forEach { (k, v) -> sb.append("[").append(k).append(" \"").append(v).append("\"]\n") }
        sb.append('\n')

        val startMoveNumber = game.startPosition.fullmoveNumber
        var line = StringBuilder()
        game.sanMoves.forEachIndexed { i, san ->
            val token = when {
                i == 0 && game.startPosition.sideToMove == Piece.BLACK ->
                    "$startMoveNumber... $san"
                (i + if (game.startPosition.sideToMove == Piece.BLACK) 1 else 0) % 2 == 0 -> {
                    val num = startMoveNumber +
                        (i + if (game.startPosition.sideToMove == Piece.BLACK) 1 else 0) / 2
                    "$num. $san"
                }
                else -> san
            }
            if (line.length + token.length + 1 > 80) {
                sb.append(line.toString().trim()).append('\n')
                line = StringBuilder()
            }
            line.append(token).append(' ')
        }
        line.append(result)
        sb.append(line.toString().trim()).append('\n')
        return sb.toString()
    }

    /** PGN beolvasása; a kommenteket, változatokat és értékeléseket figyelmen kívül hagyja. */
    fun import(pgn: String): Game? {
        val headers = mutableMapOf<String, String>()
        val headerRegex = Regex("""\[(\w+)\s+"([^"]*)"\]""")
        for (m in headerRegex.findAll(pgn)) headers[m.groupValues[1]] = m.groupValues[2]

        var moveText = pgn.lines().filterNot { it.trim().startsWith("[") }.joinToString(" ")
        moveText = moveText
            .replace(Regex("""\{[^}]*\}"""), " ")   // kommentek
            .replace(Regex("""\([^)]*\)"""), " ")   // változatok (egy szint)
            .replace(Regex(""";[^\n]*"""), " ")     // sorvégi kommentek
            .replace(Regex("""\$\d+"""), " ")       // NAG kódok
            .replace(Regex("""\d+\.(\.\.)?"""), " ") // lépésszámok

        val start = headers["FEN"]?.let { Position.fromFen(it) } ?: Position.initial()
        val game = Game(start)
        for (token in moveText.split(Regex("\\s+"))) {
            if (token.isBlank()) continue
            if (token in setOf("1-0", "0-1", "1/2-1/2", "*", "...")) continue
            if (!game.playSan(token)) return null
        }
        return game
    }
}
