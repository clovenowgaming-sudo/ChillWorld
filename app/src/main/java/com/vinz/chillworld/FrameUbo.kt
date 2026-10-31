package com.vinz.chillworld

import android.opengl.GLES30
import java.nio.FloatBuffer

/**
 * UBO "Frame" (std140), binding point 0. Urutan harus sama persis dengan blok di common.glsl.
 * Offset dihitung dalam satuan float.
 */
class FrameUbo {
    companion object {
        const val VP = 0
        const val VIEW = 16
        const val INV_VP = 32
        const val PREV_VP = 48
        const val LIGHT_VP = 64          // 4 x mat4
        const val CAM_POS = 128
        const val SUN_DIR = 132
        const val SUN_COLOR = 136
        const val MOON_DIR = 140
        const val MOON_COLOR = 144
        const val LIGHT_COLOR = 148
        const val AMB_SKY = 152
        const val AMB_GROUND = 156
        const val FOG = 160
        const val SPLITS = 164
        const val TEXEL = 168
        const val SCREEN = 172
        const val WIND = 176
        const val MISC = 180
        const val LIGHT = 184
        const val FLOATS = 188
    }

    val data = FloatArray(FLOATS)
    private val buf: FloatBuffer = Gl.floatBuf(FLOATS)
    var id = 0
        private set

    fun create() {
        id = Gl.genBuffer()
        GLES30.glBindBuffer(GLES30.GL_UNIFORM_BUFFER, id)
        GLES30.glBufferData(GLES30.GL_UNIFORM_BUFFER, FLOATS * 4, null, GLES30.GL_DYNAMIC_DRAW)
        GLES30.glBindBufferBase(GLES30.GL_UNIFORM_BUFFER, 0, id)
    }

    fun setMat(off: Int, m: FloatArray) {
        System.arraycopy(m, 0, data, off, 16)
    }

    fun setVec(off: Int, x: Float, y: Float, z: Float, w: Float) {
        data[off] = x
        data[off + 1] = y
        data[off + 2] = z
        data[off + 3] = w
    }

    fun upload() {
        buf.clear()
        buf.put(data)
        buf.position(0)
        GLES30.glBindBuffer(GLES30.GL_UNIFORM_BUFFER, id)
        GLES30.glBufferSubData(GLES30.GL_UNIFORM_BUFFER, 0, FLOATS * 4, buf)
        GLES30.glBindBufferBase(GLES30.GL_UNIFORM_BUFFER, 0, id)
    }
}
