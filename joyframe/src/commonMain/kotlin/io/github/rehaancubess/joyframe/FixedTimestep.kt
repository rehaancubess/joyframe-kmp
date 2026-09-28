// SPDX-License-Identifier: Apache-2.0
package io.github.rehaancubess.joyframe

/**
 * Runs gameplay at a constant rate whatever the display does, and reports how far rendering is
 * between the last two steps so motion can be interpolated instead of stuttering.
 *
 * ```
 * repeat(fixed.advance(step.deltaSeconds)) { world.tick(fixed.stepSeconds) }
 * instance("boat", "boat", previous = boat.previous, transform = boat.current, alpha = fixed.alpha)
 * ```
 *
 * Physics that depends on the step size (drag, springs, collisions) then behaves the same at
 * 30, 60 or 144 Hz. A long stall is capped at [maxStepsPerFrame] so a slow device drops time
 * rather than spiralling into ever-longer catch-up frames.
 */
class FixedTimestep(val stepSeconds: Float = 1f / 60f, val maxStepsPerFrame: Int = 5) {
    init {
        require(stepSeconds.isFinite() && stepSeconds > 0f) { "stepSeconds must be positive" }
        require(maxStepsPerFrame >= 1) { "maxStepsPerFrame must be at least 1" }
    }

    private var accumulator = 0f

    /** Total fixed steps taken since construction or [reset]. */
    var steps: Long = 0; private set

    /** Fraction of a step rendered past the latest one, 0..1. Pass as the instance interpolation alpha. */
    val alpha: Float get() = (accumulator / stepSeconds).coerceIn(0f, 1f)

    /** Adds [deltaSeconds] of frame time and returns how many fixed steps to simulate now. */
    fun advance(deltaSeconds: Float): Int {
        require(deltaSeconds.isFinite() && deltaSeconds >= 0f) { "deltaSeconds must be finite and non-negative" }
        accumulator += deltaSeconds
        var count = (accumulator / stepSeconds).toInt()
        if (count > maxStepsPerFrame) {
            count = maxStepsPerFrame
            accumulator = stepSeconds * maxStepsPerFrame
        }
        accumulator -= count * stepSeconds
        if (accumulator < 0f) accumulator = 0f
        steps += count
        return count
    }

    fun reset() { accumulator = 0f; steps = 0 }
}
