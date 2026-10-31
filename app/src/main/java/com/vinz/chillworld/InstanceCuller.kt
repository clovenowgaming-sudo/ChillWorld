package com.vinz.chillworld

import android.opengl.GLES30
import android.opengl.GLES31
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import kotlin.math.sqrt

/**
 * Culling instance pohon + rumput. Hasil tiap kelas dikumpulkan ke SATU buffer,
 * jadi tiap kelas cukup 1 draw call.
 * - GLES 3.1/3.2: compute shader -> SSBO + glDrawElementsIndirect
 * - GLES 3.0: culling di CPU -> glBufferSubData + glDrawElementsInstanced
 * Kedua jalur memakai aturan tes yang sama (jarak + bola vs frustum) -> tampilan sama.
 */
class InstanceCuller(private val caps: GLCaps) {
    companion object {
        const val PINE0 = 0
        const val PINE1 = 1
        const val BROAD0 = 2
        const val BROAD1 = 3
        const val BILL = 4
        const val GRASS = 5
        const val COUNT = 6
        const val CAP_TREE = 20000
        const val CAP_GRASS = 60000
    }

    val buf = IntArray(COUNT)
    val cmd = IntArray(COUNT)
    val indexCount = IntArray(COUNT)
    var useGpu = false
        private set

    private val cpuCount = IntArray(COUNT)
    private val stage = arrayOfNulls<FloatBuffer>(COUNT)
    private val minD = FloatArray(COUNT)
    private val maxD = FloatArray(COUNT)
    private val planes = FloatArray(24)
    private var camX = 0f
    private var camY = 0f
    private var camZ = 0f
    private var prog = 0
    private var locRange = -1
    private var locCount = -1
    private val cmdStage: IntBuffer = ByteBuffer.allocateDirect(20).order(ByteOrder.nativeOrder()).asIntBuffer()

    private fun cap(cls: Int): Int = if (cls == GRASS) CAP_GRASS else CAP_TREE

    fun init(shaders: ShaderLoader) {
        useGpu = caps.computeOk
        if (useGpu) {
            prog = shaders.compute("cull.comp")
            if (prog == 0) {
                Log.w(Gl.TAG, "Compute shader gagal, pakai culling CPU")
                useGpu = false
            }
        }
        for (i in 0 until COUNT) {
            buf[i] = Gl.genBuffer()
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buf[i])
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, cap(i) * 32, null, GLES30.GL_DYNAMIC_COPY)
            if (useGpu) {
                cmd[i] = Gl.genBuffer()
                GLES30.glBindBuffer(GLES31.GL_DRAW_INDIRECT_BUFFER, cmd[i])
                GLES30.glBufferData(GLES31.GL_DRAW_INDIRECT_BUFFER, 20, null, GLES30.GL_DYNAMIC_DRAW)
            } else {
                stage[i] = Gl.floatBuf(cap(i) * 8)
            }
        }
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        if (useGpu) {
            GLES30.glBindBuffer(GLES31.GL_DRAW_INDIRECT_BUFFER, 0)
            locRange = GLES30.glGetUniformLocation(prog, "uRange")
            locCount = GLES30.glGetUniformLocation(prog, "uCount")
        }
        Log.i(Gl.TAG, "Culling instance: " + (if (useGpu) "GPU compute" else "CPU"))
        Gl.check("InstanceCuller.init")
    }

    fun setRanges(l0: Float, l1: Float, maxTree: Float, grassDist: Float) {
        minD[PINE0] = 0f; maxD[PINE0] = l0
        minD[BROAD0] = 0f; maxD[BROAD0] = l0
        minD[PINE1] = l0; maxD[PINE1] = l1
        minD[BROAD1] = l0; maxD[BROAD1] = l1
        minD[BILL] = l1; maxD[BILL] = maxTree
        minD[GRASS] = 0f; maxD[GRASS] = grassDist
    }

    fun beginFrame(vp: FloatArray, cx: Float, cy: Float, cz: Float) {
        Frustum.extract(vp, planes)
        camX = cx; camY = cy; camZ = cz
        if (useGpu) {
            for (i in 0 until COUNT) {
                cmdStage.clear()
                cmdStage.put(indexCount[i]).put(0).put(0).put(0).put(0)
                cmdStage.position(0)
                GLES30.glBindBuffer(GLES31.GL_DRAW_INDIRECT_BUFFER, cmd[i])
                GLES30.glBufferSubData(GLES31.GL_DRAW_INDIRECT_BUFFER, 0, 20, cmdStage)
            }
            GLES30.glUseProgram(prog)
            val lp = GLES30.glGetUniformLocation(prog, "uPlanes")
            GLES30.glUniform4fv(lp, 6, planes, 0)
            val lc = GLES30.glGetUniformLocation(prog, "uCam")
            GLES30.glUniform4f(lc, cx, cy, cz, 0f)
        } else {
            for (i in 0 until COUNT) {
                cpuCount[i] = 0
                stage[i]?.clear()
            }
        }
    }

    private fun filterOf(cls: Int): Float = when (cls) {
        PINE0, PINE1 -> 0f
        BROAD0, BROAD1 -> 1f
        else -> -1f
    }

    /** Tambahkan pohon satu chunk. chunkDist = jarak kamera ke pusat chunk (XZ). */
    fun addTrees(c: Chunk, chunkDist: Float) {
        if (c.treeCount == 0) return
        if (useGpu) {
            for (cls in PINE0..BILL) {
                if (chunkDist - 46f < maxD[cls] && chunkDist + 46f >= minD[cls]) {
                    dispatch(cls, c.treeSsbo, c.treeCount)
                }
            }
        } else {
            val a = c.trees
            for (i in 0 until c.treeCount) {
                val o = i * 8
                val r = a[o + 7]
                val d = testInstance(a[o], a[o + 1], a[o + 2], r)
                if (d < 0f) continue
                val broad = a[o + 5] > 0.5f
                val cls = if (d < maxD[PINE0]) {
                    if (broad) BROAD0 else PINE0
                } else if (d < maxD[PINE1]) {
                    if (broad) BROAD1 else PINE1
                } else if (d < maxD[BILL]) {
                    BILL
                } else {
                    continue
                }
                push(cls, a, o)
            }
        }
    }

    fun addGrass(t: GrassTile, chunkDist: Float) {
        if (t.count == 0) return
        if (chunkDist - 46f >= maxD[GRASS]) return
        if (useGpu) {
            if (t.glBuf != 0) dispatch(GRASS, t.glBuf, t.count)
        } else {
            val a = t.data
            for (i in 0 until t.count) {
                val o = i * 8
                val d = testInstance(a[o], a[o + 1], a[o + 2], a[o + 7])
                if (d < 0f || d >= maxD[GRASS]) continue
                push(GRASS, a, o)
            }
        }
    }

    /** Return jarak kalau lolos tes frustum, -1 kalau terbuang. */
    private fun testInstance(x: Float, y: Float, z: Float, r: Float): Float {
        val cy = y + r * 0.5f
        for (k in 0 until 6) {
            val p = k * 4
            if (planes[p] * x + planes[p + 1] * cy + planes[p + 2] * z + planes[p + 3] < -r) return -1f
        }
        val dx = x - camX
        val dy = cy - camY
        val dz = z - camZ
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun push(cls: Int, a: FloatArray, o: Int) {
        if (cpuCount[cls] >= cap(cls)) return
        stage[cls]!!.put(a, o, 8)
        cpuCount[cls]++
    }

    private fun dispatch(cls: Int, src: Int, n: Int) {
        GLES30.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 0, src)
        GLES30.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 1, buf[cls])
        GLES30.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 2, cmd[cls])
        GLES30.glUniform4f(locRange, minD[cls], maxD[cls], filterOf(cls), cap(cls).toFloat())
        GLES30.glUniform1ui(locCount, n)
        GLES31.glDispatchCompute((n + 63) / 64, 1, 1)
    }

    fun endFrame() {
        if (useGpu) {
            GLES31.glMemoryBarrier(GLES31.GL_COMMAND_BARRIER_BIT or GLES31.GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT)
            GLES30.glUseProgram(0)
        } else {
            for (i in 0 until COUNT) {
                val n = cpuCount[i]
                if (n == 0) continue
                val s = stage[i]!!
                s.position(0)
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buf[i])
                GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, n * 32, s)
            }
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        }
        Gl.check("InstanceCuller.endFrame")
    }

    /** VAO kelas sudah harus di-bind pemanggil. */
    fun draw(cls: Int) {
        if (useGpu) {
            GLES30.glBindBuffer(GLES31.GL_DRAW_INDIRECT_BUFFER, cmd[cls])
            GLES31.glDrawElementsIndirect(GLES30.GL_TRIANGLES, GLES30.GL_UNSIGNED_SHORT, 0)
        } else if (cpuCount[cls] > 0) {
            GLES30.glDrawElementsInstanced(GLES30.GL_TRIANGLES, indexCount[cls], GLES30.GL_UNSIGNED_SHORT, 0, cpuCount[cls])
        }
    }

    /** Jumlah instance terlihat (hanya akurat di jalur CPU, buat statistik). */
    fun cpuVisible(cls: Int): Int = cpuCount[cls]
}
