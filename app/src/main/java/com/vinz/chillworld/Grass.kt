package com.vinz.chillworld

import android.opengl.GLES30
import kotlin.math.cos
import kotlin.math.sin

/**
 * Rumput: tiap instance = gerombol 3 kartu silang (tiap kartu 3 helai, bentuk daun dari shader).
 * Digambar tanpa face culling karena cuma satu sisi geometri.
 */
class Grass {
    private var mesh: Mesh? = null
    private var prog = 0
    private var locDist = -1

    fun build(culler: InstanceCuller) {
        val b = MeshBuilder()
        val w = 0.95f
        val h = 0.6f
        for (q in 0 until 3) {
            val ang = q * Gl.PIF / 3f
            val cx = cos(ang)
            val sz = sin(ang)
            val first = b.vCount
            for (r in 0..2) {
                val f = r / 2f
                b.v(-cx * w / 2f, f * h, -sz * w / 2f, 0f, 1f, 0f, 0f, f, 0f, f)
                b.v(cx * w / 2f, f * h, sz * w / 2f, 0f, 1f, 0f, 1f, f, 0f, f)
            }
            for (r in 0 until 2) {
                val a = first + r * 2
                b.quad(a, a + 1, a + 3, a + 2)
            }
        }
        val m = b.upload(culler.buf[InstanceCuller.GRASS])
        mesh = m
        culler.indexCount[InstanceCuller.GRASS] = m.indexCount
    }

    fun loadPrograms(sh: ShaderLoader) {
        prog = sh.program("grass.vert", "grass.frag", listOf("FAST_SHADOW"))
        locDist = GLES30.glGetUniformLocation(prog, "uGrassDist")
    }

    fun draw(culler: InstanceCuller, grassDist: Float) {
        val m = mesh ?: return
        if (prog == 0) return
        GLES30.glUseProgram(prog)
        GLES30.glUniform1f(locDist, grassDist)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glBindVertexArray(m.vao)
        culler.draw(InstanceCuller.GRASS)
        GLES30.glBindVertexArray(0)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
    }
}
