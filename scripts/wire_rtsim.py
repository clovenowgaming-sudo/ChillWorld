# Sambungkan Ray Tracing buatan + override texture 4K ke PostFx, Renderer, Settings, dan launcher desktop
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
KT = os.path.join(HERE, "..", "app", "src", "main", "java", "com", "vinz", "chillworld")
MAIN = os.path.join(HERE, "..", "desktop", "src", "main", "kotlin", "com", "vinz", "chillworld", "desktop", "Main.kt")


def patch(path, pairs):
    t = open(path, encoding="utf-8").read()
    for old, new in pairs:
        n = t.count(old)
        if n != 1:
            print("GAGAL %s: %d kecocokan untuk %r" % (os.path.basename(path), n, old[:80]))
            sys.exit(1)
        t = t.replace(old, new)
    open(path, "w", encoding="utf-8").write(t)
    print("OK  " + os.path.basename(path))


RT_METHOD = '''    /**
     * Ray Tracing buatan (screen-space), 3 pass: A = AO + contact shadow + color bleeding,
     * B = denoise bilateral, C = komposit + SSR. Hasilnya jadi masukan TAA (temporal accumulation).
     * Kalau mati atau shader gagal compile, postInput tetap sceneColor (tampilan normal).
     */
    fun rtSim(rt: RtSim) {
        postInput = sceneColor
        if (!rt.enabled || rtFbo[0] == 0 || pRtA == 0 || pRtB == 0 || pRtC == 0) return
        postState()
        GLES30.glViewport(0, 0, rw, rh)

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, rtFbo[0])
        GLES30.glUseProgram(pRtA)
        bindTex(0, sceneColor)
        bindTex(1, sceneDepth)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(pRtA, "uAoSamples"), rt.aoSamples)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(pRtA, "uGiSamples"), rt.giSamples)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(pRtA, "uContactSteps"), rt.contactSteps)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(pRtA, "uAoRadius"), rt.aoRadius)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(pRtA, "uAoStrength"), rt.aoStrength)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(pRtA, "uContactStrength"), rt.contactStrength)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(pRtA, "uContactLen"), rt.contactLen)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(pRtA, "uGiRadiusPx"), rt.giRadiusPx)
        drawFull()

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, rtFbo[1])
        GLES30.glUseProgram(pRtB)
        bindTex(0, rtTex[0])
        bindTex(1, sceneDepth)
        drawFull()

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, rtFbo[2])
        GLES30.glUseProgram(pRtC)
        bindTex(0, sceneColor)
        bindTex(1, sceneDepth)
        bindTex(2, rtTex[1])
        GLES30.glUniform1i(GLES30.glGetUniformLocation(pRtC, "uSsrSteps"), rt.ssrSteps)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(pRtC, "uGiStrength"), rt.giStrength)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(pRtC, "uSsrStrength"), rt.ssrStrength)
        drawFull()

        postInput = rtTex[2]
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
    }

    /** Bloom ringan. Return texture bloom (hasil akhir blur vertikal). */'''

LOAD_RT = '''        sh.setInt(pPresent, "uBloom", 1)

        // Ray Tracing buatan (3 pass). Kalau ada yang gagal compile, efeknya dilewati otomatis.
        pRtA = sh.program("fullscreen.vert", "rtsim_a.frag")
        if (pRtA != 0) {
            GLES30.glUseProgram(pRtA)
            sh.setInt(pRtA, "uScene", 0)
            sh.setInt(pRtA, "uDepth", 1)
        }
        pRtB = sh.program("fullscreen.vert", "rtsim_b.frag")
        if (pRtB != 0) {
            GLES30.glUseProgram(pRtB)
            sh.setInt(pRtB, "uRt", 0)
            sh.setInt(pRtB, "uDepth", 1)
        }
        pRtC = sh.program("fullscreen.vert", "rtsim_c.frag")
        if (pRtC != 0) {
            GLES30.glUseProgram(pRtC)
            sh.setInt(pRtC, "uScene", 0)
            sh.setInt(pRtC, "uDepth", 1)
            sh.setInt(pRtC, "uRt", 2)
        }
        GLES30.glUseProgram(0)
'''

patch(os.path.join(KT, "PostFx.kt"), [
    ("    private var vao = 0\n",
     "    private var vao = 0\n"
     "    private val rtFbo = IntArray(3)\n"
     "    private val rtTex = IntArray(3)\n"
     "    private var pRtA = 0\n"
     "    private var pRtB = 0\n"
     "    private var pRtC = 0\n\n"
     "    /** Texture warna untuk TAA/present: sceneColor, atau hasil Ray Tracing buatan. */\n"
     "    var postInput = 0\n"
     "        private set\n"),
    ("        sh.setInt(pPresent, \"uBloom\", 1)\n        GLES30.glUseProgram(0)\n", LOAD_RT),
    ("        sceneFbo = 0; sceneColor = 0; sceneDepth = 0; godFbo = 0; godTex = 0\n    }\n",
     "        sceneFbo = 0; sceneColor = 0; sceneDepth = 0; godFbo = 0; godTex = 0\n"
     "        for (i in 0 until 3) {\n"
     "            Gl.delFbo(rtFbo[i]); Gl.delTexture(rtTex[i])\n"
     "            rtFbo[i] = 0; rtTex[i] = 0\n"
     "        }\n"
     "        postInput = 0\n    }\n"),
    ("fun resize(screenW: Int, screenH: Int, scale: Float, taa: Boolean, god: Boolean, bloom: Boolean) {",
     "fun resize(screenW: Int, screenH: Int, scale: Float, taa: Boolean, god: Boolean, bloom: Boolean, rt: Boolean = false) {"),
    ("        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)\n        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)\n        historyValid = false\n",
     "        if (rt) {\n"
     "            for (i in 0 until 3) {\n"
     "                rtTex[i] = colorTex(rw, rh)\n"
     "                rtFbo[i] = makeFbo(rtTex[i], 0, \"rt$i\")\n"
     "            }\n"
     "        }\n"
     "        postInput = sceneColor\n"
     "        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)\n        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)\n        historyValid = false\n"),
    ("if (!taaOn || pTaa == 0 || histFbo[0] == 0) return sceneColor", "if (!taaOn || pTaa == 0 || histFbo[0] == 0) return postInput"),
    ("        bindTex(0, sceneColor)\n        bindTex(1, if (godOn && godTex != 0) godTex else sceneColor)",
     "        bindTex(0, postInput)\n        bindTex(1, if (godOn && godTex != 0) godTex else sceneColor)"),
    ("    /** Bloom ringan. Return texture bloom (hasil akhir blur vertikal). */", RT_METHOD),
])

patch(os.path.join(KT, "Settings.kt"), [
    ("    @Volatile var autoPresetDone = false\n",
     "    @Volatile var autoPresetDone = false\n"
     "    /** 0 = ikut preset. PC: 4096 kalau texture 4K asli tersedia (hanya dipakai di preset Ultra). */\n"
     "    @Volatile var texOverride = 0\n"),
])

patch(os.path.join(KT, "Renderer.kt"), [
    ("    private var spawned = false\n", "    private var spawned = false\n    private var appliedRt = -1\n"),
    ("        val size = min(q.texSize, caps.maxTextureSize)\n",
     "        val want = if (Settings.texOverride > 0 && q == Quality.ULTRA) Settings.texOverride else q.texSize\n"
     "        val size = min(want, caps.maxTextureSize)\n"),
    ("        curScale = q.renderScale\n        needTargets = true\n        hinted = false\n",
     "        curScale = q.renderScale\n        RtSim.autoFor(q)\n        needTargets = true\n        hinted = false\n"),
    ("post.resize(screenW, screenH, curScale, q.taa, q.godraySamples > 0, q.bloom)",
     "post.resize(screenW, screenH, curScale, q.taa, q.godraySamples > 0, q.bloom, RtSim.enabled)"),
    ("        if (needTargets) rebuildTargets()\n        val q = Settings.quality\n",
     "        if (RtSim.version != appliedRt) {\n            appliedRt = RtSim.version\n            needTargets = true\n        }\n"
     "        if (needTargets) rebuildTargets()\n        val q = Settings.quality\n"),
    ("        val resolved = post.resolve(q.taa, godOn)\n",
     "        post.rtSim(RtSim)\n        val resolved = post.resolve(q.taa, godOn)\n"),
    ("(if (culler?.useGpu == true) \" | compute\" else \" | CPU cull\")",
     "(if (culler?.useGpu == true) \" | compute\" else \" | CPU cull\") +\n                (if (RtSim.enabled) \" | RT-sim L\" + RtSim.level else \"\")"),
])

NATIVE4K = '''private fun down(window: Long, key: Int) = glfwGetKey(window, key) == GLFW_PRESS

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
'''

patch(MAIN, [
    ("import com.vinz.chillworld.Renderer\n", "import com.vinz.chillworld.Renderer\nimport com.vinz.chillworld.RtSim\n"),
    ("private fun down(window: Long, key: Int) = glfwGetKey(window, key) == GLFW_PRESS\n", NATIVE4K),
    ("    val ui = DesktopUi()\n    ui.window = window\n",
     "    if (native4kAvailable()) {\n"
     "        Settings.texOverride = 4096\n"
     "        println(\"[Chill World] Texture 4K asli terdeteksi: dipakai di preset Ultra (tekan K untuk matikan/nyalakan)\")\n"
     "    }\n"
     "    val ui = DesktopUi()\n    ui.window = window\n"),
    ("            GLFW_KEY_F -> Input.flyMode = !Input.flyMode\n",
     "            GLFW_KEY_F -> Input.flyMode = !Input.flyMode\n"
     "            GLFW_KEY_R -> RtSim.enable(!RtSim.enabled)\n"
     "            GLFW_KEY_T -> RtSim.cycleLevel()\n"
     "            GLFW_KEY_K -> toggle4k()\n"),
    ("println(\"[Chill World] 1-5 ganti preset (Low..Ultra) | V vsync | F11 fullscreen | Esc (saat mouse bebas) keluar\")",
     "println(\"[Chill World] 1-5 ganti preset (Low..Ultra) | R ray tracing buatan on/off | T level RT (1-3) | K texture 4K | V vsync | F11 fullscreen | Esc keluar\")"),
])
print("Semua patch berhasil.")
