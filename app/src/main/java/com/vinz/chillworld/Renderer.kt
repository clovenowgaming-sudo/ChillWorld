package com.vinz.chillworld

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.util.Log
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** Jembatan ke UI (dipanggil dari thread GL, penerima wajib pindah ke thread UI sendiri). */
interface UiBridge {
    fun onStats(fps: Float, scale: Float, info: String)
    fun onHint(text: String)
}

/**
 * Pipeline render Chill World:
 * shadow (CSM) -> [depth pre-pass] -> sky + scene -> godrays -> TAA -> bloom -> present (upscale + CAS).
 * "Mirip DLSS" = render scale dinamis + TAA + upscaling + sharpening. Bukan DLSS asli.
 */
class Renderer(private val ctx: Context, private val ui: UiBridge) : GLSurfaceView.Renderer {
    private val caps = GLCaps()
    private var shaders: ShaderLoader? = null
    private var culler: InstanceCuller? = null
    private var world: World? = null
    private var texSet: TextureSet? = null

    private val ubo = FrameUbo()
    val camera = Camera()
    private val dayNight = DayNight()
    private val terrain = TerrainRenderer()
    private val trees = Trees()
    private val houses = Houses()
    private val grass = Grass()
    private val sky = Sky()
    private val shadows = Shadows()
    private val post = PostFx()

    private var screenW = 1
    private var screenH = 1
    private var appliedPreset = -1
    private var curScale = 0.67f
    private var needTargets = true
    private var frameIndex = 0
    private var lastNs = 0L
    private val frameMs = FloatArray(30)
    private var fcount = 0
    private var statTimer = 0f
    private var lowFpsTime = 0f
    private var hinted = false
    private var spawned = false
    private var appliedRt = -1

    private val look = FloatArray(2)
    private val planes = FloatArray(24)
    private val visible = ArrayList<Chunk>(512)
    private val cascadeList = ArrayList<Chunk>(512)
    private val shadowVp = FloatArray(16)

    // ------------------------------------------------------------ siklus hidup surface
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        caps.detect(ctx)
        if (!Settings.autoPresetDone) {
            // tanpa deteksi GPU: semua HP mulai dari preset bawaan, ganti manual lewat menu
            Settings.autoPresetDone = true
            Settings.presetVersion++
            Log.i(Gl.TAG, "Preset awal: " + Settings.quality.label + " (manual, tanpa deteksi GPU)")
        }
        world?.dispose()
        GLES30.glBindVertexArray(0)

        val sh = ShaderLoader(ctx)
        shaders = sh
        ubo.create()
        terrain.indices.build()

        val c = InstanceCuller(caps)
        c.init(sh)
        culler = c
        trees.build(c)
        grass.build(c)
        houses.build()
        sky.create()
        post.create()
        world = World(terrain.indices, c.useGpu)

        texSet = null
        appliedPreset = -1
        needTargets = true
        lastNs = 0L
        if (!spawned) {
            camera.spawn(0f, 0f)
            spawned = true
        }
        Gl.check("onSurfaceCreated")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        screenW = max(1, width)
        screenH = max(1, height)
        camera.aspect = screenW.toFloat() / screenH
        needTargets = true
        GLES30.glViewport(0, 0, screenW, screenH)
    }

    fun onDestroy() {
        world?.dispose()
    }

    // ------------------------------------------------------------ preset
    private fun applyPreset() {
        val sh = shaders ?: return
        val c = culler ?: return
        val w = world ?: return
        val q = Settings.quality
        sh.lowPrecision = q.lowPrecision
        terrain.loadPrograms(sh)
        trees.loadPrograms(sh)
        houses.loadPrograms(sh)
        grass.loadPrograms(sh)
        sky.loadPrograms(sh)
        post.loadPrograms(sh)

        val want = if (Settings.texOverride > 0 && q == Quality.ULTRA) Settings.texOverride else q.texSize
        val size = min(want, caps.maxTextureSize)
        val ts = texSet
        if (ts == null || ts.size != size) {
            if (ts != null) TextureLoader.delete(ts)
            texSet = TextureLoader.load(ctx, size, caps)
        }
        shadows.allocate(q)

        val r = Settings.effectiveRadius()
        val grassChunks = ceil(q.grassDist / 64f).toInt()
        w.configure(r, grassChunks, q.grassDensity)
        c.setRanges(q.treeL0, q.treeL1, r * 64f * 0.95f, q.grassDist)
        houses.maxDist = min(r * 64f * 0.9f, 380f)
        camera.far = (r + 1) * 64f * 1.15f
        curScale = q.renderScale
        RtSim.autoFor(q)
        needTargets = true
        hinted = false
        lowFpsTime = 0f
        appliedPreset = Settings.presetVersion
        Log.i(Gl.TAG, "Preset ${q.label}: radius $r chunk, scale ${q.renderScale}, cascade ${q.cascades}")
    }

    private fun rebuildTargets() {
        val q = Settings.quality
        post.resize(screenW, screenH, curScale, q.taa, q.godraySamples > 0, q.bloom, RtSim.enabled)
        needTargets = false
    }

    // ------------------------------------------------------------ frame
    override fun onDrawFrame(gl: GL10?) {
        val c = culler ?: return
        val w = world ?: return
        if (Settings.presetVersion != appliedPreset) applyPreset()
        if (RtSim.version != appliedRt) {
            appliedRt = RtSim.version
            needTargets = true
        }
        if (needTargets) rebuildTargets()
        val q = Settings.quality
        val ts = texSet ?: return

        val now = System.nanoTime()
        var dt = if (lastNs == 0L) 0.016f else (now - lastNs) / 1e9f
        lastNs = now
        if (dt > 0.1f) dt = 0.1f
        trackFrame(dt, q)

        // input + kamera + waktu + dunia
        Input.consumeLook(look)
        camera.look(look[0], look[1])
        val vert = (if (Input.upHeld) 1f else 0f) - (if (Input.downHeld) 1f else 0f)
        camera.move(dt, Input.moveX, Input.moveY, Input.flyMode, vert)
        dayNight.update(dt)
        w.update(camera.x, camera.z)

        // jitter Halton untuk TAA
        var jx = 0f
        var jy = 0f
        if (q.taa) {
            val i = (frameIndex % 16) + 1
            jx = (Frustum.halton(i, 2) - 0.5f) * 2f / post.rw
            jy = (Frustum.halton(i, 3) - 0.5f) * 2f / post.rh
        }
        camera.updateMatrices(jx, jy)
        frameIndex++
        Frustum.extract(camera.vp, planes)

        // chunk terlihat + pilih LOD
        visible.clear()
        val chunks = w.chunks
        for (i in 0 until chunks.size) {
            val ch = chunks[i]
            val wx = ch.worldX
            val wz = ch.worldZ
            val dx = wx + 32f - camera.x
            val dz = wz + 32f - camera.z
            ch.distSq = dx * dx + dz * dz
            val d = sqrt(ch.distSq)
            ch.lod = if (d < 150f) 0 else if (d < 330f) 1 else 2
            if (Frustum.aabbVisible(planes, wx, ch.minY, wz, wx + 64f, ch.maxY, wz + 64f)) visible.add(ch)
        }

        // culling instance (GPU compute atau CPU)
        c.beginFrame(camera.vp, camera.x, camera.y, camera.z)
        houses.begin(camera.vp, camera.x, camera.z)
        for (i in 0 until visible.size) {
            val ch = visible[i]
            val d = sqrt(ch.distSq)
            c.addTrees(ch, d)
            houses.add(ch)
            val g = ch.grass
            if (g != null) c.addGrass(g, d)
        }
        c.endFrame()
        houses.end()

        fillUbo(q, w)

        GLES30.glBindVertexArray(0)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        GLES30.glDepthMask(true)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glCullFace(GLES30.GL_BACK)
        GLES30.glDisable(GLES30.GL_BLEND)

        // ---- shadow pass (CSM)
        if (dayNight.shadowStrength > 0.001f) {
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, 0)
            GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL)
            GLES30.glPolygonOffset(2f, 4f)
            for (ci in 0 until shadows.cascades) {
                shadows.bind(ci)
                shadows.copyVP(ci, shadowVp)
                cascadeList.clear()
                for (i in 0 until chunks.size) {
                    if (shadows.chunkInCascade(ci, chunks[i])) cascadeList.add(chunks[i])
                }
                terrain.draw(cascadeList, cascadeList.size, Pass.SHADOW, shadowVp)
                houses.draw(Pass.SHADOW, shadowVp)
                for (cls in InstanceCuller.PINE0..InstanceCuller.BROAD1) trees.draw(c, cls, Pass.SHADOW, shadowVp)
            }
            GLES30.glDisable(GLES30.GL_POLYGON_OFFSET_FILL)
        }

        // ---- scene pass
        post.bindScene()
        GLES30.glClearDepthf(1f)
        GLES30.glClear(GLES30.GL_DEPTH_BUFFER_BIT)
        bindSceneTextures(ts)

        if (q.depthPrepass) {
            GLES30.glColorMask(false, false, false, false)
            terrain.draw(visible, visible.size, Pass.DEPTH, null)
            houses.draw(Pass.DEPTH, null)
            for (cls in InstanceCuller.PINE0..InstanceCuller.BROAD1) trees.draw(c, cls, Pass.DEPTH, null)
            GLES30.glColorMask(true, true, true, true)
        }

        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthMask(false)
        sky.draw()
        GLES30.glDepthMask(true)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)

        terrain.draw(visible, visible.size, Pass.MAIN, null)
        houses.draw(Pass.MAIN, null)
        for (cls in InstanceCuller.PINE0..InstanceCuller.BROAD1) trees.draw(c, cls, Pass.MAIN, null)
        trees.draw(c, InstanceCuller.BILL, Pass.MAIN, null)
        grass.draw(c, q.grassDist)

        // ---- godrays
        var godOn = false
        if (q.godraySamples > 0) godOn = runGodrays(q)

        // ---- TAA, bloom, present
        post.rtSim(RtSim)
        val resolved = post.resolve(q.taa, godOn)
        val bloomTex = if (q.bloom) post.bloom(resolved) else 0
        post.present(resolved, bloomTex, if (q.cas) 0.7f else 0f, 0.35f, screenW, screenH)
        Gl.check("onDrawFrame")

        infoVisible = w.chunks.size
    }

    private var infoVisible = 0

    private fun bindSceneTextures(ts: TextureSet) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, shadows.tex)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, ts.albedo)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE2)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D_ARRAY, ts.normalRough)
    }

    private fun fillUbo(q: Quality, w: World) {
        val d = ubo.data
        ubo.setMat(FrameUbo.VP, camera.vpJ)
        ubo.setMat(FrameUbo.VIEW, camera.view)
        ubo.setMat(FrameUbo.INV_VP, camera.invVp)
        ubo.setMat(FrameUbo.PREV_VP, camera.prevVp)

        shadows.compute(camera, dayNight.lightDir, q.shadowDist)
        System.arraycopy(shadows.lightVP, 0, d, FrameUbo.LIGHT_VP, 64)

        val time = dayNight.elapsed
        ubo.setVec(FrameUbo.CAM_POS, camera.x, camera.y, camera.z, time)
        ubo.setVec(FrameUbo.SUN_DIR, dayNight.sunDir[0], dayNight.sunDir[1], dayNight.sunDir[2], 0f)
        ubo.setVec(FrameUbo.SUN_COLOR, dayNight.sunColor[0], dayNight.sunColor[1], dayNight.sunColor[2], 0f)
        ubo.setVec(FrameUbo.MOON_DIR, dayNight.moonDir[0], dayNight.moonDir[1], dayNight.moonDir[2], 0f)
        ubo.setVec(FrameUbo.MOON_COLOR, dayNight.moonColor[0], dayNight.moonColor[1], dayNight.moonColor[2], 0f)
        ubo.setVec(FrameUbo.LIGHT_COLOR, dayNight.lightColor[0], dayNight.lightColor[1], dayNight.lightColor[2], 0f)
        ubo.setVec(FrameUbo.AMB_SKY, dayNight.ambSky[0], dayNight.ambSky[1], dayNight.ambSky[2], dayNight.exposure)
        ubo.setVec(FrameUbo.AMB_GROUND, dayNight.ambGround[0], dayNight.ambGround[1], dayNight.ambGround[2], 0f)
        val viewDist = max(w.radius * 64f - 32f, 100f)
        ubo.setVec(FrameUbo.FOG, 1.7f / viewDist, viewDist, 0f, 0f)
        ubo.setVec(FrameUbo.SPLITS, shadows.splits[0], shadows.splits[1], shadows.splits[2], shadows.splits[3])
        ubo.setVec(FrameUbo.TEXEL, shadows.texel[0], shadows.texel[1], shadows.texel[2], shadows.texel[3])
        ubo.setVec(FrameUbo.SCREEN, post.rw.toFloat(), post.rh.toFloat(), 1f / post.rw, 1f / post.rh)
        ubo.setVec(FrameUbo.WIND, dayNight.windDir[0], dayNight.windDir[1], dayNight.windStrength, dayNight.gust)
        ubo.setVec(FrameUbo.MISC, shadows.cascades.toFloat(), 1f / shadows.res, dayNight.dayFactor, dayNight.cloudCover)
        ubo.setVec(FrameUbo.LIGHT, dayNight.lightDir[0], dayNight.lightDir[1], dayNight.lightDir[2], dayNight.shadowStrength)
        ubo.upload()
    }

    /** Godrays: kuat saat kamera menghadap matahari. Return true kalau dijalankan. */
    private fun runGodrays(q: Quality): Boolean {
        val sy = dayNight.sunY
        if (sy < -0.02f) return false
        val sx = dayNight.sunDir[0]
        val sz = dayNight.sunDir[2]
        val px = camera.x + sx * 1000f
        val py = camera.y + sy * 1000f
        val pz = camera.z + sz * 1000f
        val m = camera.vp
        val cw = m[3] * px + m[7] * py + m[11] * pz + m[15]
        if (cw <= 0.001f) return false
        val cxn = (m[0] * px + m[4] * py + m[8] * pz + m[12]) / cw
        val cyn = (m[1] * px + m[5] * py + m[9] * pz + m[13]) / cw
        val facing = (camera.fwd[0] * sx + camera.fwd[1] * sy + camera.fwd[2] * sz).coerceAtLeast(0f)
        val horizon = Gl.smooth(-0.02f, 0.15f, sy)
        val intensity = 0.55f * facing.pow(1.5f) * horizon * (1f + (1f - sy.coerceIn(0f, 1f)) * 0.6f)
        if (intensity < 0.01f) return false
        val sc = dayNight.sunColor
        val mx = max(sc[0], max(sc[1], max(sc[2], 0.001f)))
        post.godrays(cxn * 0.5f + 0.5f, cyn * 0.5f + 0.5f, intensity, q.godraySamples, sc[0] / mx, sc[1] / mx, sc[2] / mx)
        return true
    }

    /** Catat waktu frame, atur Dynamic Resolution Scaling, kirim statistik ke UI. */
    private fun trackFrame(dt: Float, q: Quality) {
        frameMs[fcount % 30] = dt * 1000f
        fcount++
        statTimer += dt

        if (fcount % 30 == 0) {
            var sum = 0f
            for (i in 0 until 30) sum += frameMs[i]
            val avg = sum / 30f
            if (avg > 33f && curScale > 0.5f + 0.001f) {
                curScale = max(0.5f, curScale - 0.05f)
                needTargets = true
            } else if (avg < 25f && curScale < q.renderScale - 0.001f) {
                curScale = min(q.renderScale, curScale + 0.05f)
                needTargets = true
            }
        }

        if (statTimer >= 0.5f) {
            statTimer = 0f
            val n = min(fcount, 30)
            var sum = 0f
            for (i in 0 until n) sum += frameMs[i]
            val avg = if (n > 0) sum / n else 16f
            val fps = 1000f / max(avg, 0.1f)
            val info = "Chunk " + infoVisible + " | GLES " + caps.major + "." + caps.minor +
                (if (culler?.useGpu == true) " | compute" else " | CPU cull") +
                (if (RtSim.enabled) " | RT-sim L" + RtSim.level else "")
            ui.onStats(fps, curScale, info)

            if (fps < 20f) lowFpsTime += 0.5f else lowFpsTime = 0f
            if (lowFpsTime >= 5f && !hinted) {
                hinted = true
                if (q == Quality.LOW) {
                    ui.onHint("FPS masih di bawah 20 pada Low. Saran: turunkan radius chunk lewat Menu.")
                } else {
                    ui.onHint("FPS di bawah 20 pada preset " + q.label + ". Coba turunkan preset lewat Menu.")
                }
            }
        }
    }
}
