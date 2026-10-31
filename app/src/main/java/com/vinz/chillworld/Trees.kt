package com.vinz.chillworld

import android.opengl.GLES30
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pohon prosedural: pinus + pohon daun lebar. Daun = kartu quad beralpha (alpha dibuat di shader),
 * digambar dengan instancing. Ada 3 level: mesh penuh (LOD0), mesh sederhana (LOD1), billboard.
 */
class Trees {
    private val meshes = arrayOfNulls<Mesh>(5)
    private var pMain = 0
    private var pShadow = 0
    private var pDepth = 0
    private var pBill = 0
    private var svpShadow = -1

    fun build(culler: InstanceCuller) {
        for (cls in 0..4) {
            val b = MeshBuilder()
            when (cls) {
                InstanceCuller.PINE0 -> pine(b, 0)
                InstanceCuller.PINE1 -> pine(b, 1)
                InstanceCuller.BROAD0 -> broad(b, 0)
                InstanceCuller.BROAD1 -> broad(b, 1)
                else -> billboard(b)
            }
            val m = b.upload(culler.buf[cls])
            meshes[cls] = m
            culler.indexCount[cls] = m.indexCount
        }
    }

    fun loadPrograms(sh: ShaderLoader) {
        pMain = sh.program("tree.vert", "tree.frag")
        pShadow = sh.program("tree.vert", "tree.frag", listOf("SHADOW"))
        pDepth = sh.program("tree.vert", "tree.frag", listOf("DEPTH"))
        pBill = sh.program("billboard.vert", "billboard.frag")
        svpShadow = GLES30.glGetUniformLocation(pShadow, "uShadowVP")
    }

    fun draw(culler: InstanceCuller, cls: Int, pass: Pass, shadowVp: FloatArray?) {
        val m = meshes[cls] ?: return
        if (cls == InstanceCuller.BILL && pass != Pass.MAIN) return
        val prog = if (cls == InstanceCuller.BILL) pBill else when (pass) {
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
        culler.draw(cls)
        GLES30.glBindVertexArray(0)
    }

    // ------------------------------------------------------------ pembangun geometri

    /** Tabung meruncing dari p0 ke p1. flex0/flex1 = kelenturan terhadap angin di ujung-ujungnya. */
    private fun tube(
        b: MeshBuilder, x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float,
        r0: Float, r1: Float, sides: Int, segs: Int, flex0: Float, flex1: Float
    ) {
        var dx = x1 - x0
        var dy = y1 - y0
        var dz = z1 - z0
        val len = sqrt(dx * dx + dy * dy + dz * dz)
        dx /= len; dy /= len; dz /= len
        val rx: Float
        val ry: Float
        val rz: Float
        if (abs(dy) < 0.9f) { rx = 0f; ry = 1f; rz = 0f } else { rx = 1f; ry = 0f; rz = 0f }
        // t1 = normalize(cross(d, ref)), t2 = cross(d, t1)
        var t1x = dy * rz - dz * ry
        var t1y = dz * rx - dx * rz
        var t1z = dx * ry - dy * rx
        val tl = sqrt(t1x * t1x + t1y * t1y + t1z * t1z)
        t1x /= tl; t1y /= tl; t1z /= tl
        val t2x = dy * t1z - dz * t1y
        val t2y = dz * t1x - dx * t1z
        val t2z = dx * t1y - dy * t1x

        val first = b.vCount
        for (s in 0..segs) {
            val f = s.toFloat() / segs
            val cx = x0 + dx * len * f
            val cy = y0 + dy * len * f
            val cz = z0 + dz * len * f
            val r = r0 + (r1 - r0) * f
            val flex = (flex0 + (flex1 - flex0) * f).coerceIn(0f, 1f)
            for (k in 0..sides) {
                val a = k.toFloat() / sides * 2f * Gl.PIF
                val c = cos(a)
                val sn = sin(a)
                val nx = t1x * c + t2x * sn
                val ny = t1y * c + t2y * sn
                val nz = t1z * c + t2z * sn
                b.v(cx + nx * r, cy + ny * r, cz + nz * r, nx, ny, nz, k.toFloat() / sides * 1.5f, f * len / 2f, 0f, flex)
            }
        }
        val row = sides + 1
        for (s in 0 until segs) {
            for (k in 0 until sides) {
                val a = first + s * row + k
                b.quad(a, a + 1, a + row + 1, a + row)
            }
        }
    }

    /** Kartu daun dari titik dalam (i) ke titik luar (o), melebar dari wIn ke wOut. */
    private fun card(
        b: MeshBuilder, ix: Float, iy: Float, iz: Float, ox: Float, oy: Float, oz: Float,
        wIn: Float, wOut: Float, nx0: Float, ny0: Float, nz0: Float, fIn: Float, fOut: Float
    ) {
        val dx = ox - ix
        val dz = oz - iz
        var sx = dz
        var sz = -dx
        val l = sqrt(sx * sx + sz * sz)
        if (l < 0.0001f) { sx = 1f; sz = 0f } else { sx /= l; sz /= l }
        val nl = sqrt(nx0 * nx0 + ny0 * ny0 + nz0 * nz0)
        val nx = nx0 / nl
        val ny = ny0 / nl
        val nz = nz0 / nl
        val a = b.v(ix - sx * wIn / 2f, iy, iz - sz * wIn / 2f, nx, ny, nz, 0f, 0f, 1f, fIn.coerceIn(0f, 1f))
        val bb = b.v(ix + sx * wIn / 2f, iy, iz + sz * wIn / 2f, nx, ny, nz, 1f, 0f, 1f, fIn.coerceIn(0f, 1f))
        val c = b.v(ox + sx * wOut / 2f, oy, oz + sz * wOut / 2f, nx, ny, nz, 1f, 1f, 1f, fOut.coerceIn(0f, 1f))
        val d = b.v(ox - sx * wOut / 2f, oy, oz - sz * wOut / 2f, nx, ny, nz, 0f, 1f, 1f, fOut.coerceIn(0f, 1f))
        b.quad2(a, bb, c, d)
    }

    private fun pine(b: MeshBuilder, lod: Int) {
        val trunkH = 14f
        tube(b, 0f, 0f, 0f, 0f, trunkH, 0f, 0.38f, 0.07f, if (lod == 0) 8 else 5, if (lod == 0) 4 else 1, 0f, 1f)
        val tiers = if (lod == 0) 7 else 4
        val cards = if (lod == 0) 6 else 3
        val widthMul = if (lod == 0) 1f else 1.5f
        for (t in 0 until tiers) {
            val f = t.toFloat() / (tiers - 1)
            val y = 2.2f + f * (trunkH - 3.2f)
            val reach = (1f - f * 0.85f) * 3.6f + 0.4f
            for (k in 0 until cards) {
                val ang = k.toFloat() / cards * 2f * Gl.PIF + t * 0.7f
                val cx = cos(ang)
                val sz = sin(ang)
                val oy = y + 0.5f - reach * 0.45f
                card(
                    b, 0.15f * cx, y + 0.5f, 0.15f * sz, reach * cx, oy, reach * sz,
                    0.5f * widthMul, reach * 0.95f * widthMul, cx * 0.6f, 0.8f, sz * 0.6f,
                    (y + 0.5f) / trunkH, oy / trunkH
                )
            }
        }
    }

    private fun broad(b: MeshBuilder, lod: Int) {
        val sides = if (lod == 0) 8 else 5
        tube(b, 0f, 0f, 0f, 0f, 5.0f, 0f, 0.34f, 0.2f, sides, if (lod == 0) 3 else 1, 0f, 0.45f)
        if (lod == 0) {
            tube(b, 0f, 3.6f, 0f, 1.6f, 6.9f, 0.6f, 0.16f, 0.05f, 5, 2, 0.4f, 0.8f)
            tube(b, 0f, 3.9f, 0f, -1.4f, 6.6f, -1.0f, 0.15f, 0.05f, 5, 2, 0.4f, 0.8f)
            tube(b, 0f, 4.2f, 0f, 0.2f, 7.4f, -1.5f, 0.14f, 0.05f, 5, 2, 0.4f, 0.85f)
        }
        val rnd = java.util.Random(1234L)
        val cards = if (lod == 0) 18 else 8
        val size = if (lod == 0) 3.6f else 5.0f
        for (i in 0 until cards) {
            var dx = rnd.nextFloat() * 2f - 1f
            var dy = rnd.nextFloat() * 1.4f - 0.4f
            var dz = rnd.nextFloat() * 2f - 1f
            val dl = sqrt(dx * dx + dy * dy + dz * dz) + 0.0001f
            dx /= dl; dy /= dl; dz /= dl
            val dist = 2.4f * (0.5f + 0.5f * rnd.nextFloat())
            val cx = dx * dist
            val cy = 6.6f + dy * dist
            val cz = dz * dist
            // normal kartu = arah radial agak ke atas
            var nx = dx * 0.6f
            var ny = dy * 0.6f + 0.4f
            var nz = dz * 0.6f
            val nl = sqrt(nx * nx + ny * ny + nz * nz) + 0.0001f
            nx /= nl; ny /= nl; nz /= nl
            // sumbu u: acak tegak lurus normal
            var ux = rnd.nextFloat() - 0.5f
            var uy = rnd.nextFloat() - 0.5f
            var uz = rnd.nextFloat() - 0.5f
            val dp = ux * nx + uy * ny + uz * nz
            ux -= nx * dp; uy -= ny * dp; uz -= nz * dp
            val ul = sqrt(ux * ux + uy * uy + uz * uz) + 0.0001f
            ux /= ul; uy /= ul; uz /= ul
            // sumbu v = n x u
            val vx = ny * uz - nz * uy
            val vy = nz * ux - nx * uz
            val vz = nx * uy - ny * ux
            val h = size / 2f
            val flex = (cy / 8f).coerceIn(0.4f, 1f)
            val a = b.v(cx - ux * h - vx * h, cy - uy * h - vy * h, cz - uz * h - vz * h, nx, ny, nz, 0f, 0f, 1f, flex)
            val bb = b.v(cx + ux * h - vx * h, cy + uy * h - vy * h, cz + uz * h - vz * h, nx, ny, nz, 1f, 0f, 1f, flex)
            val c = b.v(cx + ux * h + vx * h, cy + uy * h + vy * h, cz + uz * h + vz * h, nx, ny, nz, 1f, 1f, 1f, flex)
            val d = b.v(cx - ux * h + vx * h, cy - uy * h + vy * h, cz - uz * h + vz * h, nx, ny, nz, 0f, 1f, 1f, flex)
            b.quad2(a, bb, c, d)
        }
    }

    private fun billboard(b: MeshBuilder) {
        val a = b.v(-1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f)
        val bb = b.v(1f, 0f, 0f, 0f, 0f, 1f, 1f, 0f, 0f, 0f)
        val c = b.v(1f, 1f, 0f, 0f, 0f, 1f, 1f, 1f, 0f, 0f)
        val d = b.v(-1f, 1f, 0f, 0f, 0f, 1f, 0f, 1f, 0f, 0f)
        b.quad2(a, bb, c, d)
    }
}
