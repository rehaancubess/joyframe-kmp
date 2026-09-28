// SPDX-License-Identifier: Apache-2.0
package io.github.rehaancubess.joyframe.render

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.rehaancubess.joyframe.render.gpu.GpuSceneFrame
import io.github.rehaancubess.joyframe.render.gpu.PackedColor

/** One pane of a surface in pixels, origin at the top-left. */
data class PaneRect(val x: Int, val y: Int, val width: Int, val height: Int) {
    val aspect: Float get() = width.toFloat() / height.coerceAtLeast(1)

    /** The same pane measured from the bottom-left, as OpenGL viewports are. */
    fun flippedY(surfaceHeight: Int): PaneRect = copy(y = surfaceHeight - y - height)
}

/**
 * How local players share one screen. Two players split along the long edge, so each keeps a
 * near-square view (side by side in landscape, stacked in portrait). Three and four take quadrants;
 * with three, the fourth quadrant is left as gutter for the caller to fill or ignore.
 */
object SplitLayout {
    const val MAX_PANES = 4
    val DefaultGutter = PackedColor(0xff0a0f1c.toInt())

    fun panes(count: Int, width: Int, height: Int, gap: Int = 0): List<PaneRect> {
        require(count in 1..MAX_PANES) { "Split screen supports 1 to $MAX_PANES panes, not $count" }
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        if (count == 1) return listOf(PaneRect(0, 0, w, h))
        val g = gap.coerceIn(0, minOf(w, h) / 8)
        if (count == 2) {
            return if (w >= h) {
                val left = (w - g) / 2
                listOf(PaneRect(0, 0, left, h), PaneRect(left + g, 0, w - left - g, h))
            } else {
                val top = (h - g) / 2
                listOf(PaneRect(0, 0, w, top), PaneRect(0, top + g, w, h - top - g))
            }
        }
        val left = (w - g) / 2
        val top = (h - g) / 2
        val quadrants = listOf(
            PaneRect(0, 0, left, top),
            PaneRect(left + g, 0, w - left - g, top),
            PaneRect(0, top + g, left, h - top - g),
            PaneRect(left + g, top + g, w - left - g, h - top - g),
        )
        return quadrants.take(count)
    }
}

/**
 * Draws up to four frames, one per local player, into a single native surface laid out by
 * [SplitLayout]. One surface rather than several views keeps a single GPU context and one upload of
 * the shared assets, so every frame should use the same `GpuSceneAssets` (same cache key).
 * Give each pane its own camera (for example its own [ChaseCamera]). [gapDp] is the gutter width.
 */
@Composable
expect fun SplitGameView(
    frames: List<GpuSceneFrame>,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    gutter: PackedColor = SplitLayout.DefaultGutter,
    gapDp: Float = 3f,
    options: GameViewOptions = GameViewOptions.Default,
)
