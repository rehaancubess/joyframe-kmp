// SPDX-License-Identifier: Apache-2.0
package io.github.rehaancubess.joyframe.render.math

import io.github.rehaancubess.joyframe.render.gpu.Transform3D
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

enum class DepthRange {

    ZeroToOne,


    NegativeOneToOne,
}

internal object Mat {
    const val SIZE = 16

    fun identity(out: FloatArray) {
        for (i in 0 until SIZE) out[i] = 0f
        out[0] = 1f; out[5] = 1f; out[10] = 1f; out[15] = 1f
    }


    fun multiply(a: FloatArray, b: FloatArray, out: FloatArray) {
        for (column in 0 until 4) {
            val c = column * 4
            val b0 = b[c]; val b1 = b[c + 1]; val b2 = b[c + 2]; val b3 = b[c + 3]
            out[c] = a[0] * b0 + a[4] * b1 + a[8] * b2 + a[12] * b3
            out[c + 1] = a[1] * b0 + a[5] * b1 + a[9] * b2 + a[13] * b3
            out[c + 2] = a[2] * b0 + a[6] * b1 + a[10] * b2 + a[14] * b3
            out[c + 3] = a[3] * b0 + a[7] * b1 + a[11] * b2 + a[15] * b3
        }
    }


    fun compose(transform: Transform3D, model: FloatArray, normal: FloatArray) {
        val cx = cos(transform.rotation.x); val sx = sin(transform.rotation.x)
        val cy = cos(transform.rotation.y); val sy = sin(transform.rotation.y)
        val cz = cos(transform.rotation.z); val sz = sin(transform.rotation.z)

        val r00 = cy * cz
        val r01 = sy * sx - cy * sz * cx
        val r02 = cy * sz * sx + sy * cx
        val r10 = sz
        val r11 = cz * cx
        val r12 = -cz * sx
        val r20 = -sy * cz
        val r21 = sy * sz * cx + cy * sx
        val r22 = cy * cx - sy * sz * sx

        val sxScale = transform.scale.x
        val syScale = transform.scale.y
        val szScale = transform.scale.z

        model[0] = r00 * sxScale; model[1] = r10 * sxScale; model[2] = r20 * sxScale; model[3] = 0f
        model[4] = r01 * syScale; model[5] = r11 * syScale; model[6] = r21 * syScale; model[7] = 0f
        model[8] = r02 * szScale; model[9] = r12 * szScale; model[10] = r22 * szScale; model[11] = 0f
        model[12] = transform.translation.x
        model[13] = transform.translation.y
        model[14] = transform.translation.z
        model[15] = 1f
        val ix = if (sxScale > 1e-5f || sxScale < -1e-5f) 1f / sxScale else 0f
        val iy = if (syScale > 1e-5f || syScale < -1e-5f) 1f / syScale else 0f
        val iz = if (szScale > 1e-5f || szScale < -1e-5f) 1f / szScale else 0f

        normal[0] = r00 * ix; normal[1] = r10 * ix; normal[2] = r20 * ix; normal[3] = 0f
        normal[4] = r01 * iy; normal[5] = r11 * iy; normal[6] = r21 * iy; normal[7] = 0f
        normal[8] = r02 * iz; normal[9] = r12 * iz; normal[10] = r22 * iz; normal[11] = 0f
        normal[12] = 0f; normal[13] = 0f; normal[14] = 0f; normal[15] = 1f
    }


    fun lookAt(eye: Vec3, target: Vec3, up: Vec3, out: FloatArray) {
        var z = eye - target
        val zLength = z.length()
        z = if (zLength > 1e-5f) Vec3(z.x / zLength, z.y / zLength, z.z / zLength) else Vec3(0f, 0f, 1f)
        var x = up.cross(z)
        if (x.length() < 1e-5f) x = Vec3(1f, 0f, 0f).cross(z)
        x = x.normalized()
        val y = z.cross(x)

        out[0] = x.x; out[4] = x.y; out[8] = x.z; out[12] = -x.dot(eye)
        out[1] = y.x; out[5] = y.y; out[9] = y.z; out[13] = -y.dot(eye)
        out[2] = z.x; out[6] = z.y; out[10] = z.z; out[14] = -z.dot(eye)
        out[3] = 0f; out[7] = 0f; out[11] = 0f; out[15] = 1f
    }


    fun perspective(
        fovYDegrees: Float,
        aspect: Float,
        near: Float,
        far: Float,
        depthRange: DepthRange,
        out: FloatArray,
    ) {
        val focal = 1f / tan(fovYDegrees * 0.5f * (PI_F / 180f))
        for (i in 0 until SIZE) out[i] = 0f
        out[0] = focal / aspect.coerceAtLeast(0.01f)
        out[5] = focal
        out[11] = -1f
        when (depthRange) {
            DepthRange.ZeroToOne -> {
                out[10] = far / (near - far)
                out[14] = far * near / (near - far)
            }
            DepthRange.NegativeOneToOne -> {
                out[10] = (far + near) / (near - far)
                out[14] = 2f * far * near / (near - far)
            }
        }
    }


    fun orthographic(
        halfWidth: Float,
        halfHeight: Float,
        near: Float,
        far: Float,
        depthRange: DepthRange,
        out: FloatArray,
    ) {
        for (i in 0 until SIZE) out[i] = 0f
        out[0] = 1f / halfWidth
        out[5] = 1f / halfHeight
        out[15] = 1f
        when (depthRange) {
            DepthRange.ZeroToOne -> {
                out[10] = -1f / (far - near)
                out[14] = -near / (far - near)
            }
            DepthRange.NegativeOneToOne -> {
                out[10] = -2f / (far - near)
                out[14] = -(far + near) / (far - near)
            }
        }
    }

    private const val PI_F = 3.1415927f
}
