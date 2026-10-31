package com.vinz.chillworld

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min

/** Simplex noise 2D dengan seed tetap, jadi dunia selalu sama tiap main. */
object Noise {
    private const val F2 = 0.3660254f
    private const val G2 = 0.21132487f
    private val perm = IntArray(512)
    private val gx = floatArrayOf(1f, -1f, 1f, -1f, 1f, -1f, 0f, 0f)
    private val gy = floatArrayOf(1f, 1f, -1f, -1f, 0f, 0f, 1f, -1f)

    init {
        val p = IntArray(256) { it }
        var s = 1337L
        for (i in 255 downTo 1) {
            s = s * 6364136223846793005L + 1442695040888963407L
            val j = ((s ushr 33) % (i + 1)).toInt()
            val t = p[i]
            p[i] = p[j]
            p[j] = t
        }
        for (i in 0 until 512) perm[i] = p[i and 255]
    }

    /** Hasil sekitar -1..1. */
    fun simplex(xin: Float, yin: Float): Float {
        val s = (xin + yin) * F2
        val i = floor(xin + s).toInt()
        val j = floor(yin + s).toInt()
        val t = (i + j) * G2
        val x0 = xin - (i - t)
        val y0 = yin - (j - t)
        val i1: Int
        val j1: Int
        if (x0 > y0) { i1 = 1; j1 = 0 } else { i1 = 0; j1 = 1 }
        val x1 = x0 - i1 + G2
        val y1 = y0 - j1 + G2
        val x2 = x0 - 1f + 2f * G2
        val y2 = y0 - 1f + 2f * G2
        val ii = i and 255
        val jj = j and 255
        var n = 0f
        var tt = 0.5f - x0 * x0 - y0 * y0
        if (tt > 0f) {
            val g = perm[ii + perm[jj]] and 7
            tt *= tt
            n += tt * tt * (gx[g] * x0 + gy[g] * y0)
        }
        tt = 0.5f - x1 * x1 - y1 * y1
        if (tt > 0f) {
            val g = perm[ii + i1 + perm[jj + j1]] and 7
            tt *= tt
            n += tt * tt * (gx[g] * x1 + gy[g] * y1)
        }
        tt = 0.5f - x2 * x2 - y2 * y2
        if (tt > 0f) {
            val g = perm[ii + 1 + perm[jj + 1]] and 7
            tt *= tt
            n += tt * tt * (gx[g] * x2 + gy[g] * y2)
        }
        return (70f * n).coerceIn(-1f, 1f)
    }

    /** fBm, dinormalisasi kira-kira ke -1..1. */
    fun fbm(x: Float, y: Float, oct: Int): Float {
        var sum = 0f
        var amp = 1f
        var norm = 0f
        var fx = x
        var fy = y
        for (o in 0 until oct) {
            sum += simplex(fx, fy) * amp
            norm += amp
            amp *= 0.5f
            fx *= 2.03f
            fy *= 2.03f
        }
        return sum / norm
    }

    /** Hash integer ke 0..1 (deterministik). */
    fun hash(ix: Int, iz: Int, salt: Int): Float {
        var h = ix * 374761393 + iz * 668265263 + salt * 1013904223
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0xFFFFFF) / 16777216f
    }
}

/** Fungsi ketinggian dunia: lembah, bukit, sampai gunung (maks 400 m). Aman dipanggil dari thread mana pun. */
object Terrain {
    const val MAX_HEIGHT = 400f

    fun heightAt(x: Float, z: Float): Float {
        val valley = Noise.fbm(x * 0.0035f, z * 0.0035f, 4) * 14f
        val mm = Noise.fbm(x * 0.00055f + 17.3f, z * 0.00055f - 9.1f, 3) * 0.5f + 0.5f
        val m = Gl.smooth(0.30f, 0.72f, mm)
        val rid = 1f - abs(Noise.fbm(x * 0.0016f + 5.2f, z * 0.0016f + 1.7f, 5))
        val rid2 = rid * rid
        val detail = Noise.fbm(x * 0.012f, z * 0.012f, 3) * 6f
        val h = 24f + valley + detail * (0.4f + m) + m * m * (100f + 280f * rid2)
        return min(h, MAX_HEIGHT)
    }
}
