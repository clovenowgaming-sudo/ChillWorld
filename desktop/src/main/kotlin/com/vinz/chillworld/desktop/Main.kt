package com.vinz.chillworld.desktop

import android.content.Context
import android.opengl.DesktopGl
import com.vinz.chillworld.Input
import com.vinz.chillworld.Quality
import com.vinz.chillworld.Renderer
import com.vinz.chillworld.RtSim
import com.vinz.chillworld.Settings
import com.vinz.chillworld.UiBridge
import org.lwjgl.glfw.Callbacks.glfwFreeCallbacks
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.opengl.GL
import org.lwjgl.system.MemoryUtil.NULL

/** UI sederhana versi PC: statistik tampil di judul window, saran dicetak ke konsol. */
private class DesktopUi : UiBridge {
    @Volatile var window: Long = NULL

    override fun onStats(fps: Float, scale: Float, info: String) {
        val w = window
        if (w != NULL) {
            glfwSetWindowTitle(w, String.format("Chill World | %.0f FPS | skala %.2f | %s", fps, scale, info))
        }
    }

    override fun onHint(text: String) {
        println("[Chill World] $text")
    }
}

private var fullscreen = false
private var vsync = true
private var captured = false
private var hasLastCursor = false
private var lastX = 0.0
private var lastY = 0.0
private val savedPos = IntArray(2)
private val savedSize = intArrayOf(1600, 900)

private fun setCaptured(window: Long, on: Boolean) {
    captured = on
    hasLastCursor = false
    glfwSetInputMode(window, GLFW_CURSOR, if (on) GLFW_CURSOR_DISABLED else GLFW_CURSOR_NORMAL)
    if (on && glfwRawMouseMotionSupported()) glfwSetInputMode(window, GLFW_RAW_MOUSE_MOTION, GLFW_TRUE)
}

private fun toggleFullscreen(window: Long) {
    if (!fullscreen) {
        val x = IntArray(1)
        val y = IntArray(1)
        val w = IntArray(1)
        val h = IntArray(1)
        glfwGetWindowPos(window, x, y)
        glfwGetWindowSize(window, w, h)
        savedPos[0] = x[0]; savedPos[1] = y[0]
        savedSize[0] = w[0]; savedSize[1] = h[0]
        val mon = glfwGetPrimaryMonitor()
        val vm = glfwGetVideoMode(mon) ?: return
        glfwSetWindowMonitor(window, mon, 0, 0, vm.width(), vm.height(), vm.refreshRate())
    } else {
        glfwSetWindowMonitor(window, NULL, savedPos[0], savedPos[1], savedSize[0], savedSize[1], GLFW_DONT_CARE)
    }
    fullscreen = !fullscreen
    glfwSwapInterval(if (vsync) 1 else 0)
}

private fun down(window: Long, key: Int) = glfwGetKey(window, key) == GLFW_PRESS

/** True kalau SEMUA texture 4K asli ada di assets_pc/textures dan resolusinya memang >= 4096 (bukan hasil upscale). */
private fun native4kAvailable(): Boolean {
    val dir = java.io.File("assets_pc/textures")
    for (m in listOf("grass", "ground", "rock", "snow", "bark", "planks", "roof", "plaster")) {
        for (k in listOf("albedo", "normal", "rough")) {
            if (!java.io.File(dir, m + "_" + k + ".jpg").isFile) return false
        }
    }
    return try {
        val f = java.io.File(dir, "grass_albedo.jpg")
        val iis = javax.imageio.ImageIO.createImageInputStream(f)
        val readers = javax.imageio.ImageIO.getImageReaders(iis)
        if (!readers.hasNext()) {
            iis.close()
            false
        } else {
            val r = readers.next()
            r.input = iis
            val ok = r.getWidth(0) >= 4096 && r.getHeight(0) >= 4096
            r.dispose()
            iis.close()
            ok
        }
    } catch (e: Exception) {
        false
    }
}

private fun toggle4k() {
    if (!native4kAvailable()) {
        println("[Chill World] Texture 4K asli belum ada. Jalankan: python scripts/download_textures_4k.py")
        return
    }
    Settings.texOverride = if (Settings.texOverride > 0) 0 else 4096
    Settings.presetVersion++
    println("[Chill World] Texture 4K: " + (if (Settings.texOverride > 0) "AKTIF (hanya di preset Ultra, loading beberapa detik)" else "mati (2K)"))
}

fun main() {
    GLFWErrorCallback.createPrint(System.err).set()
    check(glfwInit()) { "GLFW gagal dijalankan" }

    glfwDefaultWindowHints()
    glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4)
    glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
    glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)
    glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE)
    glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE)

    val window = glfwCreateWindow(savedSize[0], savedSize[1], "Chill World", NULL, NULL)
    if (window == NULL) {
        System.err.println("Gagal bikin window OpenGL 4.3. Update driver GPU, atau GPU ini tidak mendukung OpenGL 4.3.")
        glfwTerminate()
        return
    }
    glfwMakeContextCurrent(window)
    glfwSwapInterval(1)
    GL.createCapabilities()
    DesktopGl.init()

    if (native4kAvailable()) {
        Settings.texOverride = 4096
        println("[Chill World] Texture 4K asli terdeteksi: dipakai di preset Ultra (tekan K untuk matikan/nyalakan)")
    }
    val ui = DesktopUi()
    ui.window = window
    val renderer = Renderer(Context(), ui)
    renderer.onSurfaceCreated(null, null)

    val fbW = IntArray(1)
    val fbH = IntArray(1)
    glfwGetFramebufferSize(window, fbW, fbH)
    var curW = fbW[0]
    var curH = fbH[0]
    if (curW > 0 && curH > 0) renderer.onSurfaceChanged(null, curW, curH)

    glfwSetKeyCallback(window) { w, key, _, action, _ ->
        if (action != GLFW_PRESS) return@glfwSetKeyCallback
        when (key) {
            GLFW_KEY_ESCAPE -> if (captured) setCaptured(w, false) else glfwSetWindowShouldClose(w, true)
            GLFW_KEY_F11 -> toggleFullscreen(w)
            GLFW_KEY_F -> Input.flyMode = !Input.flyMode
            GLFW_KEY_R -> RtSim.enable(!RtSim.enabled)
            GLFW_KEY_T -> RtSim.cycleLevel()
            GLFW_KEY_K -> toggle4k()
            GLFW_KEY_V -> {
                vsync = !vsync
                glfwSwapInterval(if (vsync) 1 else 0)
            }
            in GLFW_KEY_1..GLFW_KEY_5 -> {
                val all = Quality.values()
                val idx = key - GLFW_KEY_1
                if (idx < all.size) Settings.applyQuality(all[idx])
            }
        }
    }
    glfwSetMouseButtonCallback(window) { w, button, action, _ ->
        if (button == GLFW_MOUSE_BUTTON_LEFT && action == GLFW_PRESS && !captured) setCaptured(w, true)
    }
    glfwSetCursorPosCallback(window) { _, x, y ->
        if (captured) {
            if (hasLastCursor) Input.addLook((x - lastX).toFloat(), (y - lastY).toFloat())
            lastX = x
            lastY = y
            hasLastCursor = true
        }
    }

    println("[Chill World] Kontrol: WASD jalan | mouse lihat (klik untuk tangkap, Esc lepas) | F terbang, Spasi naik, Shift turun")
    println("[Chill World] 1-5 ganti preset (Low..Ultra) | R ray tracing buatan on/off | T level RT (1-3) | K texture 4K | V vsync | F11 fullscreen | Esc keluar")

    while (!glfwWindowShouldClose(window)) {
        glfwPollEvents()

        Input.moveX = (if (down(window, GLFW_KEY_D)) 1f else 0f) - (if (down(window, GLFW_KEY_A)) 1f else 0f)
        Input.moveY = (if (down(window, GLFW_KEY_W)) 1f else 0f) - (if (down(window, GLFW_KEY_S)) 1f else 0f)
        Input.upHeld = down(window, GLFW_KEY_SPACE)
        Input.downHeld = down(window, GLFW_KEY_LEFT_SHIFT) || down(window, GLFW_KEY_LEFT_CONTROL)

        glfwGetFramebufferSize(window, fbW, fbH)
        if (fbW[0] <= 0 || fbH[0] <= 0) {
            glfwWaitEventsTimeout(0.1)   // window diminimize
            continue
        }
        if (fbW[0] != curW || fbH[0] != curH) {
            curW = fbW[0]
            curH = fbH[0]
            renderer.onSurfaceChanged(null, curW, curH)
        }

        renderer.onDrawFrame(null)
        glfwSwapBuffers(window)
    }

    renderer.onDestroy()
    glfwFreeCallbacks(window)
    glfwDestroyWindow(window)
    glfwTerminate()
}
