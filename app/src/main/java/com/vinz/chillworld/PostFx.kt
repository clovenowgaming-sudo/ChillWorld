package com.vinz.chillworld

import android.opengl.GLES30
import android.util.Log

/**
 * Pasca-proses: scene FBO (resolusi render), godrays (setengah resolusi), TAA dengan history
 * ping-pong, bloom 1/4 resolusi, lalu present (upscale + sharpening ala CAS) ke layar.
 */
class PostFx {
    var rw = 1
        private set
    var rh = 1
        private set
    var sceneFbo = 0
        private set
    var sceneColor = 0
        private set
    var sceneDepth = 0
        private set
    var historyValid = false

    private var godFbo = 0
    private var godTex = 0
    private var gw = 1
    private var gh = 1
    private val histFbo = IntArray(2)
    private val histTex = IntArray(2)
    private var histIdx = 0
    private val bloomFbo = IntArray(2)
    private val bloomTex = IntArray(2)
    private var bw = 1
    private var bh = 1
    private var vao = 0

    private var pGod = 0
    private var pTaa = 0
    private var pBright = 0
    private var pBlur = 0
    private var pPresent = 0

    fun create() {
        vao = Gl.genVao()
    }

    fun loadPrograms(sh: ShaderLoader) {
        pGod = sh.program("fullscreen.vert", "godray.frag")
        GLES30.glUseProgram(pGod)
        sh.setInt(pGod, "uDepth", 0)

        pTaa = sh.program("fullscreen.vert", "taa.frag")
        GLES30.glUseProgram(pTaa)
        sh.setInt(pTaa, "uScene", 0)
        sh.setInt(pTaa, "uGod", 1)
        sh.setInt(pTaa, "uHist", 2)
        sh.setInt(pTaa, "uDepth", 3)

        pBright = sh.program("fullscreen.vert", "bright.frag")
        GLES30.glUseProgram(pBright)
        sh.setInt(pBright, "uImg", 0)

        pBlur = sh.program("fullscreen.vert", "blur.frag")
        GLES30.glUseProgram(pBlur)
        sh.setInt(pBlur, "uImg", 0)

        pPresent = sh.program("fullscreen.vert", "present.frag")
        GLES30.glUseProgram(pPresent)
        sh.setInt(pPresent, "uImg", 0)
        sh.setInt(pPresent, "uBloom", 1)
        GLES30.glUseProgram(0)
    }

    private fun colorTex(w: Int, h: Int): Int {
        val t = Gl.genTexture()
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, t)
        GLES30.glTexStorage2D(GLES30.GL_TEXTURE_2D, 1, GLES30.GL_RGBA8, w, h)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        return t
    }

    private fun depthTex(w: Int, h: Int): Int {
        val t = Gl.genTexture()
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, t)
        GLES30.glTexStorage2D(GLES30.GL_TEXTURE_2D, 1, GLES30.GL_DEPTH_COMPONENT24, w, h)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        return t
    }

    private fun makeFbo(color: Int, depth: Int, label: String): Int {
        val f = Gl.genFbo()
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, f)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, color, 0)
        if (depth != 0) {
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_TEXTURE_2D, depth, 0)
        }
        val st = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        if (st != GLES30.GL_FRAMEBUFFER_COMPLETE) Log.e(Gl.TAG, "FBO $label tidak lengkap: 0x" + Integer.toHexString(st))
        return f
    }

    fun free() {
        Gl.delFbo(sceneFbo); Gl.delTexture(sceneColor); Gl.delTexture(sceneDepth)
        Gl.delFbo(godFbo); Gl.delTexture(godTex)
        for (i in 0 until 2) {
            Gl.delFbo(histFbo[i]); Gl.delTexture(histTex[i])
            Gl.delFbo(bloomFbo[i]); Gl.delTexture(bloomTex[i])
            histFbo[i] = 0; histTex[i] = 0; bloomFbo[i] = 0; bloomTex[i] = 0
        }
        sceneFbo = 0; sceneColor = 0; sceneDepth = 0; godFbo = 0; godTex = 0
    }

    /** Buat ulang semua target untuk ukuran layar + render scale tertentu. */
    fun resize(screenW: Int, screenH: Int, scale: Float, taa: Boolean, god: Boolean, bloom: Boolean) {
        free()
        rw = maxOf(16, (screenW * scale).toInt())
        rh = maxOf(16, (screenH * scale).toInt())
        sceneColor = colorTex(rw, rh)
        sceneDepth = depthTex(rw, rh)
        sceneFbo = makeFbo(sceneColor, sceneDepth, "scene")
        if (taa) {
            for (i in 0 until 2) {
                histTex[i] = colorTex(rw, rh)
                histFbo[i] = makeFbo(histTex[i], 0, "taa$i")
            }
        }
        if (god) {
            gw = maxOf(1, rw / 2)
            gh = maxOf(1, rh / 2)
            godTex = colorTex(gw, gh)
            godFbo = makeFbo(godTex, 0, "godray")
        }
        if (bloom) {
            bw = maxOf(1, rw / 4)
            bh = maxOf(1, rh / 4)
            for (i in 0 until 2) {
                bloomTex[i] = colorTex(bw, bh)
                bloomFbo[i] = makeFbo(bloomTex[i], 0, "bloom$i")
            }
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        historyValid = false
        histIdx = 0
        Gl.check("PostFx.resize")
        Log.i(Gl.TAG, "Target render ${rw}x$rh (layar ${screenW}x$screenH, scale $scale)")
    }

    private fun bindTex(unit: Int, tex: Int) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex)
    }

    private fun drawFull() {
        GLES30.glBindVertexArray(vao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
        GLES30.glBindVertexArray(0)
    }

    fun bindScene() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, sceneFbo)
        GLES30.glViewport(0, 0, rw, rh)
    }

    private fun postState() {
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glDepthMask(true)
    }

    fun godrays(sunU: Float, sunV: Float, intensity: Float, samples: Int, r: Float, g: Float, b: Float) {
        if (godFbo == 0 || pGod == 0) return
        postState()
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, godFbo)
        GLES30.glViewport(0, 0, gw, gh)
        GLES30.glUseProgram(pGod)
        bindTex(0, sceneDepth)
        GLES30.glUniform2f(GLES30.glGetUniformLocation(pGod, "uSunUv"), sunU, sunV)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(pGod, "uColor"), r, g, b)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(pGod, "uIntensity"), intensity)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(pGod, "uSamples"), samples)
        drawFull()
    }

    /** TAA (kalau aktif). Return texture hasil yang siap dipakai pass berikutnya. */
    fun resolve(taaOn: Boolean, godOn: Boolean): Int {
        if (!taaOn || pTaa == 0 || histFbo[0] == 0) return sceneColor
        postState()
        val cur = 1 - histIdx
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, histFbo[cur])
        GLES30.glViewport(0, 0, rw, rh)
        GLES30.glUseProgram(pTaa)
        bindTex(0, sceneColor)
        bindTex(1, if (godOn && godTex != 0) godTex else sceneColor)
        bindTex(2, histTex[histIdx])
        bindTex(3, sceneDepth)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(pTaa, "uHistValid"), if (historyValid) 1f else 0f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(pTaa, "uGodOn"), if (godOn && godTex != 0) 1f else 0f)
        drawFull()
        histIdx = cur
        historyValid = true
        return histTex[cur]
    }

    /** Bloom ringan. Return texture bloom (hasil akhir blur vertikal). */
    fun bloom(src: Int): Int {
        if (bloomFbo[0] == 0 || pBright == 0 || pBlur == 0) return 0
        postState()
        GLES30.glViewport(0, 0, bw, bh)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, bloomFbo[0])
        GLES30.glUseProgram(pBright)
        bindTex(0, src)
        GLES30.glUniform2f(GLES30.glGetUniformLocation(pBright, "uTexel"), 1f / rw, 1f / rh)
        drawFull()

        GLES30.glUseProgram(pBlur)
        val dir = GLES30.glGetUniformLocation(pBlur, "uDir")
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, bloomFbo[1])
        bindTex(0, bloomTex[0])
        GLES30.glUniform2f(dir, 1f / bw, 0f)
        drawFull()

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, bloomFbo[0])
        bindTex(0, bloomTex[1])
        GLES30.glUniform2f(dir, 0f, 1f / bh)
        drawFull()
        return bloomTex[0]
    }

    /** Pass akhir ke layar: upscale + sharpening + bloom + vignette. */
    fun present(src: Int, bloomTexId: Int, sharp: Float, bloomAmt: Float, screenW: Int, screenH: Int) {
        if (pPresent == 0) return
        postState()
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, screenW, screenH)
        GLES30.glUseProgram(pPresent)
        bindTex(0, src)
        bindTex(1, if (bloomTexId != 0) bloomTexId else src)
        GLES30.glUniform2f(GLES30.glGetUniformLocation(pPresent, "uTexel"), 1f / rw, 1f / rh)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(pPresent, "uSharp"), sharp)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(pPresent, "uBloomAmt"), if (bloomTexId != 0) bloomAmt else 0f)
        drawFull()
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
    }
}
