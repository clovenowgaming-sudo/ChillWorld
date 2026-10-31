package com.vinz.chillworld

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLES30
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Hasil load: dua texture array (8 layer: grass, ground, rock, snow, bark, planks, roof, plaster). */
class TextureSet(
    val albedo: Int,
    val normalRough: Int,
    val size: Int,
    val fromFiles: Int,
    val fallback: Int
)

object TextureLoader {
    val MATERIALS = arrayOf("grass", "ground", "rock", "snow", "bark", "planks", "roof", "plaster")
    private val FALLBACK_COLORS = intArrayOf(
        0x4C7A2E, 0x6B5236, 0x7A7A7E, 0xEEF2F6, 0x4A3A2C, 0x8A6A44, 0xA04A36, 0xCFC8B8
    )

    fun load(ctx: Context, size: Int, caps: GLCaps): TextureSet {
        val layers = MATERIALS.size
        var levels = 1
        var s = size
        while (s > 1) { s = s shr 1; levels++ }

        val albedoTex = newArray(GLES30.GL_SRGB8_ALPHA8, size, levels, layers, caps)
        val nrTex = newArray(GLES30.GL_RGBA8, size, levels, layers, caps)

        val n = size * size
        val pa = IntArray(n)
        val pn = IntArray(n)
        val pr = IntArray(n)
        val bytes = ByteArray(n * 4)
        val buf = ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder())
        var okFiles = 0
        var fb = 0

        for (i in 0 until layers) {
            val name = MATERIALS[i]
            val hasA = loadPixels(ctx, "textures/${name}_albedo.jpg", size, pa)
            val hasN = loadPixels(ctx, "textures/${name}_normal.jpg", size, pn)
            val hasR = loadPixels(ctx, "textures/${name}_rough.jpg", size, pr)
            if (hasA && hasN && hasR) {
                okFiles++
            } else {
                // fallback prosedural: game tetap jalan walau texture hilang
                fb++
                Log.w(Gl.TAG, "Texture $name tidak lengkap, pakai fallback prosedural")
                if (!hasA) fallbackAlbedo(FALLBACK_COLORS[i], i, size, pa)
                if (!hasN) fallbackNormal(i, size, pn)
                if (!hasR) fallbackRough(i, size, pr)
            }

            // albedo -> RGBA (alpha 255)
            var o = 0
            for (p in 0 until n) {
                val c = pa[p]
                bytes[o++] = (c shr 16).toByte()
                bytes[o++] = (c shr 8).toByte()
                bytes[o++] = c.toByte()
                bytes[o++] = 255.toByte()
            }
            upload(albedoTex, i, size, bytes, buf)

            // normal (rgb) + roughness (alpha)
            o = 0
            for (p in 0 until n) {
                val c = pn[p]
                bytes[o++] = (c shr 16).toByte()
                bytes[o++] = (c shr 8).toByte()
                bytes[o++] = c.toByte()
                bytes[o++] = (pr[p] shr 16).toByte()
            }
            upload(nrTex, i, size, bytes, buf)
        }

        for (t in intArrayOf(albedoTex, nrTex)) {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, t)
            GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D_ARRAY)
        }
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, 0)
        Gl.check("TextureLoader.load")
        Log.i(Gl.TAG, "Texture: $okFiles dari $layers material dari file, $fb fallback, ukuran $size")
        return TextureSet(albedoTex, nrTex, size, okFiles, fb)
    }

    fun delete(t: TextureSet) {
        Gl.delTexture(t.albedo)
        Gl.delTexture(t.normalRough)
    }

    private fun newArray(fmt: Int, size: Int, levels: Int, layers: Int, caps: GLCaps): Int {
        val id = Gl.genTexture()
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, id)
        GLES30.glTexStorage3D(GLES30.GL_TEXTURE_2D_ARRAY, levels, fmt, size, size, layers)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_REPEAT)
        if (caps.anisoOk) {
            // GL_TEXTURE_MAX_ANISOTROPY_EXT = 0x84FE
            GLES20.glTexParameterf(GLES30.GL_TEXTURE_2D_ARRAY, 0x84FE, minOf(caps.maxAniso, if (size >= 2048) 16f else 4f))
        }
        return id
    }

    private fun upload(tex: Int, layer: Int, size: Int, bytes: ByteArray, buf: ByteBuffer) {
        buf.clear()
        buf.put(bytes)
        buf.position(0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, tex)
        GLES30.glTexSubImage3D(
            GLES30.GL_TEXTURE_2D_ARRAY, 0, 0, 0, layer, size, size, 1,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf
        )
    }

    /** Decode jpg dari assets, skala ke size x size, simpan sebagai ARGB. */
    private fun loadPixels(ctx: Context, path: String, size: Int, out: IntArray): Boolean {
        var bmp: Bitmap? = null
        try {
            val opts = BitmapFactory.Options()
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888
            ctx.assets.open(path).use { bmp = BitmapFactory.decodeStream(it, null, opts) }
            var b = bmp ?: return false
            if (b.width != size || b.height != size) {
                val scaled = Bitmap.createScaledBitmap(b, size, size, true)
                if (scaled !== b) b.recycle()
                b = scaled
            }
            b.getPixels(out, 0, size, 0, 0, size, size)
            b.recycle()
            return true
        } catch (e: Exception) {
            return false
        }
    }

    // ------------------------------------------------------------ fallback prosedural
    private fun valueNoise(x: Float, y: Float, salt: Int): Float {
        val ix = Math.floor(x.toDouble()).toInt()
        val iy = Math.floor(y.toDouble()).toInt()
        val fx = x - ix
        val fy = y - iy
        val sx = fx * fx * (3f - 2f * fx)
        val sy = fy * fy * (3f - 2f * fy)
        val a = Noise.hash(ix, iy, salt)
        val b = Noise.hash(ix + 1, iy, salt)
        val c = Noise.hash(ix, iy + 1, salt)
        val d = Noise.hash(ix + 1, iy + 1, salt)
        val top = a + (b - a) * sx
        val bot = c + (d - c) * sx
        return top + (bot - top) * sy
    }

    private fun fallbackAlbedo(rgb: Int, idx: Int, size: Int, out: IntArray) {
        val r0 = (rgb shr 16) and 255
        val g0 = (rgb shr 8) and 255
        val b0 = rgb and 255
        val inv = 1f / size
        for (y in 0 until size) {
            for (x in 0 until size) {
                val u = x * inv
                val v = y * inv
                val n = 0.5f * valueNoise(u * 16f, v * 16f, idx) + 0.3f * valueNoise(u * 48f, v * 48f, idx + 9) +
                    0.2f * valueNoise(u * 128f, v * 128f, idx + 17)
                val k = 0.65f + 0.7f * n
                val r = (r0 * k).toInt().coerceIn(0, 255)
                val g = (g0 * k).toInt().coerceIn(0, 255)
                val b = (b0 * k).toInt().coerceIn(0, 255)
                out[y * size + x] = (255 shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
    }

    private fun fallbackNormal(idx: Int, size: Int, out: IntArray) {
        val inv = 1f / size
        for (y in 0 until size) {
            for (x in 0 until size) {
                val nx = valueNoise(x * inv * 64f, y * inv * 64f, idx + 31) - 0.5f
                val ny = valueNoise(x * inv * 64f + 50f, y * inv * 64f, idx + 37) - 0.5f
                val r = (128 + (nx * 60f)).toInt().coerceIn(0, 255)
                val g = (128 + (ny * 60f)).toInt().coerceIn(0, 255)
                out[y * size + x] = (255 shl 24) or (r shl 16) or (g shl 8) or 255
            }
        }
    }

    private fun fallbackRough(idx: Int, size: Int, out: IntArray) {
        val base = if (idx == 3) 150 else 190
        java.util.Arrays.fill(out, 0, size * size, (255 shl 24) or (base shl 16) or (base shl 8) or base)
    }
}
