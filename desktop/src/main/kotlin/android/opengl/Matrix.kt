package android.opengl

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.tan

/** Tiruan android.opengl.Matrix (column-major, sama persis konvensinya dengan Android). */
object Matrix {
    fun setIdentityM(sm: FloatArray, smOffset: Int) {
        for (i in 0 until 16) sm[smOffset + i] = 0f
        for (i in 0 until 16 step 5) sm[smOffset + i] = 1f
    }

    fun multiplyMM(result: FloatArray, resultOffset: Int, lhs: FloatArray, lhsOffset: Int, rhs: FloatArray, rhsOffset: Int) {
        val t = FloatArray(16)
        for (c in 0 until 4) {
            for (r in 0 until 4) {
                var s = 0f
                for (k in 0 until 4) s += lhs[lhsOffset + k * 4 + r] * rhs[rhsOffset + c * 4 + k]
                t[c * 4 + r] = s
            }
        }
        System.arraycopy(t, 0, result, resultOffset, 16)
    }

    fun multiplyMV(resultVec: FloatArray, resultVecOffset: Int, lhsMat: FloatArray, lhsMatOffset: Int, rhsVec: FloatArray, rhsVecOffset: Int) {
        val t = FloatArray(4)
        for (r in 0 until 4) {
            var s = 0f
            for (c in 0 until 4) s += lhsMat[lhsMatOffset + c * 4 + r] * rhsVec[rhsVecOffset + c]
            t[r] = s
        }
        System.arraycopy(t, 0, resultVec, resultVecOffset, 4)
    }

    fun translateM(m: FloatArray, mOffset: Int, x: Float, y: Float, z: Float) {
        for (i in 0 until 4) {
            m[mOffset + 12 + i] += m[mOffset + i] * x + m[mOffset + 4 + i] * y + m[mOffset + 8 + i] * z
        }
    }

    fun setLookAtM(
        rm: FloatArray, rmOffset: Int,
        eyeX: Float, eyeY: Float, eyeZ: Float,
        centerX: Float, centerY: Float, centerZ: Float,
        upX: Float, upY: Float, upZ: Float
    ) {
        var fx = centerX - eyeX
        var fy = centerY - eyeY
        var fz = centerZ - eyeZ
        val rlf = 1f / sqrt(fx * fx + fy * fy + fz * fz)
        fx *= rlf; fy *= rlf; fz *= rlf

        var sx = fy * upZ - fz * upY
        var sy = fz * upX - fx * upZ
        var sz = fx * upY - fy * upX
        val rls = 1f / sqrt(sx * sx + sy * sy + sz * sz)
        sx *= rls; sy *= rls; sz *= rls

        val ux = sy * fz - sz * fy
        val uy = sz * fx - sx * fz
        val uz = sx * fy - sy * fx

        rm[rmOffset + 0] = sx;  rm[rmOffset + 1] = ux;  rm[rmOffset + 2] = -fx;  rm[rmOffset + 3] = 0f
        rm[rmOffset + 4] = sy;  rm[rmOffset + 5] = uy;  rm[rmOffset + 6] = -fy;  rm[rmOffset + 7] = 0f
        rm[rmOffset + 8] = sz;  rm[rmOffset + 9] = uz;  rm[rmOffset + 10] = -fz; rm[rmOffset + 11] = 0f
        rm[rmOffset + 12] = 0f; rm[rmOffset + 13] = 0f; rm[rmOffset + 14] = 0f;  rm[rmOffset + 15] = 1f
        translateM(rm, rmOffset, -eyeX, -eyeY, -eyeZ)
    }

    /** fovy dalam derajat, sama seperti Android. */
    fun perspectiveM(m: FloatArray, offset: Int, fovy: Float, aspect: Float, zNear: Float, zFar: Float) {
        val f = 1f / tan(fovy * (Math.PI / 360.0)).toFloat()
        val rr = 1f / (zNear - zFar)
        for (i in 0 until 16) m[offset + i] = 0f
        m[offset + 0] = f / aspect
        m[offset + 5] = f
        m[offset + 10] = (zFar + zNear) * rr
        m[offset + 11] = -1f
        m[offset + 14] = 2f * zFar * zNear * rr
    }

    fun orthoM(m: FloatArray, mOffset: Int, left: Float, right: Float, bottom: Float, top: Float, near: Float, far: Float) {
        val w = right - left
        val h = top - bottom
        val d = far - near
        for (i in 0 until 16) m[mOffset + i] = 0f
        m[mOffset + 0] = 2f / w
        m[mOffset + 5] = 2f / h
        m[mOffset + 10] = -2f / d
        m[mOffset + 12] = -(right + left) / w
        m[mOffset + 13] = -(top + bottom) / h
        m[mOffset + 14] = -(far + near) / d
        m[mOffset + 15] = 1f
    }

    /** Invers 4x4 (Gauss-Jordan, pivot parsial). false kalau matriks singular. */
    fun invertM(mInv: FloatArray, mInvOffset: Int, m: FloatArray, mOffset: Int): Boolean {
        val a = Array(4) { DoubleArray(8) }
        for (r in 0 until 4) {
            for (c in 0 until 4) a[r][c] = m[mOffset + c * 4 + r].toDouble()
            a[r][4 + r] = 1.0
        }
        for (col in 0 until 4) {
            var piv = col
            var best = abs(a[col][col])
            for (r in col + 1 until 4) {
                if (abs(a[r][col]) > best) {
                    best = abs(a[r][col])
                    piv = r
                }
            }
            if (best < 1e-12) return false
            if (piv != col) {
                val tmp = a[piv]
                a[piv] = a[col]
                a[col] = tmp
            }
            val d = a[col][col]
            for (c in 0 until 8) a[col][c] /= d
            for (r in 0 until 4) {
                if (r == col) continue
                val f = a[r][col]
                if (f != 0.0) for (c in 0 until 8) a[r][c] -= f * a[col][c]
            }
        }
        for (r in 0 until 4) {
            for (c in 0 until 4) mInv[mInvOffset + c * 4 + r] = a[r][4 + c].toFloat()
        }
        return true
    }
}
