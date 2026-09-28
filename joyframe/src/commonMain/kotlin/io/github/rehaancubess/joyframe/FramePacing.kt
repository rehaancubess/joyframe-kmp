// SPDX-License-Identifier: Apache-2.0
package io.github.rehaancubess.joyframe

/**
 * Measures what players feel: the interval between frames, not an average FPS. A late frame is one
 * longer than one and a half target intervals (25 ms at 60 Hz), the threshold the source game used
 * to find its audio stutter. Call [record] with every frame's timestamp and read [report] a couple
 * of times a second.
 *
 * On Android, measure the loop that advances your game. A GL thread that re-presents a stale frame
 * can report a perfect 60 while the game state is stuck, so the display alone can hide a problem.
 */
class FramePacing(val windowSeconds: Float = 2f, val targetHz: Float = 60f) {
    init { require(windowSeconds.isFinite() && windowSeconds > 0f && targetHz.isFinite() && targetHz > 0f) }

    private val intervals = FloatArray(CAPACITY)
    private var start = 0
    private var count = 0
    private var total = 0f
    private var last: Long? = null

    fun record(frameNanos: Long) {
        val previous = last
        last = frameNanos
        if (previous == null || frameNanos <= previous) return
        val ms = (frameNanos - previous) / 1_000_000f
        // A long background pause is not a dropped frame; start a fresh window instead.
        if (ms > 1_000f) { reset(); last = frameNanos; return }
        if (count == CAPACITY) drop()
        intervals[(start + count) % CAPACITY] = ms
        count++
        total += ms
        while (count > 1 && total - intervals[start] >= windowSeconds * 1000f) drop()
    }

    val report: FramePacingReport get() {
        if (count == 0) return FramePacingReport(0f, 0f, 0f, 0f, 0f, 0)
        val sorted = FloatArray(count) { intervals[(start + it) % CAPACITY] }.also { it.sort() }
        val budget = 1500f / targetHz
        fun percentile(p: Float) = sorted[((count - 1) * p).toInt()]
        return FramePacingReport(
            fps = count * 1000f / total,
            latePercent = sorted.count { it > budget } * 100f / count,
            p95Ms = percentile(.95f),
            p99Ms = percentile(.99f),
            worstMs = sorted.last(),
            frames = count,
        )
    }

    fun reset() { start = 0; count = 0; total = 0f; last = null }

    private fun drop() {
        total -= intervals[start]
        start = (start + 1) % CAPACITY
        count--
    }

    private companion object { const val CAPACITY = 2048 }
}

/** Frame intervals over the recent window. [latePercent] counts intervals over 1.5 target frames. */
data class FramePacingReport(
    val fps: Float,
    val latePercent: Float,
    val p95Ms: Float,
    val p99Ms: Float,
    val worstMs: Float,
    val frames: Int,
) {
    override fun toString(): String = "${fps.one()} fps · ${latePercent.one()}% late · p99 ${p99Ms.one()} ms · worst ${worstMs.one()} ms"
    private fun Float.one() = ((this * 10).toInt() / 10f).toString()
}
