package com.vinz.chillworld

import android.opengl.GLES30

/** Menggambar chunk terrain dengan 3 level LOD. Program dibedakan per pass (utama / bayangan / depth). */
class TerrainRenderer {
    val indices = TerrainIndices()
    private var pMain = 0
    private var pShadow = 0
    private var pDepth = 0
    private var ofsMain = -1
    private var ofsShadow = -1
    private var ofsDepth = -1
    private var svpShadow = -1

    fun loadPrograms(sh: ShaderLoader) {
        pMain = sh.program("terrain.vert", "terrain.frag")
        pShadow = sh.program("terrain.vert", "terrain.frag", listOf("SHADOW"))
        pDepth = sh.program("terrain.vert", "terrain.frag", listOf("DEPTH"))
        ofsMain = GLES30.glGetUniformLocation(pMain, "uOfs")
        ofsShadow = GLES30.glGetUniformLocation(pShadow, "uOfs")
        ofsDepth = GLES30.glGetUniformLocation(pDepth, "uOfs")
        svpShadow = GLES30.glGetUniformLocation(pShadow, "uShadowVP")
    }

    fun draw(list: ArrayList<Chunk>, count: Int, pass: Pass, shadowVp: FloatArray?) {
        val prog = when (pass) {
            Pass.MAIN -> pMain
            Pass.SHADOW -> pShadow
            Pass.DEPTH -> pDepth
        }
        if (prog == 0) return
        val ofs = when (pass) {
            Pass.MAIN -> ofsMain
            Pass.SHADOW -> ofsShadow
            Pass.DEPTH -> ofsDepth
        }
        GLES30.glUseProgram(prog)
        if (pass == Pass.SHADOW && shadowVp != null) {
            GLES30.glUniformMatrix4fv(svpShadow, 1, false, shadowVp, 0)
        }
        for (i in 0 until count) {
            val c = list[i]
            GLES30.glUniform2f(ofs, c.worldX, c.worldZ)
            GLES30.glBindVertexArray(c.vao[c.lod])
            GLES30.glDrawElements(GLES30.GL_TRIANGLES, indices.count[c.lod], GLES30.GL_UNSIGNED_SHORT, 0)
        }
        GLES30.glBindVertexArray(0)
    }
}
