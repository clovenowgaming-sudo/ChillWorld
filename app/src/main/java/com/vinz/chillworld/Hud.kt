package com.vinz.chillworld

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/** Overlay sederhana: menggambar joystick virtual saat jari menyentuh setengah kiri layar. */
class JoystickOverlay(ctx: Context) : View(ctx) {
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val knob = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cross = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        ring.style = Paint.Style.STROKE
        ring.strokeWidth = 4f
        ring.color = Color.argb(150, 255, 255, 255)
        fill.style = Paint.Style.FILL
        fill.color = Color.argb(40, 255, 255, 255)
        knob.style = Paint.Style.FILL
        knob.color = Color.argb(170, 255, 255, 255)
        cross.style = Paint.Style.STROKE
        cross.strokeWidth = 3f
        cross.color = Color.argb(160, 255, 255, 255)
        isClickable = false
        isFocusable = false
    }

    override fun onDraw(canvas: Canvas) {
        // titik bidik kecil di tengah layar
        val cx = width / 2f
        val cy = height / 2f
        canvas.drawLine(cx - 10f, cy, cx + 10f, cy, cross)
        canvas.drawLine(cx, cy - 10f, cx, cy + 10f, cross)

        if (Input.joyActive) {
            val r = Input.joyRadius
            canvas.drawCircle(Input.joyCx, Input.joyCy, r, fill)
            canvas.drawCircle(Input.joyCx, Input.joyCy, r, ring)
            canvas.drawCircle(Input.joyKx, Input.joyKy, r * 0.38f, knob)
            postInvalidateOnAnimation()
        }
    }
}
