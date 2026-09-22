// SPDX-License-Identifier: Apache-2.0
package io.github.rehaancubess.joyframe.render.math

import kotlin.math.tan

class Mat4(private val m: FloatArray = FloatArray(16) { if (it % 5 == 0) 1f else 0f }) {
    data class ClipPoint(val ndc: Vec3, val clipW: Float, val viewZ: Float)

    fun transform(v: Vec3, w: Float = 1f): FloatArray {
        val x = m[0] * v.x + m[4] * v.y + m[8] * v.z + m[12] * w
        val y = m[1] * v.x + m[5] * v.y + m[9] * v.z + m[13] * w
        val z = m[2] * v.x + m[6] * v.y + m[10] * v.z + m[14] * w
        val nw = m[3] * v.x + m[7] * v.y + m[11] * v.z + m[15] * w
        return floatArrayOf(x, y, z, nw)
    }

    fun projectSafe(v: Vec3): ClipPoint? {
        val c = transform(v)
        val w = c[3]
        if (w <= 0.05f) return null
        return ClipPoint(
            ndc = Vec3(c[0] / w, c[1] / w, c[2] / w),
            clipW = w,
            viewZ = w, // with our projection, clip.w ≈ -viewZ; larger w = farther is wrong
        )
    }

    fun multiply(other: Mat4): Mat4 {
        val r = FloatArray(16)
        for (col in 0 until 4) {
            for (row in 0 until 4) {
                var sum = 0f
                for (k in 0 until 4) {
                    sum += m[k * 4 + row] * other.m[col * 4 + k]
                }
                r[col * 4 + row] = sum
            }
        }
        return Mat4(r)
    }

    companion object {
        fun lookAt(eye: Vec3, target: Vec3, up: Vec3 = Vec3.UP): Mat4 {
            val z = (eye - target).normalized()
            var x = up.cross(z)
            if (x.length() < 1e-5f) {
                x = Vec3(1f, 0f, 0f).cross(z)
            }
            x = x.normalized()
            val y = z.cross(x)
            val m = FloatArray(16)
            m[0] = x.x; m[4] = x.y; m[8] = x.z; m[12] = -x.dot(eye)
            m[1] = y.x; m[5] = y.y; m[9] = y.z; m[13] = -y.dot(eye)
            m[2] = z.x; m[6] = z.y; m[10] = z.z; m[14] = -z.dot(eye)
            m[3] = 0f; m[7] = 0f; m[11] = 0f; m[15] = 1f
            return Mat4(m)
        }

        fun perspective(fovYDegrees: Float, aspect: Float, near: Float, far: Float): Mat4 {
            val f = 1f / tan((fovYDegrees * 0.5f) * (kotlin.math.PI.toFloat() / 180f))
            val m = FloatArray(16)
            m[0] = f / aspect
            m[5] = f
            m[10] = (far + near) / (near - far)
            m[11] = -1f
            m[14] = (2f * far * near) / (near - far)
            m[15] = 0f
            return Mat4(m)
        }
    }
}
