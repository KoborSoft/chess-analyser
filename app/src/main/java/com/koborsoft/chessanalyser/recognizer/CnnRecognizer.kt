package com.koborsoft.chessanalyser.recognizer

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import java.nio.FloatBuffer

/**
 * Offline állás-felismerő: a beágyazott ONNX CNN-nel (S1M0N38/chess-cv, MIT)
 * osztályozza a tábla 64 mezőjét, és FEN bábu-elhelyezést állít elő.
 *
 * A modell mezőnként 32×32 RGB képet vár (0..1). A tábla helyét a
 * [BoardLocator] adja; innen minden mezőt kivágunk, egy batch-ben futtatjuk,
 * softmax-szal mezőnkénti konfidenciát számolunk. A kimenet szabályosságát
 * (királyok száma, gyalog az 1./8. soron) ellenőrizzük — ez erősebb hibajelző,
 * mint a konfidencia.
 */
object CnnRecognizer {

    /** A 13 osztály sorrendje a modell szerint. */
    private val CLASSES = arrayOf(
        "bB", "bK", "bN", "bP", "bQ", "bR", "wB", "wK", "wN", "wP", "wQ", "wR", "xx",
    )
    /** Osztály → FEN-betű (üres = '.'). */
    private val FEN_CHARS = charArrayOf(
        'b', 'k', 'n', 'p', 'q', 'r', 'B', 'K', 'N', 'P', 'Q', 'R', '.',
    )

    /**
     * @param placement FEN bábu-elhelyezés (rank8/…/rank1), fehér-alul tájolással.
     * @param confidences mezőnkénti softmax-konfidencia, 0..63 (a1=0 … h8=63).
     * @param suspect igaz, ha az állás szabálytalan (gyanús felismerés).
     * @param reason a gyanú oka (kulcs), vagy null.
     */
    data class Recognition(
        val placement: String,
        val confidences: FloatArray,
        val suspect: Boolean,
        val reason: String?,
        /** A tábla-lokalizáció megbízhatósága (sakktábla-korreláció, 0..1). */
        val boardConfidence: Float,
    )

    @Volatile private var env: OrtEnvironment? = null
    @Volatile private var session: OrtSession? = null

    private fun ensureSession(context: Context): OrtSession {
        session?.let { return it }
        synchronized(this) {
            session?.let { return it }
            val e = OrtEnvironment.getEnvironment()
            val bytes = context.assets.open("pieces.onnx").use { it.readBytes() }
            val s = e.createSession(bytes, OrtSession.SessionOptions())
            env = e
            session = s
            return s
        }
    }

    fun recognize(context: Context, bitmap: Bitmap): Recognition {
        val board = BoardLocator.locate(bitmap)
        val s = ensureSession(context)
        val e = env!!

        // 64 mező kivágása és 32×32-re méretezése -> NCHW float buffer (0..1)
        val buf = FloatBuffer.allocate(64 * 3 * 32 * 32)
        val cell = board.cell
        for (r in 0 until 8) {
            for (c in 0 until 8) {
                val left = (board.x + c * cell).coerceIn(0, bitmap.width - 1)
                val top = (board.y + r * cell).coerceIn(0, bitmap.height - 1)
                val wdt = cell.coerceAtMost(bitmap.width - left)
                val hgt = cell.coerceAtMost(bitmap.height - top)
                val crop = Bitmap.createBitmap(bitmap, left, top, maxOf(1, wdt), maxOf(1, hgt))
                val sq = Bitmap.createScaledBitmap(crop, 32, 32, true)
                if (crop != sq) crop.recycle()
                val k = r * 8 + c
                val pix = IntArray(32 * 32)
                sq.getPixels(pix, 0, 32, 0, 0, 32, 32)
                sq.recycle()
                // csatornánként: R, majd G, majd B (NCHW)
                val base = k * 3 * 32 * 32
                for (ch in 0 until 3) {
                    val shift = when (ch) { 0 -> 16; 1 -> 8; else -> 0 }
                    var idx = base + ch * 32 * 32
                    for (p in pix) {
                        buf.put(idx, ((p ushr shift) and 0xFF) / 255f)
                        idx++
                    }
                }
            }
        }
        buf.rewind()

        val inputName = s.inputNames.iterator().next()
        val tensor = OnnxTensor.createTensor(e, buf, longArrayOf(64, 3, 32, 32))
        val logits: Array<FloatArray> = tensor.use {
            s.run(mapOf(inputName to it)).use { res ->
                @Suppress("UNCHECKED_CAST")
                (res[0].value as Array<FloatArray>)
            }
        }

        // Softmax + argmax mezőnként; placement a display-sorrendből (felső sor = rank8)
        val conf = FloatArray(64)
        val cls = IntArray(64)
        for (k in 0 until 64) {
            val row = logits[k]
            var mx = row[0]
            for (v in row) if (v > mx) mx = v
            var sum = 0f
            for (i in row.indices) { row[i] = kotlin.math.exp(row[i] - mx); sum += row[i] }
            var bi = 0
            for (i in row.indices) if (row[i] > row[bi]) bi = i
            cls[k] = bi
            conf[k] = row[bi] / sum
        }

        val placement = buildPlacement(cls)
        val (suspect, reason) = checkLegality(cls)

        // Diagnosztika: a felismert állás soronként + konfidencia-összegzés.
        val sb = StringBuilder()
        for (r in 0 until 8) {
            for (c in 0 until 8) sb.append(FEN_CHARS[cls[r * 8 + c]])
            if (r < 7) sb.append('/')
        }
        var minC = 1f; var sumC = 0f
        for (v in conf) { if (v < minC) minC = v; sumC += v }
        android.util.Log.d(
            "CnnRecog",
            "recognize: bitmap=${bitmap.width}x${bitmap.height} board=(${board.x},${board.y}) " +
                "cell=${board.cell} placement=$placement suspect=$suspect reason=$reason " +
                "confAvg=${"%.3f".format(sumC / 64)} confMin=${"%.3f".format(minC)}",
        )
        return Recognition(placement, conf, suspect, reason, board.confidence)
    }

    /** A 64 osztályból (display-sorrend, felső sor elöl) FEN bábu-elhelyezés. */
    private fun buildPlacement(cls: IntArray): String {
        val sb = StringBuilder()
        for (r in 0 until 8) {
            var empty = 0
            for (c in 0 until 8) {
                val ch = FEN_CHARS[cls[r * 8 + c]]
                if (ch == '.') {
                    empty++
                } else {
                    if (empty > 0) { sb.append(empty); empty = 0 }
                    sb.append(ch)
                }
            }
            if (empty > 0) sb.append(empty)
            if (r < 7) sb.append('/')
        }
        return sb.toString()
    }

    /** Szabályossági ellenőrzés: királyok száma és gyalog az 1./8. soron. */
    private fun checkLegality(cls: IntArray): Pair<Boolean, String?> {
        var whiteKings = 0
        var blackKings = 0
        var pawnOnEdge = false
        for (r in 0 until 8) {
            for (c in 0 until 8) {
                when (FEN_CHARS[cls[r * 8 + c]]) {
                    'K' -> whiteKings++
                    'k' -> blackKings++
                    'P', 'p' -> if (r == 0 || r == 7) pawnOnEdge = true
                }
            }
        }
        return when {
            whiteKings != 1 || blackKings != 1 -> true to "recog_suspect_kings"
            pawnOnEdge -> true to "recog_suspect_pawn"
            else -> false to null
        }
    }
}
