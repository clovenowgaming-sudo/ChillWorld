package com.vinz.chillworld

/**
 * Input versi desktop (keyboard + mouse). API-nya sama dengan Input Android
 * supaya Renderer dipakai tanpa perubahan. Diisi dari callback GLFW di Main.kt.
 */
object Input {
    @Volatile var moveX = 0f
    @Volatile var moveY = 0f
    @Volatile var flyMode = false
    @Volatile var upHeld = false
    @Volatile var downHeld = false

    private val lock = Any()
    private var lookAccX = 0f
    private var lookAccY = 0f

    /** Dipanggil dari callback mouse: geseran dalam piksel. */
    fun addLook(dx: Float, dy: Float) {
        synchronized(lock) {
            lookAccX += dx
            lookAccY += dy
        }
    }

    /** Ambil dan reset akumulasi geseran mouse. out[0] = dx, out[1] = dy (piksel). */
    fun consumeLook(out: FloatArray) {
        synchronized(lock) {
            out[0] = lookAccX
            out[1] = lookAccY
            lookAccX = 0f
            lookAccY = 0f
        }
    }
}
