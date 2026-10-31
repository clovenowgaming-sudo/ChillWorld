package com.vinz.chillworld

import android.opengl.Matrix
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Kamera orang pertama: jalan di tanah atau mode terbang. */
class Camera {
    var x = 0f
    var y = 0f
    var z = 0f
    var yaw = 0f          // radian, 0 = menghadap -Z
    var pitch = -0.05f
    var fovY = 65f
    var near = 0.3f
    var far = 800f
    var aspect = 1.7f

    val view = FloatArray(16)
    val proj = FloatArray(16)
    val projJ = FloatArray(16)
    val vp = FloatArray(16)
    val vpJ = FloatArray(16)
    val prevVp = FloatArray(16)
    val invVp = FloatArray(16)
    val fwd = FloatArray(3)
    val right = FloatArray(3)
    val up = FloatArray(3)
    private var first = true

    init {
        Matrix.setIdentityM(vp, 0)
        Matrix.setIdentityM(prevVp, 0)
    }

    /** Taruh kamera di tanah pada posisi awal. */
    fun spawn(px: Float, pz: Float) {
        x = px
        z = pz
        y = Terrain.heightAt(px, pz) + 1.7f
    }

    fun move(dt: Float, mx: Float, my: Float, fly: Boolean, vertical: Float) {
        val sp = if (fly) 30f else 6f
        val fx = sin(yaw)
        val fz = -cos(yaw)
        val rx = cos(yaw)
        val rz = sin(yaw)
        if (fly) {
            val cp = cos(pitch)
            x += (rx * mx + fx * cp * my) * sp * dt
            z += (rz * mx + fz * cp * my) * sp * dt
            y += (sin(pitch) * my + vertical * 0.7f) * sp * dt
            val g = Terrain.heightAt(x, z) + 1.2f
            if (y < g) y = g
        } else {
            x += (rx * mx + fx * my) * sp * dt
            z += (rz * mx + fz * my) * sp * dt
            val target = Terrain.heightAt(x, z) + 1.7f
            y += (target - y) * minOf(1f, dt * 10f)
        }
    }

    fun look(dxPx: Float, dyPx: Float) {
        yaw += dxPx * 0.0035f
        pitch -= dyPx * 0.0035f
        if (pitch > 1.5f) pitch = 1.5f
        if (pitch < -1.5f) pitch = -1.5f
    }

    /** Hitung semua matriks. jx/jy = jitter TAA dalam satuan NDC. */
    fun updateMatrices(jx: Float, jy: Float) {
        System.arraycopy(vp, 0, prevVp, 0, 16)
        val cp = cos(pitch)
        fwd[0] = sin(yaw) * cp
        fwd[1] = sin(pitch)
        fwd[2] = -cos(yaw) * cp
        right[0] = cos(yaw)
        right[1] = 0f
        right[2] = sin(yaw)
        // up = right x fwd
        up[0] = right[1] * fwd[2] - right[2] * fwd[1]
        up[1] = right[2] * fwd[0] - right[0] * fwd[2]
        up[2] = right[0] * fwd[1] - right[1] * fwd[0]

        Matrix.setLookAtM(view, 0, x, y, z, x + fwd[0], y + fwd[1], z + fwd[2], 0f, 1f, 0f)
        Matrix.perspectiveM(proj, 0, fovY, aspect, near, far)
        Matrix.multiplyMM(vp, 0, proj, 0, view, 0)
        System.arraycopy(proj, 0, projJ, 0, 16)
        projJ[8] -= jx
        projJ[9] -= jy
        Matrix.multiplyMM(vpJ, 0, projJ, 0, view, 0)
        Matrix.invertM(invVp, 0, vp, 0)
        if (first) {
            System.arraycopy(vp, 0, prevVp, 0, 16)
            first = false
        }
    }

    fun tanHalfFovY(): Float = tan(Math.toRadians((fovY / 2f).toDouble())).toFloat()
}

/** Bidang frustum (6 bidang ternormalisasi) dari matriks view-proj. */
object Frustum {
    fun extract(m: FloatArray, out: FloatArray) {
        for (i in 0 until 6) {
            val sign = if (i % 2 == 0) 1f else -1f
            val row = i / 2
            val a = m[3] + sign * m[row]
            val b = m[7] + sign * m[4 + row]
            val c = m[11] + sign * m[8 + row]
            val d = m[15] + sign * m[12 + row]
            val len = sqrt(a * a + b * b + c * c)
            val inv = if (len > 0f) 1f / len else 1f
            out[i * 4] = a * inv
            out[i * 4 + 1] = b * inv
            out[i * 4 + 2] = c * inv
            out[i * 4 + 3] = d * inv
        }
    }

    /** Tes AABB. Return false kalau pasti di luar frustum. */
    fun aabbVisible(p: FloatArray, minX: Float, minY: Float, minZ: Float, maxX: Float, maxY: Float, maxZ: Float): Boolean {
        for (i in 0 until 6) {
            val nx = p[i * 4]
            val ny = p[i * 4 + 1]
            val nz = p[i * 4 + 2]
            val px = if (nx >= 0f) maxX else minX
            val py = if (ny >= 0f) maxY else minY
            val pz = if (nz >= 0f) maxZ else minZ
            if (nx * px + ny * py + nz * pz + p[i * 4 + 3] < 0f) return false
        }
        return true
    }

    fun halton(index: Int, base: Int): Float {
        var f = 1f
        var r = 0f
        var i = index
        while (i > 0) {
            f /= base
            r += f * (i % base)
            i /= base
        }
        return r
    }

    @Suppress("unused")
    fun absf(v: Float): Float = abs(v)
}
