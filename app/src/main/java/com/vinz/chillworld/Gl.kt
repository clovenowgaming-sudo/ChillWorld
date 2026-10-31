package com.vinz.chillworld

import android.opengl.GLES30
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/** Pass render: utama, bayangan, atau depth pre-pass. */
enum class Pass { MAIN, SHADOW, DEPTH }

/** Helper GL kecil-kecilan. Semua dipanggil dari thread GL. */
object Gl {
    const val TAG = "ChillWorld"
    const val PIF = 3.14159265f
    private val tmp = IntArray(1)

    /** Cek glGetError; cuma aktif di build debug (BuildConfig.GL_DEBUG). */
    fun check(where: String) {
        if (!BuildConfig.GL_DEBUG) return
        var err = GLES30.glGetError()
        while (err != GLES30.GL_NO_ERROR) {
            Log.e(TAG, "GL error 0x" + Integer.toHexString(err) + " di " + where)
            err = GLES30.glGetError()
        }
    }

    fun genBuffer(): Int { GLES30.glGenBuffers(1, tmp, 0); return tmp[0] }
    fun genVao(): Int { GLES30.glGenVertexArrays(1, tmp, 0); return tmp[0] }
    fun genTexture(): Int { GLES30.glGenTextures(1, tmp, 0); return tmp[0] }
    fun genFbo(): Int { GLES30.glGenFramebuffers(1, tmp, 0); return tmp[0] }

    fun delTexture(id: Int) { if (id != 0) { tmp[0] = id; GLES30.glDeleteTextures(1, tmp, 0) } }
    fun delFbo(id: Int) { if (id != 0) { tmp[0] = id; GLES30.glDeleteFramebuffers(1, tmp, 0) } }
    fun delBuffer(id: Int) { if (id != 0) { tmp[0] = id; GLES30.glDeleteBuffers(1, tmp, 0) } }

    fun floatBuf(n: Int): FloatBuffer =
        ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

    fun smooth(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
