package com.vinz.chillworld

import android.app.ActivityManager
import android.content.Context
import android.opengl.GLES20
import android.opengl.GLES30
import android.opengl.GLES31
import android.util.Log

/** Info kemampuan GPU/GLES. Dipanggil sekali dari thread GL setelah context jadi. */
class GLCaps {
    var major = 3
    var minor = 0
    var renderer = ""
    var vendor = ""
    var versionStr = ""
    var extensions = ""
    var maxTextureSize = 2048
    var computeOk = false
    var anisoOk = false
    var maxAniso = 1f
    var astc = false
    var ramMb = 3000L

    fun detect(ctx: Context) {
        versionStr = GLES30.glGetString(GLES30.GL_VERSION) ?: ""
        renderer = GLES30.glGetString(GLES30.GL_RENDERER) ?: ""
        vendor = GLES30.glGetString(GLES30.GL_VENDOR) ?: ""
        extensions = GLES30.glGetString(GLES30.GL_EXTENSIONS) ?: ""

        val m = Regex("OpenGL ES (\\d+)\\.(\\d+)").find(versionStr)
        if (m != null) {
            major = m.groupValues[1].toInt()
            minor = m.groupValues[2].toInt()
        } else {
            val a = IntArray(1)
            GLES30.glGetIntegerv(GLES30.GL_MAJOR_VERSION, a, 0)
            major = if (a[0] >= 3) a[0] else 3
            GLES30.glGetIntegerv(GLES30.GL_MINOR_VERSION, a, 0)
            minor = a[0]
        }

        val t = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE, t, 0)
        maxTextureSize = t[0]

        astc = extensions.contains("GL_KHR_texture_compression_astc_ldr")
        anisoOk = extensions.contains("GL_EXT_texture_filter_anisotropic")
        if (anisoOk) {
            val f = FloatArray(1)
            GLES20.glGetFloatv(0x84FF, f, 0)   // GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT
            maxAniso = f[0].coerceAtLeast(1f)
        }

        if (major > 3 || (major == 3 && minor >= 1)) {
            val inv = IntArray(1)
            GLES30.glGetIntegerv(GLES31.GL_MAX_COMPUTE_WORK_GROUP_INVOCATIONS, inv, 0)
            computeOk = inv[0] >= 64
        }

        try {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            ramMb = mi.totalMem / (1024L * 1024L)
        } catch (e: Exception) {
            Log.w(Gl.TAG, "gagal baca RAM: " + e.message)
        }

        Log.i(Gl.TAG, "GLES $major.$minor | $renderer | $vendor | RAM ${ramMb}MB | " +
            "compute=$computeOk aniso=$maxAniso astc=$astc maxTex=$maxTextureSize")
    }

    /** Pilih preset awal dari nama GPU, versi GLES, dan RAM. */
    fun suggestedQuality(): Quality {
        val r = renderer.lowercase()
        var q = when {
            r.contains("adreno") -> {
                val n = Regex("(\\d{3})").find(r)?.value?.toInt() ?: 0
                when {
                    n >= 700 -> Quality.HIGH
                    n >= 600 -> Quality.MEDIUM
                    else -> Quality.LOW
                }
            }
            r.contains("immortalis") -> Quality.HIGH
            r.contains("mali") -> {
                val n = Regex("g(\\d+)").find(r)?.groupValues?.get(1)?.toInt() ?: 0
                when {
                    n >= 77 -> Quality.HIGH
                    n >= 52 -> Quality.MEDIUM
                    else -> Quality.LOW
                }
            }
            r.contains("powervr") -> Quality.LOW
            else -> Quality.MEDIUM
        }
        if (!computeOk && q.ordinal > Quality.MEDIUM.ordinal) q = Quality.MEDIUM
        if (ramMb <= 3800L && q.ordinal > Quality.MEDIUM.ordinal) q = Quality.MEDIUM
        if (ramMb <= 3000L) q = Quality.LOW
        return q
    }
}
