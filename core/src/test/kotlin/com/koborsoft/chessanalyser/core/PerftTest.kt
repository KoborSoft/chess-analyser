package com.koborsoft.chessanalyser.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A lépésgenerálás helyességének ellenőrzése ismert perft-értékekkel.
 * Források: https://www.chessprogramming.org/Perft_Results
 */
class PerftTest {

    private fun check(fen: String, vararg expected: Long) {
        val pos = Position.fromFen(fen)
        expected.forEachIndexed { i, exp ->
            assertEquals("$fen, mélység ${i + 1}", exp, MoveGenerator.perft(pos, i + 1))
        }
    }

    @Test
    fun kezdoallas() =
        check(Position.START_FEN, 20, 400, 8902, 197281)

    @Test
    fun kiwipete() =
        check(
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
            48, 2039, 97862,
        )

    @Test
    fun vegjatek() =
        check("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1", 14, 191, 2812, 43238)

    @Test
    fun atvaltozasok() =
        check(
            "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1",
            6, 264, 9467,
        )

    @Test
    fun tukrozott() =
        check(
            "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10",
            46, 2079, 89890,
        )
}

class SzabalyTest {

    @Test
    fun `matt felismerese - susztermatt`() {
        val game = Game()
        listOf("f3", "e5", "g4", "Qh4#").forEach { assert(game.playSan(it)) { it } }
        assertEquals(ResultType.CHECKMATE, game.result().type)
        assertEquals(Piece.BLACK, game.result().winner)
    }

    @Test
    fun `patt felismerese`() {
        val pos = Position.fromFen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1")
        val game = Game(pos)
        assertEquals(ResultType.STALEMATE, game.result().type)
    }

    @Test
    fun `anyaghiany - kiraly futo kiraly ellen`() {
        val pos = Position.fromFen("8/8/4k3/8/8/2B5/4K3/8 w - - 0 1")
        assert(Game.isInsufficientMaterial(pos))
    }

    @Test
    fun `fen oda-vissza`() {
        val fen = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1"
        assertEquals(fen, Position.fromFen(fen).fen())
    }

    @Test
    fun `visszavonas helyreallitja az allast`() {
        val game = Game()
        val fenBefore = game.position.fen()
        game.playSan("e4")
        game.playSan("e5")
        game.undo()
        game.undo()
        assertEquals(fenBefore, game.position.fen())
    }

    @Test
    fun `pgn export-import oda-vissza`() {
        val game = Game()
        listOf("e4", "e5", "Nf3", "Nc6", "Bb5", "a6", "O-O").forEach {
            assert(game.playSan(it)) { it }
        }
        val pgn = Pgn.export(game)
        val imported = Pgn.import(pgn)
        assertEquals(game.position.fen(), imported?.position?.fen())
    }
}
