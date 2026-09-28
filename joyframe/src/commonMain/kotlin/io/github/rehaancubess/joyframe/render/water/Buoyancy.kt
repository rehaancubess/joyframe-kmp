// SPDX-License-Identifier: Apache-2.0
package io.github.rehaancubess.joyframe.render.water

import io.github.rehaancubess.joyframe.render.gpu.Transform3D
import io.github.rehaancubess.joyframe.render.math.Vec3
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Makes a hull ride the same waves the water shader draws: heave from the surface height, pitch and
 * roll from its slope. The height/slope queries are the CPU mirror of the GPU swell, so the hull and
 * the water can never disagree.
 *
 * Orientation follows Joyframe's convention for vehicles: the model's bow faces +X and
 * `Transform3D.rotation.y` turns it, so yaw 0 heads along +X and π/2 heads along −Z.
 */
object Buoyancy {
    /**
     * Pose for a hull at ([x], [z]) turned to [yawRadians].
     *
     * With [length] and [beam] of zero the hull follows the slope at its centre, which is lively and
     * suits small boats. Give the hull's real length and beam to sample bow, stern and both sides
     * instead: waves shorter than the hull then average out, as they would on a real ship.
     * [energy] maps a position to wave energy (for example a `WaterField` shoreline ramp).
     * [lift] raises the model so its waterline sits on the surface.
     */
    fun pose(
        water: WaterConfig,
        x: Float,
        z: Float,
        yawRadians: Float,
        seconds: Float,
        lift: Float = 0f,
        scale: Float = 1f,
        length: Float = 0f,
        beam: Float = 0f,
        energy: (x: Float, z: Float) -> Float = { _, _ -> 1f },
    ): Transform3D {
        require(listOf(x, z, yawRadians, seconds, lift, scale, length, beam).all { it.isFinite() })
        require(length >= 0f && beam >= 0f && scale > 0f)
        val forwardX = cos(yawRadians)
        val forwardZ = -sin(yawRadians)
        // Starboard is forward turned a quarter clockwise seen from above.
        val sideX = -forwardZ
        val sideZ = forwardX
        fun height(px: Float, pz: Float) = water.heightAt(px, pz, seconds, energy(px, pz))
        val pitch: Float
        val roll: Float
        val heave: Float
        if (length <= 0f && beam <= 0f) {
            val slope = water.slopeAt(x, z, seconds, energy(x, z))
            pitch = atan(slope.alongX * forwardX + slope.alongZ * forwardZ)
            roll = -atan(slope.alongX * sideX + slope.alongZ * sideZ)
            heave = height(x, z)
        } else {
            val halfLength = length * .5f
            val halfBeam = beam * .5f
            val bow = height(x + forwardX * halfLength, z + forwardZ * halfLength)
            val stern = height(x - forwardX * halfLength, z - forwardZ * halfLength)
            val starboard = height(x + sideX * halfBeam, z + sideZ * halfBeam)
            val port = height(x - sideX * halfBeam, z - sideZ * halfBeam)
            pitch = if (length > 0f) atan2(bow - stern, length) else 0f
            roll = if (beam > 0f) -atan2(starboard - port, beam) else 0f
            heave = (bow + stern + starboard + port + height(x, z) * 2f) / 6f
        }
        return Transform3D(Vec3(x, heave + lift, z), Vec3(roll, yawRadians, pitch), Vec3(scale, scale, scale))
    }
}

/**
 * A whirlpool's pull, independent of how it is drawn. Suction is zero at [radius], rises as a square
 * so the rim is a warning rather than a shove, and a hull inside [coreRadius] is swallowed.
 * Accelerations are world units per second squared, applied by the caller's own physics.
 */
data class Whirlpool(
    val x: Float,
    val z: Float,
    val radius: Float = 420f,
    val coreRadius: Float = 90f,
    val pull: Float = 980f,
    val swirl: Float = 380f,
    val clockwise: Boolean = true,
) {
    init {
        require(listOf(x, z, radius, coreRadius, pull, swirl).all { it.isFinite() })
        require(radius > coreRadius && coreRadius >= 0f) { "radius must exceed coreRadius" }
    }

    fun distanceTo(px: Float, pz: Float): Float {
        val dx = px - x
        val dz = pz - z
        return kotlin.math.sqrt(dx * dx + dz * dz)
    }

    fun swallows(px: Float, pz: Float): Boolean = distanceTo(px, pz) <= coreRadius

    /** Suction 0..1 at a point, peaking at the core edge. */
    fun influenceAt(px: Float, pz: Float): Float {
        val distance = distanceTo(px, pz)
        if (distance >= radius) return 0f
        val into = ((radius - maxOf(distance, coreRadius)) / (radius - coreRadius)).coerceIn(0f, 1f)
        return into * into
    }

    /**
     * Velocity change over [deltaSeconds] for a hull at a point: inward pull plus a tangential swirl
     * that carries it around the eye. Returns (dx, dz). Swirl only starts inside the bowl, so skimming
     * the rim does not yank steering.
     */
    fun velocityChange(px: Float, pz: Float, deltaSeconds: Float): Pair<Float, Float> {
        val influence = influenceAt(px, pz)
        val distance = distanceTo(px, pz)
        if (influence < .05f || distance < 1e-3f) return 0f to 0f
        val inX = (x - px) / distance
        val inZ = (z - pz) / distance
        val sign = if (clockwise) 1f else -1f
        // Same turning sense as the dish Primitives.whirlpool draws for this `clockwise` flag.
        val spinX = -inZ * sign
        val spinZ = inX * sign
        val spin = if (influence >= .1f) swirl * influence else 0f
        return (inX * pull * influence + spinX * spin) * deltaSeconds to
            (inZ * pull * influence + spinZ * spin) * deltaSeconds
    }

    /** Shader reaction for a hull near this whirlpool; pass as the frame's `vortexSurge`. */
    fun surge(hullX: Float, hullZ: Float): VortexSurge =
        VortexSurge(hullX = hullX, hullZ = hullZ, hullSuction = influenceAt(hullX, hullZ))
}
