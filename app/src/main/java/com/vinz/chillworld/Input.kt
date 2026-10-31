package com.vinz.chillworld

import android.view.MotionEvent

/**
 * Kontrol sentuh: joystick virtual di setengah kiri layar (jalan),
 * geser di setengah kanan layar (melihat sekeliling).
 * Tombol terbang/naik/turun diatur dari MainActivity.
 */
object Input {
    @Volatile var moveX = 0f
    @Volatile var moveY = 0f
    @Volatile var flyMode = false
    @Volatile var upHeld = false
    @Volatile var downHeld = false

    // data buat digambar HUD (dibaca dari thread UI)
    @Volatile var joyActive = false
    @Volatile var joyCx = 0f
    @Volatile var joyCy = 0f
    @Volatile var joyKx = 0f
    @Volatile var joyKy = 0f
    @Volatile var joyRadius = 150f

    private val lock = Any()
    private var lookAccX = 0f
    private var lookAccY = 0f
    private var joyId = -1
    private var lookId = -1
    private var lastX = 0f
    private var lastY = 0f

    /** Ambil dan reset akumulasi geseran layar. out[0] = dx, out[1] = dy (piksel). */
    fun consumeLook(out: FloatArray) {
        synchronized(lock) {
            out[0] = lookAccX
            out[1] = lookAccY
            lookAccX = 0f
            lookAccY = 0f
        }
    }

    fun onTouch(ev: MotionEvent, viewWidth: Int): Boolean {
        val half = viewWidth * 0.5f
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = ev.actionIndex
                val id = ev.getPointerId(i)
                val x = ev.getX(i)
                val y = ev.getY(i)
                if (x < half) {
                    if (joyId == -1) {
                        joyId = id
                        joyCx = x
                        joyCy = y
                        joyKx = x
                        joyKy = y
                        joyActive = true
                        moveX = 0f
                        moveY = 0f
                    }
                } else if (lookId == -1) {
                    lookId = id
                    lastX = x
                    lastY = y
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until ev.pointerCount) {
                    val id = ev.getPointerId(i)
                    val x = ev.getX(i)
                    val y = ev.getY(i)
                    if (id == joyId) {
                        var dx = x - joyCx
                        var dy = y - joyCy
                        val len = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                        val r = joyRadius
                        if (len > r) {
                            dx *= r / len
                            dy *= r / len
                        }
                        joyKx = joyCx + dx
                        joyKy = joyCy + dy
                        moveX = dx / r
                        moveY = -dy / r
                    } else if (id == lookId) {
                        synchronized(lock) {
                            lookAccX += x - lastX
                            lookAccY += y - lastY
                        }
                        lastX = x
                        lastY = y
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val i = ev.actionIndex
                val id = ev.getPointerId(i)
                if (ev.actionMasked == MotionEvent.ACTION_CANCEL) {
                    release(joyId)
                    release(lookId)
                } else {
                    release(id)
                }
            }
        }
        return true
    }

    private fun release(id: Int) {
        if (id == -1) return
        if (id == joyId) {
            joyId = -1
            joyActive = false
            moveX = 0f
            moveY = 0f
        }
        if (id == lookId) lookId = -1
    }
}
