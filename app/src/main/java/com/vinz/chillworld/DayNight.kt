package com.vinz.chillworld

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Siklus siang-malam. 24 jam game = 10 menit nyata.
 * Semua hasil disimpan di array supaya tidak ada alokasi tiap frame.
 */
class DayNight {
    companion object {
        const val DAY_SECONDS = 600f
    }

    val sunDir = FloatArray(3)
    val moonDir = FloatArray(3)
    val sunColor = FloatArray(3)
    val moonColor = FloatArray(3)
    val lightDir = FloatArray(3)
    val lightColor = FloatArray(3)
    val ambSky = FloatArray(3)
    val ambGround = FloatArray(3)
    val windDir = FloatArray(2)
    var shadowStrength = 1f
    var dayFactor = 1f
    var exposure = 0.85f
    var cloudCover = 0.5f
    var windStrength = 1f
    var gust = 0.3f
    var sunY = 1f
    var elapsed = 0f
        private set

    private fun ramp(e0: Float, e1: Float, x: Float): Float = Gl.smooth(e0, e1, x)

    fun update(dt: Float) {
        elapsed += dt
        if (Settings.timeRunning) {
            var t = Settings.timeOfDay + dt * 24f / DAY_SECONDS
            if (t >= 24f) t -= 24f
            Settings.timeOfDay = t
        }
        val hour = Settings.timeOfDay
        val a = (hour - 6f) / 12f * Gl.PIF

        // matahari lewat dari timur ke barat, agak condong ke selatan
        var sx = cos(a) * 0.92f
        var sy = sin(a)
        var sz = 0.38f
        var l = sqrt(sx * sx + sy * sy + sz * sz)
        sx /= l; sy /= l; sz /= l
        sunDir[0] = sx; sunDir[1] = sy; sunDir[2] = sz
        sunY = sy

        // bulan kira-kira di seberang matahari
        var mx = -sx
        var my = -sy
        var mz = -sz + 0.15f
        l = sqrt(mx * mx + my * my + mz * mz)
        mx /= l; my /= l; mz /= l
        moonDir[0] = mx; moonDir[1] = my; moonDir[2] = mz

        // warna matahari: makin rendah makin oranye (hamburan Rayleigh)
        val air = Math.min(1f / (max(sy, 0f) * 0.8f + 0.12f), 8f)
        val sunRamp = ramp(0.0f, 0.12f, sy)
        sunColor[0] = exp(-0.0058f * 9f * air) * 3.4f * sunRamp
        sunColor[1] = exp(-0.0135f * 9f * air) * 3.4f * sunRamp
        sunColor[2] = exp(-0.0331f * 9f * air) * 3.4f * sunRamp

        val moonRamp = ramp(0.0f, 0.12f, my)
        moonColor[0] = 0.55f * 0.22f * moonRamp
        moonColor[1] = 0.65f * 0.22f * moonRamp
        moonColor[2] = 1.00f * 0.22f * moonRamp

        // cahaya utama: matahari kalau di atas horizon, kalau tidak bulan
        if (sy > 0f) {
            for (i in 0 until 3) { lightDir[i] = sunDir[i]; lightColor[i] = sunColor[i] }
            shadowStrength = ramp(0.0f, 0.08f, sy)
        } else {
            for (i in 0 until 3) { lightDir[i] = moonDir[i]; lightColor[i] = moonColor[i] }
            shadowStrength = ramp(0.0f, 0.08f, my) * 0.8f
        }

        dayFactor = ramp(-0.12f, 0.25f, sy)
        val dusk = ramp(0.0f, 0.4f, sy)
        val moonAmb = max(my, 0f) * 0.02f
        val nightR = 0.012f + moonAmb
        val nightG = 0.020f + moonAmb * 1.3f
        val nightB = 0.050f + moonAmb * 2.5f
        val dayR = 0.38f * 0.85f
        val dayG = 0.55f * 0.85f
        val dayB = 0.95f * 0.85f
        val duskR = 0.35f
        val duskG = 0.28f
        val duskB = 0.38f
        val r = duskR + (dayR - duskR) * dusk
        val g = duskG + (dayG - duskG) * dusk
        val b = duskB + (dayB - duskB) * dusk
        ambSky[0] = nightR + (r - nightR) * dayFactor
        ambSky[1] = nightG + (g - nightG) * dayFactor
        ambSky[2] = nightB + (b - nightB) * dayFactor
        ambGround[0] = ambSky[0] * 0.3f + 0.06f * dayFactor
        ambGround[1] = ambSky[1] * 0.3f + 0.05f * dayFactor
        ambGround[2] = ambSky[2] * 0.3f + 0.03f * dayFactor

        // angin dan awan berubah pelan
        windDir[0] = 0.8f
        windDir[1] = 0.6f
        windStrength = 1.0f
        gust = 0.3f + 0.2f * sin(elapsed * 0.1f)
        cloudCover = 0.5f + 0.1f * sin(elapsed * 0.02f)
    }
}
