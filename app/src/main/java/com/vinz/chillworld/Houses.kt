package com.vinz.chillworld

import android.opengl.GLES30
import java.nio.FloatBuffer
import kotlin.math.sqrt

/**
 * Rumah prosedural (kayu + plester + atap genteng + pintu + jendela + cerobong).
 * Jumlah rumah sedikit, jadi culling-nya cukup di CPU untuk kedua jalur.
 * ID material di vertex: 0 plester, 1 papan kayu, 2 genteng, 3 kaca.
 */
class Houses {
    companion object {
        const val CAP = 256
    }

    private var mesh: Mesh? = null
    private var instBuf = 0
    private val stage: FloatBuffer = Gl.floatBuf(CAP * 8)
    private val planes = FloatArray(24)
    private var count = 0
    private var camX = 0f
    private var camZ = 0f
    private var pMain = 0
    private var pShadow = 0
    private var pDepth = 0
    private var svpShadow = -1
    var maxDist = 350f

    fun build() {
        instBuf = Gl.genBuffer()
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, instBuf)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, CAP * 32, null, GLES30.GL_DYNAMIC_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)

        val b = MeshBuilder()
        val W = 3.5f      // setengah lebar (sumbu x)
        val D = 2.75f     // setengah dalam (sumbu z)
        val bot = -1.5f   // dinding menjorok ke bawah tanah supaya tidak ada celah
        val top = 3.2f
        val ridge = 5.45f

        // dinding (plester). Titik berurutan CCW dilihat dari luar.
        face(b, floatArrayOf(-W, bot, D, W, bot, D, W, top, D, -W, top, D), 4, 0, 2f)       // depan
        face(b, floatArrayOf(W, bot, -D, -W, bot, -D, -W, top, -D, W, top, -D), 4, 0, 2f)   // belakang
        face(b, floatArrayOf(-W, bot, -D, -W, bot, D, -W, top, D, -W, top, -D), 4, 0, 2f)   // kiri
        face(b, floatArrayOf(W, bot, D, W, bot, -D, W, top, -D, W, top, D), 4, 0, 2f)       // kanan
        // segitiga gable kiri/kanan
        face(b, floatArrayOf(-W, top, -D, -W, top, D, -W, ridge, 0f), 3, 0, 2f)
        face(b, floatArrayOf(W, top, D, W, top, -D, W, ridge, 0f), 3, 0, 2f)

        // atap genteng dengan overhang
        face(b, floatArrayOf(-3.95f, 2.7f, 3.3f, 3.95f, 2.7f, 3.3f, 3.95f, ridge, 0f, -3.95f, ridge, 0f), 4, 2, 1.5f)
        face(b, floatArrayOf(3.95f, 2.7f, -3.3f, -3.95f, 2.7f, -3.3f, -3.95f, ridge, 0f, 3.95f, ridge, 0f), 4, 2, 1.5f)

        // pintu depan (papan kayu) + bingkai
        face(b, floatArrayOf(-0.62f, 0f, 2.77f, 0.62f, 0f, 2.77f, 0.62f, 2.2f, 2.77f, -0.62f, 2.2f, 2.77f), 4, 1, 1f)
        // jendela depan (kaca) + bingkai papan
        for (sx in floatArrayOf(-2.2f, 2.2f)) {
            face(b, floatArrayOf(sx - 0.75f, 0.9f, 2.765f, sx + 0.75f, 0.9f, 2.765f, sx + 0.75f, 2.3f, 2.765f, sx - 0.75f, 2.3f, 2.765f), 4, 1, 1f)
            face(b, floatArrayOf(sx - 0.62f, 1.0f, 2.78f, sx + 0.62f, 1.0f, 2.78f, sx + 0.62f, 2.2f, 2.78f, sx - 0.62f, 2.2f, 2.78f), 4, 3, 1f)
        }
        // jendela samping
        face(b, floatArrayOf(-3.53f, 1.0f, -0.6f, -3.53f, 1.0f, 0.6f, -3.53f, 2.2f, 0.6f, -3.53f, 2.2f, -0.6f), 4, 3, 1f)
        face(b, floatArrayOf(3.53f, 1.0f, 0.6f, 3.53f, 1.0f, -0.6f, 3.53f, 2.2f, -0.6f, 3.53f, 2.2f, 0.6f), 4, 3, 1f)

        // cerobong (plester), keempat sisi
        val cx0 = 2.0f
        val cx1 = 2.8f
        val cz0 = -1.2f
        val cz1 = -0.4f
        val cb = 3.6f
        val ct = 6.6f
        face(b, floatArrayOf(cx0, cb, cz1, cx1, cb, cz1, cx1, ct, cz1, cx0, ct, cz1), 4, 0, 2f)
        face(b, floatArrayOf(cx1, cb, cz0, cx0, cb, cz0, cx0, ct, cz0, cx1, ct, cz0), 4, 0, 2f)
        face(b, floatArrayOf(cx0, cb, cz0, cx0, cb, cz1, cx0, ct, cz1, cx0, ct, cz0), 4, 0, 2f)
        face(b, floatArrayOf(cx1, cb, cz1, cx1, cb, cz0, cx1, ct, cz0, cx1, ct, cz1), 4, 0, 2f)
        face(b, floatArrayOf(cx0, ct, cz1, cx1, ct, cz1, cx1, ct, cz0, cx0, ct, cz0), 4, 0, 2f)

        // undakan depan pintu (papan kayu)
        face(b, floatArrayOf(-1.1f, 0.25f, 3.5f, 1.1f, 0.25f, 3.5f, 1.1f, 0.25f, 2.75f, -1.1f, 0.25f, 2.75f), 4, 1, 1f)
        face(b, floatArrayOf(-1.1f, -0.5f, 3.5f, 1.1f, -0.5f, 3.5f, 1.1f, 0.25f, 3.5f, -1.1f, 0.25f, 3.5f), 4, 1, 1f)
        face(b, floatArrayOf(1.1f, -0.5f, 3.5f, 1.1f, -0.5f, 2.75f, 1.1f, 0.25f, 2.75f, 1.1f, 0.25f, 3.5f), 4, 1, 1f)
        face(b, floatArrayOf(-1.1f, -0.5f, 2.75f, -1.1f, -0.5f, 3.5f, -1.1f, 0.25f, 3.5f, -1.1f, 0.25f, 2.75f), 4, 1, 1f)

        mesh = b.upload(instBuf)
    }

    /** Poligon konveks; normal dari titik 0..2, UV dari proyeksi ke bidangnya (tile = meter per ulangan texture). */
    private fun face(b: MeshBuilder, p: FloatArray, n: Int, mat: Int, tile: Float) {
        val ax = p[3] - p[0]
        val ay = p[4] - p[1]
        val az = p[5] - p[2]
        val bx = p[6] - p[0]
        val by = p[7] - p[1]
        val bz = p[8] - p[2]
        var nx = ay * bz - az * by
        var ny = az * bx - ax * bz
        var nz = ax * by - ay * bx
        val nl = sqrt(nx * nx + ny * ny + nz * nz)
        nx /= nl; ny /= nl; nz /= nl
        val ul = sqrt(ax * ax + ay * ay + az * az)
        val ux = ax / ul
        val uy = ay / ul
        val uz = az / ul
        // v = n x u
        val vx = ny * uz - nz * uy
        val vy = nz * ux - nx * uz
        val vz = nx * uy - ny * ux
        val first = b.vCount
        for (i in 0 until n) {
            val dx = p[i * 3] - p[0]
            val dy = p[i * 3 + 1] - p[1]
            val dz = p[i * 3 + 2] - p[2]
            val u = (dx * ux + dy * uy + dz * uz) / tile
            val v = (dx * vx + dy * vy + dz * vz) / tile
            b.v(p[i * 3], p[i * 3 + 1], p[i * 3 + 2], nx, ny, nz, u, v, mat.toFloat(), 0f)
        }
        for (i in 1 until n - 1) b.tri(first, first + i, first + i + 1)
    }

    fun loadPrograms(sh: ShaderLoader) {
        pMain = sh.program("house.vert", "house.frag")
        pShadow = sh.program("house.vert", "house.frag", listOf("SHADOW"))
        pDepth = sh.program("house.vert", "house.frag", listOf("DEPTH"))
        svpShadow = GLES30.glGetUniformLocation(pShadow, "uShadowVP")
    }

    fun begin(vp: FloatArray, cx: Float, cz: Float) {
        Frustum.extract(vp, planes)
        camX = cx
        camZ = cz
        count = 0
        stage.clear()
    }

    fun add(c: Chunk) {
        for (i in 0 until c.houseCount) {
            if (count >= CAP) return
            val o = i * 8
            val x = c.houses[o]
            val y = c.houses[o + 1]
            val z = c.houses[o + 2]
            val r = c.houses[o + 7]
            val dx = x - camX
            val dz = z - camZ
            if (dx * dx + dz * dz > maxDist * maxDist) continue
            var ok = true
            for (k in 0 until 6) {
                val p = k * 4
                if (planes[p] * x + planes[p + 1] * (y + r * 0.5f) + planes[p + 2] * z + planes[p + 3] < -r) {
                    ok = false
                    break
                }
            }
            if (!ok) continue
            stage.put(c.houses, o, 8)
            count++
        }
    }

    fun end() {
        if (count == 0) return
        stage.position(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, instBuf)
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, count * 32, stage)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    fun draw(pass: Pass, shadowVp: FloatArray?) {
        val m = mesh ?: return
        if (count == 0) return
        val prog = when (pass) {
            Pass.MAIN -> pMain
            Pass.SHADOW -> pShadow
            Pass.DEPTH -> pDepth
        }
        if (prog == 0) return
        GLES30.glUseProgram(prog)
        if (pass == Pass.SHADOW && shadowVp != null) {
            GLES30.glUniformMatrix4fv(svpShadow, 1, false, shadowVp, 0)
        }
        GLES30.glBindVertexArray(m.vao)
        GLES30.glDrawElementsInstanced(GLES30.GL_TRIANGLES, m.indexCount, GLES30.GL_UNSIGNED_SHORT, 0, count)
        GLES30.glBindVertexArray(0)
    }

    fun visibleCount(): Int = count
}
