package android.opengl

import org.lwjgl.opengl.GL11C
import org.lwjgl.opengl.GL12C
import org.lwjgl.opengl.GL13C
import org.lwjgl.opengl.GL15C
import org.lwjgl.opengl.GL20C
import org.lwjgl.opengl.GL30C
import org.lwjgl.opengl.GL31C
import org.lwjgl.opengl.GL33C
import org.lwjgl.opengl.GL40C
import org.lwjgl.opengl.GL41C
import org.lwjgl.opengl.GL42C
import org.lwjgl.opengl.GL43C
import org.lwjgl.system.MemoryUtil
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.DoubleBuffer
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.LongBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Tiruan android.opengl.GLES20/30/31 di atas OpenGL 4.3 core (LWJGL).
 * Tujuannya: kode render Android yang sama bisa dikompilasi untuk Windows tanpa diubah.
 * Hanya fungsi dan konstanta yang benar-benar dipakai game yang disediakan di sini.
 */
object DesktopGl {
    @JvmField var defaultVao = 0

    /** Core profile wajib punya VAO aktif; GLES mengizinkan VAO 0, jadi kita sediakan VAO bawaan. */
    fun init() {
        defaultVao = GL30C.glGenVertexArrays()
        GL30C.glBindVertexArray(defaultVao)
    }

    /** "#version 300/310 es" -> "#version 430 core" (sisa sintaks shader kompatibel). */
    fun toDesktopGlsl(src: String): String =
        Regex("#version\\s+3\\d0\\s+es").replace(src, "#version 430 core")

    /** Core profile tidak mengizinkan glGetString(GL_EXTENSIONS), jadi dirakit dari glGetStringi. */
    fun extensions(): String {
        val n = GL11C.glGetInteger(0x821D)
        val sb = StringBuilder()
        for (i in 0 until n) {
            val e = GL30C.glGetStringi(0x1F03, i) ?: continue
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(e)
        }
        return sb.toString()
    }
}

private fun elemSize(b: Buffer): Int = when (b) {
    is ByteBuffer -> 1
    is ShortBuffer, is CharBuffer -> 2
    is IntBuffer, is FloatBuffer -> 4
    is LongBuffer, is DoubleBuffer -> 8
    else -> 1
}

private fun remainingBytes(b: Buffer): Int = b.remaining() * elemSize(b)

private fun copyToNative(src: Buffer, dst: ByteBuffer) {
    when (src) {
        is ByteBuffer -> dst.put(src.duplicate())
        is FloatBuffer -> dst.asFloatBuffer().put(src.duplicate())
        is IntBuffer -> dst.asIntBuffer().put(src.duplicate())
        is ShortBuffer -> dst.asShortBuffer().put(src.duplicate())
        else -> throw IllegalArgumentException("Tipe buffer tidak didukung: " + src.javaClass.name)
    }
}

/** Alamat native buffer mulai dari posisinya (seperti semantik GLES di Android). */
internal fun <R> withAddr(buf: Buffer?, block: (Long) -> R): R {
    if (buf == null) return block(0L)
    if (buf.isDirect) {
        return block(MemoryUtil.memAddress0(buf) + buf.position().toLong() * elemSize(buf))
    }
    val tmp = MemoryUtil.memAlloc(remainingBytes(buf))
    try {
        copyToNative(buf, tmp)
        return block(MemoryUtil.memAddress(tmp))
    } finally {
        MemoryUtil.memFree(tmp)
    }
}

open class GLBase {
    // ---- konstanta (nilai sama persis di GLES dan OpenGL desktop) ----
    @JvmField val GL_ARRAY_BUFFER = 0x8892
    @JvmField val GL_BACK = 0x0405
    @JvmField val GL_BLEND = 0x0BE2
    @JvmField val GL_CLAMP_TO_EDGE = 0x812F
    @JvmField val GL_COLOR_ATTACHMENT0 = 0x8CE0
    @JvmField val GL_COMMAND_BARRIER_BIT = 0x40
    @JvmField val GL_COMPARE_REF_TO_TEXTURE = 0x884E
    @JvmField val GL_COMPILE_STATUS = 0x8B81
    @JvmField val GL_COMPUTE_SHADER = 0x91B9
    @JvmField val GL_CULL_FACE = 0x0B44
    @JvmField val GL_DEPTH_ATTACHMENT = 0x8D00
    @JvmField val GL_DEPTH_BUFFER_BIT = 0x100
    @JvmField val GL_DEPTH_COMPONENT16 = 0x81A5
    @JvmField val GL_DEPTH_COMPONENT24 = 0x81A6
    @JvmField val GL_DEPTH_TEST = 0x0B71
    @JvmField val GL_DRAW_INDIRECT_BUFFER = 0x8F3F
    @JvmField val GL_DYNAMIC_COPY = 0x88EA
    @JvmField val GL_DYNAMIC_DRAW = 0x88E8
    @JvmField val GL_ELEMENT_ARRAY_BUFFER = 0x8893
    @JvmField val GL_EXTENSIONS = 0x1F03
    @JvmField val GL_FLOAT = 0x1406
    @JvmField val GL_FRAGMENT_SHADER = 0x8B30
    @JvmField val GL_FRAMEBUFFER = 0x8D40
    @JvmField val GL_FRAMEBUFFER_COMPLETE = 0x8CD5
    @JvmField val GL_INVALID_INDEX = -1
    @JvmField val GL_LEQUAL = 0x0203
    @JvmField val GL_LINEAR = 0x2601
    @JvmField val GL_LINEAR_MIPMAP_LINEAR = 0x2703
    @JvmField val GL_LINK_STATUS = 0x8B82
    @JvmField val GL_MAJOR_VERSION = 0x821B
    @JvmField val GL_MAX_COMPUTE_WORK_GROUP_INVOCATIONS = 0x90EB
    @JvmField val GL_MAX_TEXTURE_SIZE = 0x0D33
    @JvmField val GL_MINOR_VERSION = 0x821C
    @JvmField val GL_NEAREST = 0x2600
    @JvmField val GL_NONE = 0
    @JvmField val GL_NO_ERROR = 0
    @JvmField val GL_POLYGON_OFFSET_FILL = 0x8037
    @JvmField val GL_RENDERER = 0x1F01
    @JvmField val GL_REPEAT = 0x2901
    @JvmField val GL_RGBA = 0x1908
    @JvmField val GL_RGBA8 = 0x8058
    @JvmField val GL_SHADER_STORAGE_BUFFER = 0x90D2
    @JvmField val GL_SRGB8_ALPHA8 = 0x8C43
    @JvmField val GL_STATIC_DRAW = 0x88E4
    @JvmField val GL_TEXTURE0 = 0x84C0
    @JvmField val GL_TEXTURE1 = 0x84C1
    @JvmField val GL_TEXTURE2 = 0x84C2
    @JvmField val GL_TEXTURE_2D = 0x0DE1
    @JvmField val GL_TEXTURE_2D_ARRAY = 0x8C1A
    @JvmField val GL_TEXTURE_COMPARE_FUNC = 0x884D
    @JvmField val GL_TEXTURE_COMPARE_MODE = 0x884C
    @JvmField val GL_TEXTURE_MAG_FILTER = 0x2800
    @JvmField val GL_TEXTURE_MIN_FILTER = 0x2801
    @JvmField val GL_TEXTURE_WRAP_S = 0x2802
    @JvmField val GL_TEXTURE_WRAP_T = 0x2803
    @JvmField val GL_TRIANGLES = 0x0004
    @JvmField val GL_UNIFORM_BUFFER = 0x8A11
    @JvmField val GL_UNSIGNED_BYTE = 0x1401
    @JvmField val GL_UNSIGNED_SHORT = 0x1403
    @JvmField val GL_VENDOR = 0x1F00
    @JvmField val GL_VERSION = 0x1F02
    @JvmField val GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT = 0x1
    @JvmField val GL_VERTEX_SHADER = 0x8B31

    // ---- state & draw ----
    fun glEnable(cap: Int) = GL11C.glEnable(cap)
    fun glDisable(cap: Int) = GL11C.glDisable(cap)
    fun glClear(mask: Int) = GL11C.glClear(mask)
    fun glClearDepthf(d: Float) = GL41C.glClearDepthf(d)
    fun glViewport(x: Int, y: Int, w: Int, h: Int) = GL11C.glViewport(x, y, w, h)
    fun glDepthFunc(f: Int) = GL11C.glDepthFunc(f)
    fun glDepthMask(m: Boolean) = GL11C.glDepthMask(m)
    fun glColorMask(r: Boolean, g: Boolean, b: Boolean, a: Boolean) = GL11C.glColorMask(r, g, b, a)
    fun glCullFace(m: Int) = GL11C.glCullFace(m)
    fun glPolygonOffset(factor: Float, units: Float) = GL11C.glPolygonOffset(factor, units)
    fun glGetError(): Int = GL11C.glGetError()

    fun glDrawArrays(mode: Int, first: Int, count: Int) = GL11C.glDrawArrays(mode, first, count)
    fun glDrawElements(mode: Int, count: Int, type: Int, offset: Int) =
        GL11C.glDrawElements(mode, count, type, offset.toLong())
    fun glDrawElementsInstanced(mode: Int, count: Int, type: Int, offset: Int, instances: Int) =
        GL31C.glDrawElementsInstanced(mode, count, type, offset.toLong(), instances)
    fun glDrawElementsIndirect(mode: Int, type: Int, offset: Int) =
        GL40C.glDrawElementsIndirect(mode, type, offset.toLong())
    fun glDrawBuffers(n: Int, bufs: IntArray, offset: Int) =
        GL20C.glDrawBuffers(bufs.copyOfRange(offset, offset + n))
    fun glReadBuffer(src: Int) = GL11C.glReadBuffer(src)

    // ---- info ----
    fun glGetString(name: Int): String? =
        if (name == 0x1F03) DesktopGl.extensions() else GL11C.glGetString(name)
    fun glGetIntegerv(pname: Int, out: IntArray, offset: Int) { out[offset] = GL11C.glGetInteger(pname) }
    fun glGetFloatv(pname: Int, out: FloatArray, offset: Int) { out[offset] = GL11C.glGetFloat(pname) }

    // ---- buffer ----
    fun glGenBuffers(n: Int, ids: IntArray, offset: Int) { for (i in 0 until n) ids[offset + i] = GL15C.glGenBuffers() }
    fun glDeleteBuffers(n: Int, ids: IntArray, offset: Int) { for (i in 0 until n) GL15C.glDeleteBuffers(ids[offset + i]) }
    fun glBindBuffer(target: Int, buffer: Int) = GL15C.glBindBuffer(target, buffer)
    fun glBindBufferBase(target: Int, index: Int, buffer: Int) = GL30C.glBindBufferBase(target, index, buffer)
    fun glBufferData(target: Int, size: Int, data: Buffer?, usage: Int) {
        withAddr(data) { GL15C.nglBufferData(target, size.toLong(), it, usage) }
    }
    fun glBufferSubData(target: Int, offset: Int, size: Int, data: Buffer?) {
        withAddr(data) { GL15C.nglBufferSubData(target, offset.toLong(), size.toLong(), it) }
    }

    // ---- vertex array ----
    fun glGenVertexArrays(n: Int, ids: IntArray, offset: Int) { for (i in 0 until n) ids[offset + i] = GL30C.glGenVertexArrays() }
    fun glBindVertexArray(vao: Int) = GL30C.glBindVertexArray(if (vao == 0) DesktopGl.defaultVao else vao)
    fun glEnableVertexAttribArray(index: Int) = GL20C.glEnableVertexAttribArray(index)
    fun glVertexAttribPointer(index: Int, size: Int, type: Int, normalized: Boolean, stride: Int, offset: Int) =
        GL20C.glVertexAttribPointer(index, size, type, normalized, stride, offset.toLong())
    fun glVertexAttribDivisor(index: Int, divisor: Int) = GL33C.glVertexAttribDivisor(index, divisor)

    // ---- texture ----
    fun glGenTextures(n: Int, ids: IntArray, offset: Int) { for (i in 0 until n) ids[offset + i] = GL11C.glGenTextures() }
    fun glDeleteTextures(n: Int, ids: IntArray, offset: Int) { for (i in 0 until n) GL11C.glDeleteTextures(ids[offset + i]) }
    fun glBindTexture(target: Int, tex: Int) = GL11C.glBindTexture(target, tex)
    fun glActiveTexture(unit: Int) = GL13C.glActiveTexture(unit)
    fun glTexParameteri(target: Int, pname: Int, param: Int) = GL11C.glTexParameteri(target, pname, param)
    fun glTexParameterf(target: Int, pname: Int, param: Float) = GL11C.glTexParameterf(target, pname, param)
    fun glTexStorage2D(target: Int, levels: Int, internalFormat: Int, w: Int, h: Int) =
        GL42C.glTexStorage2D(target, levels, internalFormat, w, h)
    fun glTexStorage3D(target: Int, levels: Int, internalFormat: Int, w: Int, h: Int, d: Int) =
        GL42C.glTexStorage3D(target, levels, internalFormat, w, h, d)
    fun glTexSubImage3D(
        target: Int, level: Int, xo: Int, yo: Int, zo: Int, w: Int, h: Int, d: Int,
        format: Int, type: Int, pixels: Buffer?
    ) {
        withAddr(pixels) { GL12C.nglTexSubImage3D(target, level, xo, yo, zo, w, h, d, format, type, it) }
    }
    fun glGenerateMipmap(target: Int) = GL30C.glGenerateMipmap(target)

    // ---- framebuffer ----
    fun glGenFramebuffers(n: Int, ids: IntArray, offset: Int) { for (i in 0 until n) ids[offset + i] = GL30C.glGenFramebuffers() }
    fun glDeleteFramebuffers(n: Int, ids: IntArray, offset: Int) { for (i in 0 until n) GL30C.glDeleteFramebuffers(ids[offset + i]) }
    fun glBindFramebuffer(target: Int, fbo: Int) = GL30C.glBindFramebuffer(target, fbo)
    fun glFramebufferTexture2D(target: Int, attachment: Int, texTarget: Int, tex: Int, level: Int) =
        GL30C.glFramebufferTexture2D(target, attachment, texTarget, tex, level)
    fun glFramebufferTextureLayer(target: Int, attachment: Int, tex: Int, level: Int, layer: Int) =
        GL30C.glFramebufferTextureLayer(target, attachment, tex, level, layer)
    fun glCheckFramebufferStatus(target: Int): Int = GL30C.glCheckFramebufferStatus(target)

    // ---- shader & program ----
    fun glCreateShader(type: Int): Int = GL20C.glCreateShader(type)
    fun glShaderSource(shader: Int, src: String) = GL20C.glShaderSource(shader, DesktopGl.toDesktopGlsl(src))
    fun glCompileShader(shader: Int) = GL20C.glCompileShader(shader)
    fun glGetShaderiv(shader: Int, pname: Int, out: IntArray, offset: Int) { out[offset] = GL20C.glGetShaderi(shader, pname) }
    fun glGetShaderInfoLog(shader: Int): String = GL20C.glGetShaderInfoLog(shader)
    fun glDeleteShader(shader: Int) = GL20C.glDeleteShader(shader)
    fun glCreateProgram(): Int = GL20C.glCreateProgram()
    fun glAttachShader(program: Int, shader: Int) = GL20C.glAttachShader(program, shader)
    fun glLinkProgram(program: Int) = GL20C.glLinkProgram(program)
    fun glGetProgramiv(program: Int, pname: Int, out: IntArray, offset: Int) { out[offset] = GL20C.glGetProgrami(program, pname) }
    fun glGetProgramInfoLog(program: Int): String = GL20C.glGetProgramInfoLog(program)
    fun glDeleteProgram(program: Int) = GL20C.glDeleteProgram(program)
    fun glUseProgram(program: Int) = GL20C.glUseProgram(program)
    fun glGetUniformLocation(program: Int, name: String): Int = GL20C.glGetUniformLocation(program, name)
    fun glGetUniformBlockIndex(program: Int, name: String): Int = GL31C.glGetUniformBlockIndex(program, name)
    fun glUniformBlockBinding(program: Int, index: Int, binding: Int) = GL31C.glUniformBlockBinding(program, index, binding)

    // ---- uniform ----
    fun glUniform1f(loc: Int, x: Float) = GL20C.glUniform1f(loc, x)
    fun glUniform1i(loc: Int, x: Int) = GL20C.glUniform1i(loc, x)
    fun glUniform1ui(loc: Int, x: Int) = GL30C.glUniform1ui(loc, x)
    fun glUniform2f(loc: Int, x: Float, y: Float) = GL20C.glUniform2f(loc, x, y)
    fun glUniform3f(loc: Int, x: Float, y: Float, z: Float) = GL20C.glUniform3f(loc, x, y, z)
    fun glUniform4f(loc: Int, x: Float, y: Float, z: Float, w: Float) = GL20C.glUniform4f(loc, x, y, z, w)
    fun glUniform4fv(loc: Int, count: Int, v: FloatArray, offset: Int) =
        GL20C.glUniform4fv(loc, v.copyOfRange(offset, offset + 4 * count))
    fun glUniformMatrix4fv(loc: Int, count: Int, transpose: Boolean, v: FloatArray, offset: Int) =
        GL20C.glUniformMatrix4fv(loc, transpose, v.copyOfRange(offset, offset + 16 * count))

    // ---- compute (butuh OpenGL 4.3) ----
    fun glDispatchCompute(x: Int, y: Int, z: Int) = GL43C.glDispatchCompute(x, y, z)
    fun glMemoryBarrier(barriers: Int) = GL42C.glMemoryBarrier(barriers)
}

object GLES20 : GLBase()
object GLES30 : GLBase()
object GLES31 : GLBase()

/** Pengganti android.opengl.GLSurfaceView: yang dipakai game cuma interface Renderer-nya. */
open class GLSurfaceView {
    interface Renderer {
        fun onSurfaceCreated(gl: GL10?, config: EGLConfig?)
        fun onSurfaceChanged(gl: GL10?, width: Int, height: Int)
        fun onDrawFrame(gl: GL10?)
    }
}
