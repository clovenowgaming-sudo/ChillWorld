package com.vinz.chillworld

import android.opengl.GLES30

/** Langit prosedural: segitiga layar penuh, semua perhitungan ada di sky.frag. */
class Sky {
    private var prog = 0
    private var vao = 0

    fun create() {
        vao = Gl.genVao()
    }

    fun loadPrograms(sh: ShaderLoader) {
        prog = sh.program("sky.vert", "sky.frag")
    }

    fun draw() {
        if (prog == 0) return
        GLES30.glUseProgram(prog)
        GLES30.glBindVertexArray(vao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
        GLES30.glBindVertexArray(0)
    }
}
