package com.vinz.chillworld

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLES31
import android.util.Log

/**
 * Memuat shader dari assets/shaders.
 * - "#include "x.glsl"" diganti isi file tersebut
 * - define varian (SHADOW, DEPTH, FAST_SHADOW, ...) disisipkan setelah #version
 * - error compile/link dicetak ke logcat lengkap dengan nama file
 */
class ShaderLoader(private val ctx: Context) {
    var lowPrecision = false

    private fun readAsset(name: String): String? = try {
        ctx.assets.open("shaders/$name").bufferedReader().use { it.readText() }
    } catch (e: Exception) {
        Log.e(Gl.TAG, "File shader tidak ada: $name")
        null
    }

    private fun resolve(name: String, depth: Int): String? {
        val raw = readAsset(name) ?: return null
        if (depth > 4) return raw
        val sb = StringBuilder()
        for (line in raw.split("\n")) {
            val t = line.trim()
            if (t.startsWith("#include")) {
                val inc = t.substringAfter('"').substringBeforeLast('"')
                sb.append(resolve(inc, depth + 1) ?: "").append('\n')
            } else {
                sb.append(line).append('\n')
            }
        }
        return sb.toString()
    }

    private fun withDefines(src: String, defs: List<String>): String {
        val nl = src.indexOf('\n')
        val sb = StringBuilder(src.substring(0, nl + 1))
        if (lowPrecision) sb.append("#define LOW_PRECISION 1\n")
        for (d in defs) sb.append("#define ").append(d).append('\n')
        sb.append(src.substring(nl + 1))
        return sb.toString()
    }

    private fun compile(type: Int, src: String, label: String): Int {
        val s = GLES30.glCreateShader(type)
        GLES30.glShaderSource(s, src)
        GLES30.glCompileShader(s)
        val st = IntArray(1)
        GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, st, 0)
        if (st[0] == 0) {
            Log.e(Gl.TAG, "Gagal compile $label:\n" + GLES30.glGetShaderInfoLog(s))
            GLES30.glDeleteShader(s)
            return 0
        }
        return s
    }

    /** Buat program vertex+fragment. Return 0 kalau gagal. */
    fun program(vert: String, frag: String, defs: List<String> = emptyList()): Int {
        val label = "$vert + $frag $defs"
        val vsrc = resolve(vert, 0) ?: return 0
        val fsrc = resolve(frag, 0) ?: return 0
        val vs = compile(GLES30.GL_VERTEX_SHADER, withDefines(vsrc, defs), vert)
        val fs = compile(GLES30.GL_FRAGMENT_SHADER, withDefines(fsrc, defs), "$frag $defs")
        if (vs == 0 || fs == 0) return 0
        val p = GLES30.glCreateProgram()
        GLES30.glAttachShader(p, vs)
        GLES30.glAttachShader(p, fs)
        GLES30.glLinkProgram(p)
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)
        return finishLink(p, label)
    }

    /** Buat compute shader (GLES 3.1+). */
    fun compute(name: String): Int {
        val src = resolve(name, 0) ?: return 0
        val cs = compile(GLES31.GL_COMPUTE_SHADER, withDefines(src, emptyList()), name)
        if (cs == 0) return 0
        val p = GLES30.glCreateProgram()
        GLES30.glAttachShader(p, cs)
        GLES30.glLinkProgram(p)
        GLES30.glDeleteShader(cs)
        return finishLink(p, name)
    }

    private fun finishLink(p: Int, label: String): Int {
        val st = IntArray(1)
        GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, st, 0)
        if (st[0] == 0) {
            Log.e(Gl.TAG, "Gagal link $label:\n" + GLES30.glGetProgramInfoLog(p))
            GLES30.glDeleteProgram(p)
            return 0
        }
        // UBO "Frame" selalu di binding point 0
        val bi = GLES30.glGetUniformBlockIndex(p, "Frame")
        if (bi != GLES30.GL_INVALID_INDEX) GLES30.glUniformBlockBinding(p, bi, 0)
        // unit sampler standar: shadow 0, albedo 1, normal+rough 2
        GLES30.glUseProgram(p)
        setInt(p, "uShadow", 0)
        setInt(p, "uAlbedo", 1)
        setInt(p, "uNormRough", 2)
        Gl.check("link $label")
        return p
    }

    fun setInt(p: Int, name: String, v: Int) {
        val loc = GLES30.glGetUniformLocation(p, name)
        if (loc >= 0) GLES30.glUniform1i(loc, v)
    }
}
