// SPDX-License-Identifier: Apache-2.0
package io.github.rehaancubess.joyframe.render.math

import kotlin.math.sqrt

data class Vec3(val x: Float = 0f, val y: Float = 0f, val z: Float = 0f) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    operator fun unaryMinus() = Vec3(-x, -y, -z)

    fun length() = sqrt(x * x + y * y + z * z)

    fun normalized(): Vec3 {
        val len = length()
        return if (len < 1e-6f) Vec3() else this * (1f / len)
    }

    fun cross(o: Vec3) = Vec3(
        y * o.z - z * o.y,
        z * o.x - x * o.z,
        x * o.y - y * o.x,
    )

    fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z

    companion object {
        val ZERO = Vec3()
        val UP = Vec3(0f, 1f, 0f)
    }
}
