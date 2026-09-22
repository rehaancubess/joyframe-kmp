// SPDX-License-Identifier: Apache-2.0
package io.github.rehaancubess.joyframe.render.math

import io.github.rehaancubess.joyframe.render.gpu.Bounds3
import kotlin.math.abs

internal class BoundsFrustum {
    private val planes = FloatArray(24)

    fun update(viewProjection: FloatArray, depthRange: DepthRange) {
        for (plane in 0 until 6) {
            val row = plane / 2
            val sign = if (plane % 2 == 0) 1f else -1f
            for (column in 0 until 4) {
                val base = column * 4
                planes[plane * 4 + column] = if (plane == 4 && depthRange == DepthRange.ZeroToOne) {
                    viewProjection[base + 2]
                } else {
                    viewProjection[base + 3] + sign * viewProjection[base + row]
                }
            }
        }
    }

    fun intersects(bounds: Bounds3, model: FloatArray): Boolean {
        val cx = (bounds.minimum.x + bounds.maximum.x) * .5f
        val cy = (bounds.minimum.y + bounds.maximum.y) * .5f
        val cz = (bounds.minimum.z + bounds.maximum.z) * .5f
        val hx = (bounds.maximum.x - bounds.minimum.x) * .5f
        val hy = (bounds.maximum.y - bounds.minimum.y) * .5f
        val hz = (bounds.maximum.z - bounds.minimum.z) * .5f
        val x = model[0] * cx + model[4] * cy + model[8] * cz + model[12]
        val y = model[1] * cx + model[5] * cy + model[9] * cz + model[13]
        val z = model[2] * cx + model[6] * cy + model[10] * cz + model[14]
        for (plane in 0 until 6) {
            val i = plane * 4
            val a = planes[i]; val b = planes[i + 1]; val c = planes[i + 2]
            val radius = hx * abs(a * model[0] + b * model[1] + c * model[2]) +
                hy * abs(a * model[4] + b * model[5] + c * model[6]) +
                hz * abs(a * model[8] + b * model[9] + c * model[10])
            if (a * x + b * y + c * z + planes[i + 3] < -radius) return false
        }
        return true
    }
}
