// SPDX-License-Identifier: Apache-2.0
package io.github.rehaancubess.joyframe.render

import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * How much a view renders and how it asks the device to run. The defaults are what the source game
 * measured on mid-range phones, not guesses:
 *
 * - [renderScale]: an Adreno 618 at 1080x2400 was fragment-bound; rendering at 0.8 and letting the
 *   compositor upscale took the GPU from ~90% to ~70% busy and frames back onto single vsyncs.
 *   Android defaults to 0.8; other platforms to 1.
 * - [maxLongEdgePixels] / [maxPixels]: an iPhone 15 Plus drawable drops from 2796x1290 to 1920x885,
 *   about 53% fewer shaded pixels, with the HUD and touch still at native resolution.
 * - [holdSixtyHz] (Android): on a 120 Hz panel the frame clock otherwise runs everything twice as
 *   often, the phone throttles, and presents land on uneven 8/17/25 ms intervals.
 * - [sustainedPerformance] (Android): asks for a clock the device can hold instead of boosting past
 *   its thermal envelope and landing a late frame on every fallback.
 *
 * Use [Full] to render every native pixel at the panel's own refresh rate.
 */
data class GameViewOptions(
    val renderScale: Float = platformRenderScale,
    val maxLongEdgePixels: Int = 1920,
    val maxPixels: Int = 2_073_600,
    val holdSixtyHz: Boolean = true,
    val sustainedPerformance: Boolean = true,
) {
    init {
        require(renderScale.isFinite() && renderScale in .25f..1f) { "renderScale must be in 0.25..1" }
        require(maxLongEdgePixels > 0 && maxPixels > 0)
    }

    /** The buffer size to render for a view of [width] x [height] native pixels. */
    fun renderSize(width: Int, height: Int): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return 1 to 1
        val cap = min(maxLongEdgePixels.toDouble() / maxOf(width, height),
            sqrt(maxPixels.toDouble() / (width.toDouble() * height)))
        val scale = min(renderScale.toDouble(), cap).coerceAtMost(1.0)
        return (width * scale).roundToInt().coerceAtLeast(1) to (height * scale).roundToInt().coerceAtLeast(1)
    }

    companion object {
        val Default = GameViewOptions()
        val Full = GameViewOptions(renderScale = 1f, maxLongEdgePixels = Int.MAX_VALUE, maxPixels = Int.MAX_VALUE,
            holdSixtyHz = false, sustainedPerformance = false)
    }
}

internal expect val platformRenderScale: Float
