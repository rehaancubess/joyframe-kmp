// SPDX-License-Identifier: Apache-2.0
package io.github.rehaancubess.joyframe.render

import io.github.rehaancubess.joyframe.render.gpu.GpuCamera
import io.github.rehaancubess.joyframe.render.math.Vec3
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * Framing for [ChaseCamera]. The defaults are the arcade chase from the game Joyframe was extracted
 * from: close behind and pitched down, so the vehicle sits in the lower third and the horizon rides high.
 *
 * Two groups of numbers do the work and are easy to confuse. The boom ([distance], [height]) sets how
 * large the target reads. The aim point ([lookAhead], [lookHeight]) sets the pitch, and therefore where
 * the horizon sits: aiming lower or nearer tips the camera down. Shortening the boom alone just zooms.
 *
 * Distances suit a vehicle roughly 60–80 world units long; use [scaled] for bigger or smaller models.
 */
data class ChaseCameraStyle(
    val distance: Float = 168f,
    val height: Float = 134f,
    val lookAhead: Float = 148f,
    val lookHeight: Float = 6f,
    val fovDegrees: Float = 52f,
    /** Extra field of view at full speed: reads as acceleration without moving the boom. */
    val speedFov: Float = 4f,
    val speedDistance: Float = 14f,
    val speedHeight: Float = 6f,
    val speedLookAhead: Float = 22f,
    /** Exponential follow rates per second. The aim point follows tighter than the eye. */
    val eyeResponse: Float = 9f,
    val targetResponse: Float = 12f,
    val nearPlane: Float = 16f,
    val farPlane: Float = 6_500f,
) {
    init {
        val lengths = listOf(distance, height, lookAhead, lookHeight, speedDistance, speedHeight, speedLookAhead)
        require(lengths.all { it.isFinite() }) { "Chase camera lengths must be finite" }
        require(fovDegrees.isFinite() && fovDegrees in 1f..170f && fovDegrees + speedFov in 1f..170f)
        require(eyeResponse.isFinite() && eyeResponse > 0f && targetResponse.isFinite() && targetResponse > 0f)
        require(nearPlane.isFinite() && nearPlane > 0f && farPlane.isFinite() && farPlane > nearPlane)
    }

    /** Every length multiplied by [factor]; angles and response rates are unchanged. */
    fun scaled(factor: Float): ChaseCameraStyle {
        require(factor.isFinite() && factor > 0f)
        return copy(
            distance = distance * factor, height = height * factor,
            lookAhead = lookAhead * factor, lookHeight = lookHeight * factor,
            speedDistance = speedDistance * factor, speedHeight = speedHeight * factor,
            speedLookAhead = speedLookAhead * factor,
            nearPlane = nearPlane * factor, farPlane = farPlane * factor,
        )
    }
}

/**
 * Retained follow camera. A fixed-step simulation makes the desired pose jump whenever the heading
 * does; snapping a 3D camera to it every frame reads as jitter, so this damps toward it in render time
 * and adds a short local-only [shake]. Nothing here feeds back into gameplay.
 *
 * Keep one instance per view (split-screen panes each need their own), call [update] once per rendered
 * frame, and call [cut] after a teleport or reset so the camera does not fly across the scene.
 * Not thread-safe.
 */
class ChaseCamera(var style: ChaseCameraStyle = ChaseCameraStyle()) {
    private var eye: Vec3? = null
    private var target = Vec3.ZERO
    private var heading = Vec3(1f, 0f, 0f)
    private var clock = 0f
    private var shakeStrength = 0f
    private var shakeAge = SHAKE_SECONDS
    private var shakeSeed = 0

    /** The pose the camera is heading for, without damping or shake. [speed] is 0..1 of top speed. */
    fun desired(position: Vec3, forward: Vec3, speed: Float = 0f): GpuCamera {
        val flat = Vec3(forward.x, 0f, forward.z).normalized()
        if (flat.length() > 0f) heading = flat
        val s = speed.let { if (it.isFinite()) it.coerceIn(0f, 1f) else 0f }
        val eye = position - heading * (style.distance + s * style.speedDistance) +
            Vec3(0f, style.height + s * style.speedHeight, 0f)
        val target = position + heading * (style.lookAhead + s * style.speedLookAhead) + Vec3(0f, style.lookHeight, 0f)
        return GpuCamera(eye, target, verticalFovDegrees = style.fovDegrees + s * style.speedFov,
            nearPlane = style.nearPlane, farPlane = style.farPlane)
    }

    /**
     * Advances the retained pose by [deltaSeconds] of render time and returns the camera to draw with.
     * A gap longer than a fifth of a second (a resume, a hitch) cuts instead of sweeping.
     */
    fun update(position: Vec3, forward: Vec3, speed: Float, deltaSeconds: Float): GpuCamera {
        require(deltaSeconds.isFinite() && deltaSeconds >= 0f) { "deltaSeconds must be finite and non-negative" }
        val wanted = desired(position, forward, speed)
        val current = eye
        if (current == null || deltaSeconds > CUT_GAP_SECONDS) {
            eye = wanted.eye
            target = wanted.target
        } else {
            val dt = deltaSeconds.coerceAtMost(MAX_STEP_SECONDS)
            eye = current + (wanted.eye - current) * (1f - exp(-style.eyeResponse * dt))
            target += (wanted.target - target) * (1f - exp(-style.targetResponse * dt))
        }
        clock += deltaSeconds
        shakeAge += deltaSeconds
        val offset = shakeOffset()
        // Keep the aim point almost still so a punch reads as the camera, not as the view drifting.
        return wanted.copy(eye = eye!! + offset, target = target + offset * .12f)
    }

    /** Snap to the desired pose on the next [update]. Use after respawns, resets and scene changes. */
    fun cut() { eye = null; shakeAge = SHAKE_SECONDS }

    /**
     * A short camera punch of up to [strength] world units with a cubic falloff, gone within a quarter
     * of a second so it never fights aiming. A weaker shake does not interrupt a stronger one.
     */
    fun shake(strength: Float) {
        require(strength.isFinite() && strength >= 0f)
        if (strength >= currentShake()) {
            shakeStrength = strength
            shakeAge = 0f
            shakeSeed++
        }
    }

    private fun currentShake(): Float {
        val decay = (1f - shakeAge / SHAKE_SECONDS).coerceIn(0f, 1f)
        return shakeStrength * decay * decay * decay
    }

    private fun shakeOffset(): Vec3 {
        val amplitude = currentShake()
        if (amplitude <= 0f) return Vec3.ZERO
        val phase = clock * 78f + shakeSeed * 2.17f
        return Vec3(cos(phase) * amplitude, sin(phase * 1.31f) * amplitude * .38f, sin(phase * .73f) * amplitude * .28f)
    }

    companion object {
        private const val SHAKE_SECONDS = .22f
        private const val CUT_GAP_SECONDS = .2f
        private const val MAX_STEP_SECONDS = .05f

        /** Forward direction for a model whose bow faces +X and is turned by `Transform3D.rotation.y`. */
        fun forwardFromYaw(yawRadians: Float): Vec3 = Vec3(cos(yawRadians), 0f, -sin(yawRadians))
    }
}

/**
 * How a sound at [source] is heard from this camera: louder when near, panned toward the side it is on.
 * Pass the result to `AudioPlayer.play(id, mix)`. [range] is the distance at which it falls silent.
 */
fun GpuCamera.hear(source: Vec3, range: Float): io.github.rehaancubess.joyframe.audio.SpatialMix =
    io.github.rehaancubess.joyframe.audio.SpatialMix.of(eye.x, eye.z, target.x - eye.x, target.z - eye.z,
        source.x, source.z, range)
