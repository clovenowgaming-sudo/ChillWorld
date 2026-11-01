package com.vinz.chillworld

/**
 * Pengaturan "Ray Tracing buatan" (Universal Ray Tracing Simulation).
 * INI BUKAN ray tracing hardware: semuanya screen-space (depth buffer + warna scene):
 * AO, contact shadow, color bleeding (GI palsu), pantulan SSR, denoise bilateral, lalu TAA
 * yang merata-ratakan noise antar frame (temporal accumulation).
 */
object RtSim {
    /** Naik tiap ada perubahan; Renderer membangun ulang target kalau berubah. */
    @Volatile var version = 0
        private set
    @Volatile var enabled = false
        private set
    @Volatile var level = 2
        private set

    // jumlah sampel (batas shader: AO 16, GI 12, contact 16, SSR 32)
    var aoSamples = 10
        private set
    var giSamples = 8
        private set
    var contactSteps = 10
        private set
    var ssrSteps = 24
        private set

    val aoRadius = 1.4f
    val aoStrength = 0.9f
    val giStrength = 0.8f
    val giRadiusPx = 90f
    val contactStrength = 0.65f
    val contactLen = 0.9f
    val ssrStrength = 1.6f

    init {
        applyLevel(2)
    }

    private fun applyLevel(l: Int) {
        level = l.coerceIn(1, 3)
        when (level) {
            1 -> { aoSamples = 6; giSamples = 4; contactSteps = 6; ssrSteps = 16 }
            2 -> { aoSamples = 10; giSamples = 8; contactSteps = 10; ssrSteps = 24 }
            else -> { aoSamples = 14; giSamples = 12; contactSteps = 14; ssrSteps = 32 }
        }
    }

    fun enable(on: Boolean) {
        if (enabled != on) {
            enabled = on
            version++
        }
    }

    fun changeLevel(l: Int) {
        applyLevel(l)
        version++
    }

    /** Naik 1 -> 2 -> 3 -> 1 ... */
    fun cycleLevel() = changeLevel(if (level >= 3) 1 else level + 1)

    /** Default per preset: aktif di Ultra, mati di preset lain (bisa diubah manual setelahnya). */
    fun autoFor(q: Quality) = enable(q == Quality.ULTRA)
}
