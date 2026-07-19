package com.koborsoft.chessanalyser.recognizer

import android.graphics.Bitmap

/**
 * Tengely-igazított sakktábla megkeresése digitális screenshoton, OpenCV nélkül.
 *
 * A világos/sötét mezők miatt a tábla szabályos, egyenlő közű éleket ad; az
 * él-profilok autokorrelációja adja a mezőméret (periódus) jelölteket. Mivel a
 * tábla gyakran csak a kép egy részét tölti ki (fent/lent UI-sáv, overlay),
 * egyetlen periódus-becslés törékeny — ezért TÖBB periódus-jelöltet próbálunk
 * (mindkét tengelyről + teljes-szélesség/magasság prior), és mindegyikhez a
 * legjobb eltolást SAKKTÁBLA-KORRELÁCIÓVAL keressük. A globálisan legjobb
 * korrelációjú (periódus, eltolás) nyer; a korreláció egyben megbízhatóság:
 * alacsony érték = nincs (tiszta) tábla a képen.
 *
 * A lokalizáció közel teljes felbontáson fut a pontosságért; csak nagyon nagy
 * képet kicsinyítünk le [WORK_MAX]-ra.
 */
object BoardLocator {

    /**
     * Tábla-téglalap az eredeti kép pixelkoordinátáiban. A [confidence] a
     * sakktábla-korreláció (0..1): alacsony érték = valószínűleg nincs (tiszta)
     * tábla a képen, a felismerés nem megbízható.
     */
    data class Board(val x: Int, val y: Int, val cell: Int, val confidence: Float) {
        val size: Int get() = cell * 8
    }

    private const val WORK_MAX = 1600  // lokalizációs munkafelbontás felső határa

    fun locate(bitmap: Bitmap): Board {
        val srcW = bitmap.width
        val srcH = bitmap.height
        val scale = minOf(1f, WORK_MAX.toFloat() / maxOf(srcW, srcH))
        val w = maxOf(64, (srcW * scale).toInt())
        val h = maxOf(64, (srcH * scale).toInt())
        val small = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, w, h, true) else bitmap
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        if (small != bitmap) small.recycle()

        val gray = FloatArray(w * h)
        for (i in px.indices) {
            val p = px[i]
            val r = (p ushr 16) and 0xFF
            val g = (p ushr 8) and 0xFF
            val b = p and 0xFF
            gray[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }

        // Él-profilok: gx hossza w-1, gy hossza h-1
        val gx = FloatArray(w - 1)
        val gy = FloatArray(h - 1)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w - 1) {
                gx[x] += kotlin.math.abs(gray[row + x + 1] - gray[row + x])
            }
        }
        for (y in 0 until h - 1) {
            val row = y * w
            val nrow = row + w
            for (x in 0 until w) {
                gy[y] += kotlin.math.abs(gray[nrow + x] - gray[row + x])
            }
        }

        val pmin = maxOf(8, minOf(w, h) / 40)
        val pmax = minOf(w, h) / 8

        // Periódus-jelöltek mindkét tengely autokorrelációs csúcsaiból, plusz a
        // teljes-szélesség/magasság prior (gyakori: a tábla kitölti a szélességet).
        val candidates = LinkedHashSet<Int>()
        candidates.addAll(topPeriods(gx, pmin, pmax, 4))
        candidates.addAll(topPeriods(gy, pmin, pmax, 4))
        candidates.add((w / 8).coerceIn(pmin, pmax))
        candidates.add((h / 8).coerceIn(pmin, pmax))

        var bestScore = -1.0
        var ox = 0
        var oy = 0
        var period = pmin
        for (per in candidates) {
            if (per * 8 > w || per * 8 > h) continue
            val ox0 = findOffset(gx, per)
            val oy0 = findOffset(gy, per)
            val (cx, cy, sc) = refineByChecker(gray, w, h, ox0, oy0, per)
            if (sc > bestScore) {
                bestScore = sc
                ox = cx
                oy = cy
                period = per
            }
        }

        val inv = 1f / scale
        val board = Board(
            x = (ox * inv).toInt().coerceIn(0, srcW - 8),
            y = (oy * inv).toInt().coerceIn(0, srcH - 8),
            cell = (period * inv).toInt().coerceAtLeast(1),
            confidence = bestScore.toFloat(),
        )
        android.util.Log.d(
            "CnnRecog",
            "locate: src=${srcW}x$srcH work=${w}x$h period=$period conf=${"%.3f".format(bestScore)} " +
                "-> board=(${board.x},${board.y}) cell=${board.cell} size=${board.size}",
        )
        return board
    }

    /** Autokorreláció adott periódusra (k=1..7 eltolások átlaga). */
    private fun autocorr(p: FloatArray, mean: Float, period: Int): Double {
        val n = p.size
        var sum = 0.0
        var cnt = 0
        var k = 1
        while (k < 8) {
            val shift = period * k
            if (shift >= n) break
            var dot = 0.0
            var i = 0
            val lim = n - shift
            while (i < lim) {
                dot += (p[i] - mean).toDouble() * (p[i + shift] - mean)
                i++
            }
            sum += dot
            cnt++
            k++
        }
        return if (cnt >= 3) sum / cnt else -1e18
    }

    /** A [topN] legerősebb autokorrelációjú periódus a [pmin,pmax] tartományból. */
    private fun topPeriods(prof: FloatArray, pmin: Int, pmax: Int, topN: Int): List<Int> {
        var mean = 0f
        for (v in prof) mean += v
        mean /= prof.size
        val scored = ArrayList<Pair<Double, Int>>(pmax - pmin + 1)
        var period = pmin
        while (period <= pmax) {
            scored.add(autocorr(prof, mean, period) to period)
            period++
        }
        scored.sortByDescending { it.first }
        return scored.take(topN).map { it.second }
    }

    /** Legjobb kezdő-eltolás: a 9 rácsvonal (0..8*period) profilösszege maximális. */
    private fun findOffset(prof: FloatArray, period: Int): Int {
        val n = prof.size
        val extent = period * 8
        var best = -1e18
        var bestOff = 0
        var off = 0
        while (off <= n - extent) {
            var v = 0.0
            var i = 0
            while (i <= 8) {
                val idx = off + Math.round(i * period.toDouble()).toInt()
                if (idx < n) v += prof[idx]
                i++
            }
            if (v > best) {
                best = v
                bestOff = off
            }
            off++
        }
        return bestOff
    }

    /**
     * Az [ox0]/[oy0] durva eltolás rácsán (± egész periódusok) megkeresi azt a
     * (ox, oy) párt, amelyre a 8×8 régió a legjobban követi a sakktábla-mintázatot.
     */
    private fun refineByChecker(
        gray: FloatArray, w: Int, h: Int, ox0: Int, oy0: Int, period: Int,
    ): Triple<Int, Int, Double> {
        val extent = period * 8
        val xs = gridCandidates(ox0, period, w - extent)
        val ys = gridCandidates(oy0, period, h - extent)
        var best = -1.0
        var bx = ox0
        var by = oy0
        for (ox in xs) {
            for (oy in ys) {
                val sc = checkerScore(gray, w, ox, oy, period)
                if (sc > best) {
                    best = sc
                    bx = ox
                    by = oy
                }
            }
        }
        return Triple(bx, by, best)
    }

    /** Az `o0 mod period`-del kongruens, [0, maxOff] közti eltolások. */
    private fun gridCandidates(o0: Int, period: Int, maxOff: Int): List<Int> {
        val start = ((o0 % period) + period) % period
        val out = ArrayList<Int>()
        var o = start
        while (o <= maxOff) {
            out.add(o)
            o += period
        }
        if (out.isEmpty()) out.add(o0.coerceIn(0, maxOf(0, maxOff)))
        return out
    }

    /**
     * Sakktábla-korreláció: mennyire követi a 64 mező középső részének átlagos
     * luminanciája a váltakozó (r+c páros = világos) mintázatot. Előjel-független
     * abszolút korreláció. A mezőn belül ritkított mintavétel a sebességért.
     */
    private fun checkerScore(gray: FloatArray, w: Int, ox: Int, oy: Int, period: Int): Double {
        val margin = maxOf(1, period / 4)
        val inner = period - 2 * margin
        val step = maxOf(1, inner / 4)  // ~4 minta / tengely / mező
        val l = DoubleArray(64)
        var lmean = 0.0
        for (r in 0 until 8) {
            for (c in 0 until 8) {
                val y0 = oy + r * period + margin
                val x0 = ox + c * period + margin
                val y1 = oy + (r + 1) * period - margin
                val x1 = ox + (c + 1) * period - margin
                var sum = 0.0
                var cnt = 0
                var y = y0
                while (y < y1) {
                    val row = y * w
                    var x = x0
                    while (x < x1) {
                        sum += gray[row + x]
                        cnt++
                        x += step
                    }
                    y += step
                }
                val m = if (cnt > 0) sum / cnt else 0.0
                l[r * 8 + c] = m
                lmean += m
            }
        }
        lmean /= 64
        var num = 0.0
        var den = 0.0
        for (r in 0 until 8) {
            for (c in 0 until 8) {
                val s = if ((r + c) % 2 == 0) 1.0 else -1.0
                val d = l[r * 8 + c] - lmean
                num += d * s
                den += d * d
            }
        }
        return if (den > 0) kotlin.math.abs(num) / (kotlin.math.sqrt(den) * 8.0) else 0.0
    }
}
