package com.vinz.chillworld

import android.opengl.GLES30
import android.opengl.Matrix
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Cascaded Shadow Maps. Satu texture array depth 16-bit (1 layer per cascade),
 * perbandingan hardware (PCF 3x3 dikerjakan di shader). Posisi cascade di-snap ke texel
 * supaya bayangan tidak berkedip saat kamera bergerak.
 */
class Shadows {
    var fbo = 0
        private set
    var tex = 0
        private set
    var res = 0
        private set
    var cascades = 0
        private set

    val lightVP = FloatArray(64)
    val splits = FloatArray(4)
    val texel = FloatArray(4)

    private val lv = FloatArray(16)
    private val proj = FloatArray(16)
    private val vin = FloatArray(4)
    private val vout = FloatArray(4)

    fun allocate(q: Quality) {
        free()
        res = q.shadowRes
        cascades = q.cascades
        tex = Gl.genTexture()
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, tex)
        GLES30.glTexStorage3D(GLES30.GL_TEXTURE_2D_ARRAY, 1, GLES30.GL_DEPTH_COMPONENT16, res, res, cascades)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_COMPARE_MODE, GLES30.GL_COMPARE_REF_TO_TEXTURE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_COMPARE_FUNC, GLES30.GL_LEQUAL)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, 0)

        fbo = Gl.genFbo()
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
        GLES30.glFramebufferTextureLayer(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT, tex, 0, 0)
        GLES30.glDrawBuffers(1, intArrayOf(GLES30.GL_NONE), 0)
        GLES30.glReadBuffer(GLES30.GL_NONE)
        val st = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        if (st != GLES30.GL_FRAMEBUFFER_COMPLETE) android.util.Log.e(Gl.TAG, "FBO shadow tidak lengkap: 0x" + Integer.toHexString(st))
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        Gl.check("Shadows.allocate")
    }

    fun free() {
        Gl.delTexture(tex)
        Gl.delFbo(fbo)
        tex = 0
        fbo = 0
    }

    /** Hitung split, bola pembatas, dan matriks ortho tiap cascade. Panggil setelah camera.updateMatrices. */
    fun compute(cam: Camera, ld: FloatArray, shadowDist: Float) {
        val n = cascades
        val near = cam.near
        val far = shadowDist
        val lambda = 0.7f
        for (i in 0 until 4) {
            if (i < n) {
                val p = (i + 1).toFloat() / n
                val lg = near * (far / near).pow(p)
                val un = near + (far - near) * p
                splits[i] = lambda * lg + (1f - lambda) * un
            } else {
                splits[i] = far
            }
        }

        // matriks view cahaya tetap (berpusat di origin), supaya snapping stabil
        val upx: Float
        val upy: Float
        val upz: Float
        if (abs(ld[1]) > 0.99f) { upx = 0f; upy = 0f; upz = 1f } else { upx = 0f; upy = 1f; upz = 0f }
        Matrix.setLookAtM(lv, 0, ld[0] * 1000f, ld[1] * 1000f, ld[2] * 1000f, 0f, 0f, 0f, upx, upy, upz)

        val th = cam.tanHalfFovY()
        val tx = th * cam.aspect
        for (i in 0 until n) {
            val sn = if (i == 0) near else splits[i - 1]
            val sf = splits[i]
            // titik tengah + radius bola yang membungkus 8 sudut slice frustum
            var mx = 0f
            var my = 0f
            var mz = 0f
            for (k in 0 until 8) {
                corner(cam, k, sn, sf, th, tx)
                mx += vout[0]; my += vout[1]; mz += vout[2]
            }
            mx /= 8f; my /= 8f; mz /= 8f
            var r = 0f
            for (k in 0 until 8) {
                corner(cam, k, sn, sf, th, tx)
                val dx = vout[0] - mx
                val dy = vout[1] - my
                val dz = vout[2] - mz
                val d = sqrt(dx * dx + dy * dy + dz * dz)
                if (d > r) r = d
            }
            r = ceil(r * 16f) / 16f
            vin[0] = mx; vin[1] = my; vin[2] = mz; vin[3] = 1f
            Matrix.multiplyMV(vout, 0, lv, 0, vin, 0)
            val texSize = 2f * r / res
            val sx = floor(vout[0] / texSize) * texSize
            val sy = floor(vout[1] / texSize) * texSize
            val dist = -vout[2]
            Matrix.orthoM(proj, 0, sx - r, sx + r, sy - r, sy + r, dist - r - 600f, dist + r)
            Matrix.multiplyMM(lightVP, i * 16, proj, 0, lv, 0)
            texel[i] = texSize
        }
        for (i in n until 4) texel[i] = texel[maxOf(n - 1, 0)]
    }

    private fun corner(cam: Camera, k: Int, sn: Float, sf: Float, th: Float, tx: Float) {
        val d = if ((k and 4) == 0) sn else sf
        val sxv = if ((k and 1) == 0) -1f else 1f
        val syv = if ((k and 2) == 0) -1f else 1f
        vout[0] = cam.x + cam.fwd[0] * d + cam.right[0] * sxv * tx * d + cam.up[0] * syv * th * d
        vout[1] = cam.y + cam.fwd[1] * d + cam.right[1] * sxv * tx * d + cam.up[1] * syv * th * d
        vout[2] = cam.z + cam.fwd[2] * d + cam.right[2] * sxv * tx * d + cam.up[2] * syv * th * d
    }

    fun bind(i: Int) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
        GLES30.glFramebufferTextureLayer(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT, tex, 0, i)
        GLES30.glViewport(0, 0, res, res)
        GLES30.glClearDepthf(1f)
        GLES30.glClear(GLES30.GL_DEPTH_BUFFER_BIT)
    }

    /** Apakah AABB chunk menyentuh area ortho cascade i (tes di ruang cahaya, XY saja)? */
    fun chunkInCascade(i: Int, c: Chunk): Boolean {
        val o = i * 16
        val x0 = c.worldX
        val z0 = c.worldZ
        var minX = 1e9f
        var maxX = -1e9f
        var minY = 1e9f
        var maxY = -1e9f
        for (k in 0 until 8) {
            val x = if ((k and 1) == 0) x0 else x0 + 64f
            val y = if ((k and 2) == 0) c.minY else c.maxY
            val z = if ((k and 4) == 0) z0 else z0 + 64f
            val px = lightVP[o] * x + lightVP[o + 4] * y + lightVP[o + 8] * z + lightVP[o + 12]
            val py = lightVP[o + 1] * x + lightVP[o + 5] * y + lightVP[o + 9] * z + lightVP[o + 13]
            if (px < minX) minX = px
            if (px > maxX) maxX = px
            if (py < minY) minY = py
            if (py > maxY) maxY = py
        }
        return !(maxX < -1f || minX > 1f || maxY < -1f || minY > 1f)
    }

    /** Salin matriks cascade i ke out (16 float). */
    fun copyVP(i: Int, out: FloatArray) {
        System.arraycopy(lightVP, i * 16, out, 0, 16)
    }
}
