package com.vinz.chillworld

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Layar game penuh: GLSurfaceView (OpenGL ES 3.x) + HUD + menu pengaturan. */
class MainActivity : Activity(), UiBridge {
    private lateinit var glView: GLSurfaceView
    private lateinit var renderer: Renderer
    private lateinit var overlay: JoystickOverlay
    private lateinit var fpsText: TextView
    private lateinit var hintText: TextView
    private lateinit var panel: LinearLayout
    private lateinit var timeLabel: TextView
    private lateinit var timeBar: SeekBar
    private lateinit var radiusLabel: TextView
    private val presetButtons = ArrayList<Button>()
    private val ui = Handler(Looper.getMainLooper())
    private var density = 1f

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        density = resources.displayMetrics.density
        Input.joyRadius = 55f * density

        val root = FrameLayout(this)
        renderer = Renderer(applicationContext, this)

        glView = object : GLSurfaceView(this) {
            override fun onTouchEvent(event: MotionEvent): Boolean {
                val r = Input.onTouch(event, width)
                this@MainActivity.overlay.postInvalidateOnAnimation()
                return r
            }
        }
        glView.setEGLContextClientVersion(3)
        glView.setEGLConfigChooser(8, 8, 8, 8, 0, 0)
        glView.preserveEGLContextOnPause = true
        glView.setRenderer(renderer)
        root.addView(glView, FrameLayout.LayoutParams(-1, -1))

        overlay = JoystickOverlay(this)
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))

        fpsText = TextView(this)
        fpsText.setTextColor(Color.WHITE)
        fpsText.setShadowLayer(4f, 1f, 1f, Color.BLACK)
        fpsText.textSize = 12f
        val fp = FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START)
        fp.setMargins(dp(12), dp(8), 0, 0)
        root.addView(fpsText, fp)

        hintText = TextView(this)
        hintText.setTextColor(Color.WHITE)
        hintText.setBackgroundColor(Color.argb(170, 0, 0, 0))
        hintText.textSize = 12f
        hintText.setPadding(dp(10), dp(6), dp(10), dp(6))
        hintText.visibility = View.GONE
        val hp = FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        hp.setMargins(0, dp(40), 0, 0)
        root.addView(hintText, hp)

        // tombol kanan atas: menu + terbang
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        val menuBtn = smallButton("Menu")
        val flyBtn = smallButton("Terbang: mati")
        bar.addView(flyBtn)
        bar.addView(menuBtn)
        val bp = FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END)
        bp.setMargins(0, dp(6), dp(8), 0)
        root.addView(bar, bp)

        // tombol naik/turun (mode terbang), kanan bawah
        val vbar = LinearLayout(this)
        vbar.orientation = LinearLayout.VERTICAL
        val upBtn = smallButton("Naik")
        val downBtn = smallButton("Turun")
        vbar.addView(upBtn)
        vbar.addView(downBtn)
        val vp = FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END)
        vp.setMargins(0, 0, dp(14), dp(14))
        root.addView(vbar, vp)

        upBtn.setOnTouchListener { _, ev -> holdButton(ev, true); true }
        downBtn.setOnTouchListener { _, ev -> holdButton(ev, false); true }
        flyBtn.setOnClickListener {
            Input.flyMode = !Input.flyMode
            flyBtn.text = if (Input.flyMode) "Terbang: nyala" else "Terbang: mati"
        }

        panel = buildPanel()
        panel.visibility = View.GONE
        val pp = FrameLayout.LayoutParams(dp(300), -2, Gravity.CENTER)
        root.addView(panel, pp)
        menuBtn.setOnClickListener {
            panel.visibility = if (panel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            refreshPanel()
        }

        setContentView(root)
        hideSystemBars()
    }

    private fun dp(v: Int): Int = (v * density).toInt()

    private fun smallButton(label: String): Button {
        val b = Button(this)
        b.text = label
        b.textSize = 12f
        b.setTextColor(Color.WHITE)
        b.setBackgroundColor(Color.argb(140, 20, 30, 50))
        b.setPadding(dp(10), dp(4), dp(10), dp(4))
        b.minHeight = 0
        b.minimumHeight = 0
        val lp = LinearLayout.LayoutParams(-2, dp(38))
        lp.setMargins(dp(3), dp(3), dp(3), dp(3))
        b.layoutParams = lp
        return b
    }

    private fun holdButton(ev: MotionEvent, up: Boolean) {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                Input.flyMode = true
                if (up) Input.upHeld = true else Input.downHeld = true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (up) Input.upHeld = false else Input.downHeld = false
            }
        }
    }

    private fun buildPanel(): LinearLayout {
        val p = LinearLayout(this)
        p.orientation = LinearLayout.VERTICAL
        p.setBackgroundColor(Color.argb(215, 12, 18, 30))
        p.setPadding(dp(14), dp(12), dp(14), dp(12))

        val title = TextView(this)
        title.text = "Pengaturan"
        title.setTextColor(Color.WHITE)
        title.textSize = 16f
        p.addView(title)

        val lbl = TextView(this)
        lbl.text = "Preset grafik"
        lbl.setTextColor(Color.LTGRAY)
        lbl.textSize = 12f
        p.addView(lbl)

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        for (q in Quality.values()) {
            val b = smallButton(q.label)
            b.setOnClickListener {
                Settings.setQuality(q)
                refreshPanel()
            }
            presetButtons.add(b)
            row.addView(b)
        }
        p.addView(row)

        val rrow = LinearLayout(this)
        rrow.orientation = LinearLayout.HORIZONTAL
        val minus = smallButton("Radius -")
        val plus = smallButton("Radius +")
        radiusLabel = TextView(this)
        radiusLabel.setTextColor(Color.WHITE)
        radiusLabel.textSize = 12f
        minus.setOnClickListener { Settings.changeRadius(-1); refreshPanel() }
        plus.setOnClickListener { Settings.changeRadius(1); refreshPanel() }
        rrow.addView(minus)
        rrow.addView(plus)
        rrow.addView(radiusLabel)
        p.addView(rrow)

        val fpsBtn = smallButton("FPS: nyala")
        fpsBtn.setOnClickListener {
            Settings.showFps = !Settings.showFps
            fpsBtn.text = if (Settings.showFps) "FPS: nyala" else "FPS: mati"
            if (!Settings.showFps) fpsText.text = ""
        }
        p.addView(fpsBtn)

        timeLabel = TextView(this)
        timeLabel.setTextColor(Color.WHITE)
        timeLabel.textSize = 12f
        p.addView(timeLabel)

        timeBar = SeekBar(this)
        timeBar.max = 239
        timeBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    Settings.timeOfDay = progress / 10f
                    updateTimeLabel()
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        p.addView(timeBar)

        val runBtn = smallButton("Waktu: jalan")
        runBtn.setOnClickListener {
            Settings.timeRunning = !Settings.timeRunning
            runBtn.text = if (Settings.timeRunning) "Waktu: jalan" else "Waktu: berhenti"
        }
        p.addView(runBtn)

        val close = smallButton("Tutup")
        close.setOnClickListener { panel.visibility = View.GONE }
        p.addView(close)
        return p
    }

    private fun updateTimeLabel() {
        val t = Settings.timeOfDay
        val h = t.toInt()
        val m = ((t - h) * 60f).toInt()
        timeLabel.text = "Jam " + String.format("%02d:%02d", h, m)
    }

    private fun refreshPanel() {
        val cur = Settings.quality
        for (i in 0 until presetButtons.size) {
            val q = Quality.values()[i]
            presetButtons[i].setBackgroundColor(
                if (q == cur) Color.argb(220, 40, 110, 200) else Color.argb(140, 20, 30, 50)
            )
        }
        radiusLabel.text = " " + Settings.effectiveRadius() + " chunk"
        timeBar.progress = (Settings.timeOfDay * 10f).toInt().coerceIn(0, 239)
        updateTimeLabel()
    }

    // ------------------------------------------------------------ UiBridge (dipanggil dari thread GL)
    override fun onStats(fps: Float, scale: Float, info: String) {
        ui.post {
            if (Settings.showFps) {
                fpsText.text = String.format("%.0f FPS | skala %.0f%% | %s | %s", fps, scale * 100f, Settings.quality.label, info)
            }
            if (panel.visibility == View.VISIBLE) {
                timeBar.progress = (Settings.timeOfDay * 10f).toInt().coerceIn(0, 239)
                updateTimeLabel()
            }
        }
    }

    override fun onHint(text: String) {
        ui.post {
            hintText.text = text
            hintText.visibility = View.VISIBLE
            ui.postDelayed({ hintText.visibility = View.GONE }, 9000)
        }
    }

    // ------------------------------------------------------------ siklus hidup
    private fun hideSystemBars() {
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onResume() {
        super.onResume()
        glView.onResume()
        hideSystemBars()
    }

    override fun onPause() {
        Input.upHeld = false
        Input.downHeld = false
        glView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        renderer.onDestroy()
        super.onDestroy()
    }
}
