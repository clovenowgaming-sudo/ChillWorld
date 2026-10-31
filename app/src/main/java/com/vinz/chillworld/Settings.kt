package com.vinz.chillworld

/**
 * Preset grafik. Angka mengikuti spesifikasi:
 * Low / Medium / High / Extreme.
 */
enum class Quality(
    val label: String,
    val renderScale: Float,
    val cascades: Int,
    val shadowRes: Int,
    val taa: Boolean,
    val cas: Boolean,
    val godraySamples: Int,
    val chunkRadius: Int,
    val grassDensity: Float,
    val depthPrepass: Boolean,
    val bloom: Boolean,
    val shadowDist: Float,
    val lowPrecision: Boolean,
    val grassDist: Float,
    val treeL0: Float,
    val treeL1: Float,
    val texSize: Int
) {
    LOW("Low", 0.50f, 1, 1024, false, false, 0, 4, 0.25f, false, false, 70f, true, 50f, 45f, 120f, 512),
    MEDIUM("Medium", 0.67f, 2, 1536, true, false, 8, 6, 0.50f, false, true, 130f, false, 70f, 60f, 170f, 1024),
    HIGH("High", 0.80f, 3, 2048, true, false, 12, 8, 0.75f, false, true, 200f, false, 90f, 70f, 200f, 1024),
    EXTREME("Extreme", 1.00f, 4, 2048, true, true, 16, 10, 1.00f, true, true, 300f, false, 110f, 80f, 230f, 1024)
}

/** Pengaturan yang bisa diubah dari menu dalam game. */
object Settings {
    @Volatile var quality: Quality = Quality.MEDIUM
    @Volatile var presetVersion = 0
    @Volatile var radiusAdjust = 0
    @Volatile var showFps = true
    @Volatile var timeOfDay = 10f        // jam 0..24
    @Volatile var timeRunning = true
    @Volatile var autoPresetDone = false

    fun effectiveRadius(): Int = (quality.chunkRadius + radiusAdjust).coerceAtLeast(2)

    fun applyQuality(q: Quality) {
        quality = q
        radiusAdjust = 0
        presetVersion++
    }

    fun changeRadius(delta: Int) {
        val r = (quality.chunkRadius + radiusAdjust + delta).coerceIn(2, quality.chunkRadius + 2)
        radiusAdjust = r - quality.chunkRadius
        presetVersion++
    }
}
