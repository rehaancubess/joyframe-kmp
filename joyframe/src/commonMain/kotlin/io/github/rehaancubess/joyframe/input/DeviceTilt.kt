// SPDX-License-Identifier: Apache-2.0
package io.github.rehaancubess.joyframe.input

/**
 * Steering by rolling the phone like a wheel. Reads gravity rather than the gyroscope: the player
 * holds an *angle*, which gravity gives directly with no drift to integrate away. The angle is taken
 * in the screen plane, so it works at any pitch, flat on a table or upright in bed. It is zeroed
 * wherever the phone is held when [start] runs, because nobody holds a phone level; [recenter]
 * takes a new neutral.
 *
 * Android needs `JoyframeAndroid.initialize(context)`. In browsers, call [start] from a tap: iOS
 * Safari asks the player for motion permission then. Desktop reports [available] = false.
 * Feed [steer] into `ActionInput.tiltSteer` each frame.
 */
expect object DeviceTilt {
    /** False where there is no gravity sensor, so tilt is only offered where it works. */
    val available: Boolean

    /** Starts listening; a full-lock turn takes [fullLockDegrees] of roll (26 reaches with the wrists). */
    fun start(fullLockDegrees: Float = TiltMath.DEFAULT_FULL_LOCK_DEGREES)

    fun stop()

    /** Takes the next reading as centre. */
    fun recenter()

    /** -1 (full left) to 1 (full right), past a small dead zone; 0 when not listening. */
    val steer: Float
}

/** The shared roll arithmetic: gravity's in-plane angle against a neutral, as a steer value. */
object TiltMath {
    const val DEFAULT_FULL_LOCK_DEGREES = 26f

    /** Ignores the hand's natural drift; below this the vehicle holds its line. */
    const val DEAD_ZONE = .08f

    /** Roll about the axis through the screen, in radians, from in-plane gravity. */
    fun roll(gravityX: Double, gravityY: Double): Float = kotlin.math.atan2(gravityX, gravityY).toFloat()

    fun steer(roll: Float, neutral: Float, fullLockDegrees: Float): Float {
        require(fullLockDegrees.isFinite() && fullLockDegrees > 0f)
        var delta = roll - neutral
        // Shortest way round, so a neutral taken near the wrap point does not invert. This also makes
        // the result immune to platforms that report gravity with both axes flipped.
        while (delta > kotlin.math.PI) delta -= (2 * kotlin.math.PI).toFloat()
        while (delta < -kotlin.math.PI) delta += (2 * kotlin.math.PI).toFloat()
        val fullLock = (fullLockDegrees * kotlin.math.PI / 180f).toFloat()
        // Negated: rolling clockwise (the right edge dipping) turns gravity's angle the other way.
        val raw = (-delta / fullLock).coerceIn(-1f, 1f)
        if (kotlin.math.abs(raw) <= DEAD_ZONE) return 0f
        // Rescaled past the dead zone so the first degree of real tilt is not a step change.
        val scaled = (kotlin.math.abs(raw) - DEAD_ZONE) / (1f - DEAD_ZONE)
        return if (raw < 0f) -scaled else scaled
    }
}

/** Shared bookkeeping for platforms that sample gravity: neutral on first reading, then steer. */
internal class TiltTracker {
    @kotlin.concurrent.Volatile var fullLockDegrees = TiltMath.DEFAULT_FULL_LOCK_DEGREES
    @kotlin.concurrent.Volatile private var neutral = Float.NaN
    @kotlin.concurrent.Volatile var steer = 0f; private set

    fun reading(gravityX: Double, gravityY: Double) {
        if (!gravityX.isFinite() || !gravityY.isFinite()) return
        val roll = TiltMath.roll(gravityX, gravityY)
        if (neutral.isNaN()) { neutral = roll; steer = 0f; return }
        steer = TiltMath.steer(roll, neutral, fullLockDegrees)
    }

    fun recenter() { neutral = Float.NaN; steer = 0f }
}
