package com.vinz.chillworld

/**
 * Pengganti BuildConfig yang dibuat otomatis oleh Android (field GL_DEBUG dipakai Gl.check).
 * Cek error GL (glGetError) memaksa GPU sinkron, jadi default-nya MATI supaya benchmark tidak terganggu.
 * Nyalakan dengan argumen JVM -Dcw.debug=true untuk mencari error GL.
 */
object BuildConfig {
    @JvmField val GL_DEBUG: Boolean = System.getProperty("cw.debug") == "true"
}
