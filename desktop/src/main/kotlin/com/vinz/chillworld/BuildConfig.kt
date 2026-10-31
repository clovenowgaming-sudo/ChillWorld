package com.vinz.chillworld

/**
 * Pengganti BuildConfig yang dibuat otomatis oleh Android.
 * Cek error GL (glGetError) memaksa GPU sinkron, jadi default-nya MATI supaya benchmark tidak terganggu.
 * Nyalakan dengan argumen JVM -Dcw.debug=true untuk cari error GL.
 */
object BuildConfig {
    @JvmField val DEBUG: Boolean = System.getProperty("cw.debug") == "true"
}
