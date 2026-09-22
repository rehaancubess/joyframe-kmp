package io.github.rehaancubess.joyframe

/** Monotonic frame delta with a capped resume gap. Reset when pausing or replacing a scene. */
class FrameClock(private val maxDeltaSeconds: Float = .05f) {
    init { require(maxDeltaSeconds.isFinite() && maxDeltaSeconds > 0f) }
    private var previous: Long? = null
    fun advance(nanos: Long): Float {
        val last = previous
        previous = nanos
        return if (last == null || nanos <= last) 0f
        else ((nanos - last) / 1_000_000_000.0).toFloat().coerceAtMost(maxDeltaSeconds)
    }
    fun reset() { previous = null }
}
