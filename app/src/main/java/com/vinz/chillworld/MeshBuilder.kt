package com.vinz.chillworld

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Mesh siap gambar (VAO sudah termasuk atribut instance kalau instanceBuf diberikan). */
class Mesh(val vao: Int, val vbo: Int, val ibo: Int, val indexCount: Int)

/**
 * Pembangun mesh sederhana. Format vertex (10 float) dipakai bareng pohon/rumput/rumah:
 * pos(3) normal(3) uv(2) info(2).
 */
class MeshBuilder {
    private var verts = FloatArray(4096)
    private var idx = ShortArray(8192)
    var vCount = 0
        private set
    var iCount = 0
        private set

    fun v(
        px: Float, py: Float, pz: Float,
        nx: Float, ny: Float, nz: Float,
        u: Float, vv: Float, info0: Float, info1: Float
    ): Int {
        if ((vCount + 1) * 10 > verts.size) verts = verts.copyOf(verts.size * 2)
        val o = vCount * 10
        verts[o] = px; verts[o + 1] = py; verts[o + 2] = pz
        verts[o + 3] = nx; verts[o + 4] = ny; verts[o + 5] = nz
        verts[o + 6] = u; verts[o + 7] = vv
        verts[o + 8] = info0; verts[o + 9] = info1
        return vCount++
    }

    fun tri(a: Int, b: Int, c: Int) {
        if (iCount + 3 > idx.size) idx = idx.copyOf(idx.size * 2)
        idx[iCount++] = a.toShort()
        idx[iCount++] = b.toShort()
        idx[iCount++] = c.toShort()
    }

    /** Dua segitiga (a,b,c) dan (a,c,d). */
    fun quad(a: Int, b: Int, c: Int, d: Int) {
        tri(a, b, c)
        tri(a, c, d)
    }

    /** Quad dua sisi (daun dan rumput tidak butuh pengaturan culling khusus). */
    fun quad2(a: Int, b: Int, c: Int, d: Int) {
        quad(a, b, c, d)
        tri(a, c, b)
        tri(a, d, c)
    }

    fun upload(instanceBuf: Int): Mesh {
        val vbo = Gl.genBuffer()
        val ibo = Gl.genBuffer()
        val vao = Gl.genVao()

        val vb = ByteBuffer.allocateDirect(vCount * 10 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        vb.put(verts, 0, vCount * 10)
        vb.position(0)
        val ib = ByteBuffer.allocateDirect(iCount * 2).order(ByteOrder.nativeOrder()).asShortBuffer()
        ib.put(idx, 0, iCount)
        ib.position(0)

        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vCount * 40, vb, GLES30.GL_STATIC_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibo)
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, iCount * 2, ib, GLES30.GL_STATIC_DRAW)

        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 40, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 40, 12)
        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(2, 2, GLES30.GL_FLOAT, false, 40, 24)
        GLES30.glEnableVertexAttribArray(3)
        GLES30.glVertexAttribPointer(3, 2, GLES30.GL_FLOAT, false, 40, 32)

        if (instanceBuf != 0) {
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, instanceBuf)
            GLES30.glEnableVertexAttribArray(4)
            GLES30.glVertexAttribPointer(4, 4, GLES30.GL_FLOAT, false, 32, 0)
            GLES30.glVertexAttribDivisor(4, 1)
            GLES30.glEnableVertexAttribArray(5)
            GLES30.glVertexAttribPointer(5, 4, GLES30.GL_FLOAT, false, 32, 16)
            GLES30.glVertexAttribDivisor(5, 1)
        }
        GLES30.glBindVertexArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        Gl.check("MeshBuilder.upload")
        return Mesh(vao, vbo, ibo, iCount)
    }
}
