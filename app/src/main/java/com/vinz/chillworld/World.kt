package com.vinz.chillworld

import android.opengl.GLES30
import android.util.Log
import java.nio.FloatBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/** Tugas buat thread latar. kind 0 = terrain+pohon+rumah, 1 = rumput. */
class GenTask(val kind: Int, val cx: Int, val cz: Int, val prio: Int, val density: Float) : Comparable<GenTask> {
    override fun compareTo(other: GenTask): Int = prio.compareTo(other.prio)
}

/**
 * Dunia tak terbatas: chunk 64x64 m dibuat di thread latar, diunggah ke GPU maksimal
 * 1 per frame. Objek chunk di-daur ulang (pooling), jadi tidak ada alokasi di render loop.
 */
class World(private val indices: TerrainIndices, private val computeOk: Boolean) {
    val chunks = ArrayList<Chunk>()
    private val map = HashMap<Long, Chunk>()
    private val pendingKeys = HashSet<Long>()
    private val freeChunks = ArrayList<Chunk>()
    private val tasks = PriorityBlockingQueue<GenTask>(64)
    private val results = ConcurrentLinkedQueue<Any>()
    private val dataPool = ArrayBlockingQueue<ChunkData>(5)
    private val grassFree = ConcurrentLinkedQueue<GrassTile>()
    private val stage: FloatBuffer = Gl.floatBuf(WorldConst.GRASS_CAP * 8)

    @Volatile private var running = true
    private var thread: Thread? = null

    var radius = 6
        private set
    var grassRadius = 2
        private set
    var grassDensity = 0.5f
        private set
    private var lastCx = Int.MIN_VALUE
    private var lastCz = Int.MIN_VALUE
    private var force = true

    init {
        for (i in 0 until 4) dataPool.offer(ChunkData())
        val t = Thread({ workerLoop() }, "ChunkGen")
        t.priority = Thread.NORM_PRIORITY - 1
        t.isDaemon = true
        t.start()
        thread = t
    }

    /** Dipanggil saat preset berubah. */
    fun configure(newRadius: Int, newGrassRadius: Int, newDensity: Float) {
        if (newRadius != radius) force = true
        if (newGrassRadius != grassRadius || abs(newDensity - grassDensity) > 0.001f) {
            // buang semua rumput, nanti dibuat ulang sesuai kepadatan baru
            for (c in chunks) releaseGrass(c)
            force = true
        }
        radius = newRadius
        grassRadius = newGrassRadius
        grassDensity = newDensity
    }

    fun dispose() {
        running = false
        thread?.interrupt()
    }

    /** Context GL hilang: semua buffer GL tidak valid lagi, kosongkan semuanya. */
    fun onContextLost() {
        chunks.clear()
        map.clear()
        pendingKeys.clear()
        freeChunks.clear()
        grassFree.clear()
        results.clear()
        lastCx = Int.MIN_VALUE
        force = true
    }

    fun update(camX: Float, camZ: Float) {
        val cx = Math.floorDiv(floor(camX).toInt(), WorldConst.CHUNK)
        val cz = Math.floorDiv(floor(camZ).toInt(), WorldConst.CHUNK)
        if (cx != lastCx || cz != lastCz || force) {
            lastCx = cx
            lastCz = cz
            force = false
            rescan(cx, cz)
        }
        processOneResult()
    }

    private fun rescan(cx: Int, cz: Int) {
        // buang chunk yang terlalu jauh
        val maxD = (radius + 1) * (radius + 1) + 1
        var i = chunks.size - 1
        while (i >= 0) {
            val c = chunks[i]
            val dx = c.cx - cx
            val dz = c.cz - cz
            if (dx * dx + dz * dz > maxD) {
                releaseGrass(c)
                map.remove(WorldConst.key(c.cx, c.cz))
                freeChunks.add(c)
                val last = chunks.size - 1
                chunks[i] = chunks[last]
                chunks.removeAt(last)
            }
            i--
        }
        // minta chunk baru (yang terdekat dikerjakan duluan)
        for (dz in -radius..radius) {
            for (dx in -radius..radius) {
                val d2 = dx * dx + dz * dz
                if (d2 > radius * radius + radius) continue
                val k = WorldConst.key(cx + dx, cz + dz)
                if (map.containsKey(k) || pendingKeys.contains(k)) continue
                pendingKeys.add(k)
                tasks.add(GenTask(0, cx + dx, cz + dz, d2, 0f))
            }
        }
        // rumput hanya untuk chunk dekat
        for (n in 0 until chunks.size) {
            val c = chunks[n]
            val dx = c.cx - cx
            val dz = c.cz - cz
            val cheb = maxOf(abs(dx), abs(dz))
            if (cheb <= grassRadius) {
                if (c.grass == null && !c.grassPending && grassDensity > 0f) {
                    c.grassPending = true
                    tasks.add(GenTask(1, c.cx, c.cz, dx * dx + dz * dz - 50, grassDensity))
                }
            } else if (cheb > grassRadius + 1 && c.grass != null) {
                releaseGrass(c)
            }
        }
    }

    private fun releaseGrass(c: Chunk) {
        val g = c.grass
        if (g != null) {
            grassFree.add(g)
            c.grass = null
        }
    }

    private fun processOneResult() {
        val r = results.poll() ?: return
        if (r is ChunkData) uploadChunk(r) else if (r is GrassResult) attachGrass(r)
    }

    private fun uploadChunk(d: ChunkData) {
        val key = WorldConst.key(d.cx, d.cz)
        pendingKeys.remove(key)
        val dx = d.cx - lastCx
        val dz = d.cz - lastCz
        if (dx * dx + dz * dz > (radius + 1) * (radius + 1) + 1 || map.containsKey(key)) {
            dataPool.offer(d)
            return
        }
        val c: Chunk
        if (freeChunks.isEmpty()) {
            c = Chunk()
            c.createGl(indices)
        } else {
            c = freeChunks.removeAt(freeChunks.size - 1)
        }
        c.cx = d.cx
        c.cz = d.cz
        c.minY = d.minY
        c.maxY = d.maxY
        c.grass = null
        c.grassPending = false

        d.verts.position(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, c.vbo)
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, WorldConst.VERT_COUNT * WorldConst.VERT_FLOATS * 4, d.verts)

        c.treeCount = d.treeCount
        System.arraycopy(d.trees, 0, c.trees, 0, d.treeCount * 8)
        c.houseCount = d.houseCount
        System.arraycopy(d.houses, 0, c.houses, 0, d.houseCount * 8)
        if (computeOk && c.treeCount > 0) {
            stage.clear()
            stage.put(c.trees, 0, c.treeCount * 8)
            stage.position(0)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, c.treeSsbo)
            GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, c.treeCount * 32, stage)
        }
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        map[key] = c
        chunks.add(c)
        dataPool.offer(d)
    }

    private fun attachGrass(r: GrassResult) {
        val c = map[r.key]
        if (c == null) {
            grassFree.add(r.tile)
            return
        }
        c.grassPending = false
        val dx = c.cx - lastCx
        val dz = c.cz - lastCz
        if (maxOf(abs(dx), abs(dz)) > grassRadius + 1 || c.grass != null) {
            grassFree.add(r.tile)
            return
        }
        val t = r.tile
        if (computeOk) {
            if (t.glBuf == 0) {
                t.glBuf = Gl.genBuffer()
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, t.glBuf)
                GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, WorldConst.GRASS_CAP * 32, null, GLES30.GL_DYNAMIC_DRAW)
            }
            stage.clear()
            stage.put(t.data, 0, t.count * 8)
            stage.position(0)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, t.glBuf)
            GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, t.count * 32, stage)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        }
        c.grass = t
    }

    // ------------------------------------------------------------ thread latar
    private fun workerLoop() {
        val gen = ChunkGenerator()
        while (running) {
            var polled: GenTask? = null
            try {
                polled = tasks.poll(200, TimeUnit.MILLISECONDS)
            } catch (e: InterruptedException) {
                polled = null
            }
            val t: GenTask = polled ?: continue
            try {
                if (t.kind == 0) {
                    var d: ChunkData? = null
                    while (running && d == null) d = dataPool.poll(100, TimeUnit.MILLISECONDS)
                    if (d == null) break
                    gen.generateChunk(t.cx, t.cz, d)
                    results.add(d)
                } else {
                    val tile = grassFree.poll() ?: GrassTile()
                    gen.generateGrass(t.cx, t.cz, t.density, tile)
                    results.add(GrassResult(tile, WorldConst.key(t.cx, t.cz)))
                }
            } catch (e: InterruptedException) {
                break
            } catch (e: Exception) {
                Log.e(Gl.TAG, "Generate chunk gagal: " + e)
            }
        }
    }
}

/** Pembuat isi chunk. Hanya dipakai satu thread (thread latar), jadi aman pakai scratch bersama. */
class ChunkGenerator {
    private val grid = FloatArray(67 * 67)
    private val houseCache = HashMap<Long, FloatArray?>()
    private val near = FloatArray(16)
    private var nearCount = 0
    private var nx = 0f
    private var ny = 1f
    private var nz = 0f

    private fun h(gx: Int, gz: Int): Float = grid[(gz + 1) * 67 + (gx + 1)]

    private fun fillGrid(cx: Int, cz: Int) {
        val x0 = cx * 64f
        val z0 = cz * 64f
        for (gz in -1..65) {
            for (gx in -1..65) {
                grid[(gz + 1) * 67 + (gx + 1)] = Terrain.heightAt(x0 + gx, z0 + gz)
            }
        }
    }

    private fun normalAt(gx: Int, gz: Int) {
        val dx = (h(gx + 1, gz) - h(gx - 1, gz)) * 0.5f
        val dz = (h(gx, gz + 1) - h(gx, gz - 1)) * 0.5f
        val l = sqrt(dx * dx + 1f + dz * dz)
        nx = -dx / l
        ny = 1f / l
        nz = -dz / l
    }

    private fun sampleH(lx: Float, lz: Float): Float {
        val gx = floor(lx).toInt().coerceIn(0, 63)
        val gz = floor(lz).toInt().coerceIn(0, 63)
        val fx = (lx - gx).coerceIn(0f, 1f)
        val fz = (lz - gz).coerceIn(0f, 1f)
        val a = h(gx, gz) + (h(gx + 1, gz) - h(gx, gz)) * fx
        val b = h(gx, gz + 1) + (h(gx + 1, gz + 1) - h(gx, gz + 1)) * fx
        return a + (b - a) * fz
    }

    private fun slopeNy(lx: Float, lz: Float): Float {
        normalAt(lx.toInt().coerceIn(0, 64), lz.toInt().coerceIn(0, 64))
        return ny
    }

    private fun putVert(b: FloatBuffer, gx: Int, gz: Int, drop: Float) {
        normalAt(gx, gz)
        b.put(gx.toFloat()); b.put(h(gx, gz) - drop); b.put(gz.toFloat())
        b.put(nx); b.put(ny); b.put(nz)
    }

    /** Lokasi rumah untuk sel 128x128 m (jarak antar rumah dijamin > 40 m). null = tidak ada. */
    private fun houseSite(ci: Int, cj: Int): FloatArray? {
        val key = WorldConst.key(ci, cj)
        if (houseCache.containsKey(key)) return houseCache[key]
        var res: FloatArray? = null
        if (Noise.hash(ci, cj, 91) < 0.38f) {
            for (t in 0 until 10) {
                val px = ci * 128f + 26f + Noise.hash(ci, cj, 100 + t * 2) * 76f
                val pz = cj * 128f + 26f + Noise.hash(ci, cj, 101 + t * 2) * 76f
                val hc = Terrain.heightAt(px, pz)
                if (hc < 4f || hc > 85f) continue
                var mn = hc
                var mx = hc
                for (k in 0 until 8) {
                    val ang = k * 0.785398f
                    val hh = Terrain.heightAt(px + cos(ang) * 5.5f, pz + sin(ang) * 5.5f)
                    if (hh < mn) mn = hh
                    if (hh > mx) mx = hh
                }
                if (mx - mn > 1.6f) continue
                res = floatArrayOf(px, mn, pz, Noise.hash(ci, cj, 7) * 6.2831853f, Noise.hash(ci, cj, 8))
                break
            }
        }
        houseCache[key] = res
        return res
    }

    /** Kumpulkan rumah yang dekat chunk (margin 12 m) ke array near[]. */
    private fun collectHouses(cx: Int, cz: Int) {
        nearCount = 0
        val x0 = cx * 64f
        val z0 = cz * 64f
        val ia = Math.floorDiv((x0 - 12f).toInt(), 128)
        val ib = Math.floorDiv((x0 + 76f).toInt(), 128)
        val ja = Math.floorDiv((z0 - 12f).toInt(), 128)
        val jb = Math.floorDiv((z0 + 76f).toInt(), 128)
        for (j in ja..jb) {
            for (i in ia..ib) {
                val s = houseSite(i, j) ?: continue
                if (nearCount < 4) {
                    near[nearCount * 4] = s[0]
                    near[nearCount * 4 + 1] = s[2]
                    nearCount++
                }
            }
        }
    }

    private fun nearHouseSq(wx: Float, wz: Float): Float {
        var best = 1e9f
        for (i in 0 until nearCount) {
            val dx = wx - near[i * 4]
            val dz = wz - near[i * 4 + 1]
            val d = dx * dx + dz * dz
            if (d < best) best = d
        }
        return best
    }

    fun generateChunk(cx: Int, cz: Int, d: ChunkData) {
        fillGrid(cx, cz)
        collectHouses(cx, cz)
        d.cx = cx
        d.cz = cz
        val b = d.verts
        b.clear()
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (gz in 0..64) {
            for (gx in 0..64) {
                putVert(b, gx, gz, 0f)
                val hh = h(gx, gz)
                if (hh < minY) minY = hh
                if (hh > maxY) maxY = hh
            }
        }
        // skirt: utara, selatan, barat, timur (urutan harus sama dengan TerrainIndices)
        val sd = WorldConst.SKIRT_DROP
        for (x in 0..64) putVert(b, x, 0, sd)
        for (x in 0..64) putVert(b, x, 64, sd)
        for (z in 0..64) putVert(b, 0, z, sd)
        for (z in 0..64) putVert(b, 64, z, sd)
        b.flip()
        d.minY = minY - sd
        d.maxY = maxY + 24f   // tinggi pohon/rumah

        val x0 = cx * 64f
        val z0 = cz * 64f

        // ---- pohon: 1 kandidat per sel 8x8 m, kepadatan ikut noise hutan
        var tc = 0
        for (j in 0 until 8) {
            for (i in 0 until 8) {
                val gi = cx * 8 + i
                val gj = cz * 8 + j
                val lx = i * 8f + Noise.hash(gi, gj, 1) * 8f
                val lz = j * 8f + Noise.hash(gi, gj, 2) * 8f
                val wx = x0 + lx
                val wz = z0 + lz
                val forest = Noise.fbm(wx * 0.006f + 40f, wz * 0.006f + 11f, 3) * 0.5f + 0.5f
                val dens = Gl.smooth(0.38f, 0.60f, forest)
                if (Noise.hash(gi, gj, 3) > dens * 0.9f + 0.04f) continue
                val hh = sampleH(lx, lz)
                if (hh > 190f) continue
                if (Noise.hash(gi, gj, 4) > 1f - Gl.smooth(110f, 190f, hh)) continue
                if (slopeNy(lx, lz) < 0.82f) continue
                if (nearHouseSq(wx, wz) < 144f) continue
                val pineChance = Gl.smooth(60f, 130f, hh) * 0.8f + 0.15f
                val species = if (Noise.hash(gi, gj, 5) < pineChance) 0f else 1f
                val sc = 0.75f + 0.7f * Noise.hash(gi, gj, 6)
                val o = tc * 8
                d.trees[o] = wx
                d.trees[o + 1] = hh - 0.15f
                d.trees[o + 2] = wz
                d.trees[o + 3] = sc
                d.trees[o + 4] = Noise.hash(gi, gj, 7) * 6.2831853f
                d.trees[o + 5] = species
                d.trees[o + 6] = Noise.hash(gi, gj, 8)
                d.trees[o + 7] = (if (species < 0.5f) 8.5f else 7.5f) * sc
                tc++
            }
        }
        d.treeCount = tc

        // ---- rumah yang titik pusatnya jatuh di chunk ini
        var hc = 0
        val ia = Math.floorDiv(cx * 64, 128)
        val ja = Math.floorDiv(cz * 64, 128)
        for (j in ja..ja) {
            for (i in ia..ia) {
                val s = houseSite(i, j) ?: continue
                if (s[0] >= x0 && s[0] < x0 + 64f && s[2] >= z0 && s[2] < z0 + 64f && hc < WorldConst.MAX_HOUSES) {
                    val o = hc * 8
                    d.houses[o] = s[0]
                    d.houses[o + 1] = s[1]
                    d.houses[o + 2] = s[2]
                    d.houses[o + 3] = 1f
                    d.houses[o + 4] = s[3]
                    d.houses[o + 5] = 0f
                    d.houses[o + 6] = s[4]
                    d.houses[o + 7] = 9f
                    hc++
                }
            }
        }
        d.houseCount = hc
    }

    fun generateGrass(cx: Int, cz: Int, density: Float, tile: GrassTile) {
        fillGrid(cx, cz)
        collectHouses(cx, cz)
        val n = floor(sqrt(WorldConst.GRASS_BASE * density)).toInt().coerceAtLeast(4)
        val x0 = cx * 64f
        val z0 = cz * 64f
        val a = tile.data
        var cnt = 0
        for (j in 0 until n) {
            for (i in 0 until n) {
                val gi = cx * 977 + i
                val gj = cz * 977 + j
                val lx = (i + Noise.hash(gi, gj, 11)) / n * 64f
                val lz = (j + Noise.hash(gi, gj, 12)) / n * 64f
                val wx = x0 + lx
                val wz = z0 + lz
                val hh = sampleH(lx, lz)
                if (hh > 130f) continue
                if (slopeNy(lx, lz) < 0.74f) continue
                if (Noise.fbm(wx * 0.02f, wz * 0.02f, 2) < -0.35f) continue
                if (nearHouseSq(wx, wz) < 64f) continue
                if (cnt >= WorldConst.GRASS_CAP) break
                val sc = 0.75f + 0.8f * Noise.hash(gi, gj, 13)
                val o = cnt * 8
                a[o] = wx
                a[o + 1] = hh - 0.02f
                a[o + 2] = wz
                a[o + 3] = sc
                a[o + 4] = Noise.hash(gi, gj, 14) * 6.2831853f
                a[o + 5] = 0f
                a[o + 6] = Noise.hash(gi, gj, 15)
                a[o + 7] = 1.3f * sc
                cnt++
            }
        }
        tile.count = cnt
    }
}
