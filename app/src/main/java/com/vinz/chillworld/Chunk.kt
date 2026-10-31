package com.vinz.chillworld

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

object WorldConst {
    const val CHUNK = 64                    // meter
    const val GRID = 65                     // vertex per sisi
    const val SKIRT_DROP = 12f
    const val VERT_COUNT = GRID * GRID + 4 * GRID
    const val VERT_FLOATS = 6               // pos(3) + normal(3)
    const val MAX_TREES = 64
    const val MAX_HOUSES = 4
    const val GRASS_BASE = 3072             // gerombol rumput per chunk pada kepadatan 100%
    const val GRASS_CAP = 3100

    fun key(cx: Int, cz: Int): Long = (cx.toLong() shl 32) xor (cz.toLong() and 0xffffffffL)
}

/** Data hasil generate di thread latar (dipakai ulang lewat pool). */
class ChunkData {
    var cx = 0
    var cz = 0
    var minY = 0f
    var maxY = 0f
    val verts: FloatBuffer = ByteBuffer
        .allocateDirect(WorldConst.VERT_COUNT * WorldConst.VERT_FLOATS * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer()
    val trees = FloatArray(WorldConst.MAX_TREES * 8)
    var treeCount = 0
    val houses = FloatArray(WorldConst.MAX_HOUSES * 8)
    var houseCount = 0
}

/** Rumput satu chunk (hanya dibuat untuk chunk yang dekat pemain). */
class GrassTile {
    val data = FloatArray(WorldConst.GRASS_CAP * 8)
    var count = 0
    var glBuf = 0
}

class GrassResult(val tile: GrassTile, val key: Long)

/** Chunk terrain 64x64 meter yang sudah ada di GPU. */
class Chunk {
    var cx = 0
    var cz = 0
    var minY = 0f
    var maxY = 0f
    var vbo = 0
    val vao = IntArray(3)
    var treeSsbo = 0
    val trees = FloatArray(WorldConst.MAX_TREES * 8)
    var treeCount = 0
    val houses = FloatArray(WorldConst.MAX_HOUSES * 8)
    var houseCount = 0
    var grass: GrassTile? = null
    var grassPending = false

    // diisi tiap frame oleh Renderer
    var lod = 0
    var distSq = 0f

    val worldX: Float get() = cx * WorldConst.CHUNK.toFloat()
    val worldZ: Float get() = cz * WorldConst.CHUNK.toFloat()

    fun createGl(idx: TerrainIndices) {
        vbo = Gl.genBuffer()
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER, WorldConst.VERT_COUNT * WorldConst.VERT_FLOATS * 4,
            null, GLES30.GL_DYNAMIC_DRAW
        )
        for (lod in 0 until 3) {
            vao[lod] = Gl.genVao()
            GLES30.glBindVertexArray(vao[lod])
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 24, 0)
            GLES30.glEnableVertexAttribArray(1)
            GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 24, 12)
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, idx.ibo[lod])
            GLES30.glBindVertexArray(0)
        }
        // buffer instance pohon untuk jalur compute (GLES 3.1+)
        treeSsbo = Gl.genBuffer()
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, treeSsbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, WorldConst.MAX_TREES * 32, null, GLES30.GL_DYNAMIC_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        Gl.check("Chunk.createGl")
    }
}

/**
 * Index buffer terrain yang dipakai SEMUA chunk (susunan grid-nya sama).
 * LOD0 stride 1 (1 m), LOD1 stride 2, LOD2 stride 4. Tiap LOD punya skirt di tepi
 * supaya tidak ada retakan antar chunk beda LOD.
 */
class TerrainIndices {
    val ibo = IntArray(3)
    val count = IntArray(3)

    fun build() {
        val g = WorldConst.GRID
        val base = g * g
        for (lod in 0 until 3) {
            val s = 1 shl lod
            val n = 64 / s
            val list = ShortArray(n * n * 6 + 4 * n * 6)
            var k = 0
            for (qz in 0 until n) {
                for (qx in 0 until n) {
                    val x = qx * s
                    val z = qz * s
                    val i00 = z * g + x
                    val i10 = z * g + x + s
                    val i01 = (z + s) * g + x
                    val i11 = (z + s) * g + x + s
                    list[k++] = i00.toShort(); list[k++] = i01.toShort(); list[k++] = i10.toShort()
                    list[k++] = i10.toShort(); list[k++] = i01.toShort(); list[k++] = i11.toShort()
                }
            }
            // skirt: utara (z=0) dan timur (x=64) satu arah putar, selatan dan barat kebalikannya
            for (edge in 0 until 4) {
                var i = 0
                while (i < 64) {
                    val i2 = i + s
                    val t0: Int
                    val t1: Int
                    val s0: Int
                    val s1: Int
                    when (edge) {
                        0 -> { t0 = i; t1 = i2; s0 = base + i; s1 = base + i2 }
                        1 -> { t0 = 64 * g + i; t1 = 64 * g + i2; s0 = base + g + i; s1 = base + g + i2 }
                        2 -> { t0 = i * g; t1 = i2 * g; s0 = base + 2 * g + i; s1 = base + 2 * g + i2 }
                        else -> { t0 = i * g + 64; t1 = i2 * g + 64; s0 = base + 3 * g + i; s1 = base + 3 * g + i2 }
                    }
                    val flip = (edge == 1 || edge == 2)
                    if (!flip) {
                        list[k++] = t0.toShort(); list[k++] = t1.toShort(); list[k++] = s0.toShort()
                        list[k++] = t1.toShort(); list[k++] = s1.toShort(); list[k++] = s0.toShort()
                    } else {
                        list[k++] = t0.toShort(); list[k++] = s0.toShort(); list[k++] = t1.toShort()
                        list[k++] = t1.toShort(); list[k++] = s0.toShort(); list[k++] = s1.toShort()
                    }
                    i += s
                }
            }
            count[lod] = k
            val bb = ByteBuffer.allocateDirect(k * 2).order(ByteOrder.nativeOrder()).asShortBuffer()
            bb.put(list, 0, k)
            bb.position(0)
            ibo[lod] = Gl.genBuffer()
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibo[lod])
            GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, k * 2, bb, GLES30.GL_STATIC_DRAW)
        }
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)
        Gl.check("TerrainIndices.build")
    }
}
