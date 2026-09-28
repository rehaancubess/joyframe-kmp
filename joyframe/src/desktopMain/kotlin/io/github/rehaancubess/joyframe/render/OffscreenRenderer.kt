// SPDX-License-Identifier: Apache-2.0
package io.github.rehaancubess.joyframe.render

import io.github.rehaancubess.joyframe.render.gpu.GpuSceneFrame
import io.github.rehaancubess.joyframe.render.gpu.PackedColor
import java.awt.image.BufferedImage

/**
 * Renders frames without a window, for screenshots, store art, GIFs and render tests.
 * macOS only in this release (a dedicated CGL context); other desktops throw on construction.
 * Use every call from one thread and [close] when done.
 */
class OffscreenRenderer : AutoCloseable {
    private val framebuffer: MacFramebuffer

    init {
        check(isMac) { "OffscreenRenderer currently supports macOS only" }
        framebuffer = MacFramebuffer()
    }

    /** One frame, or up to four split-screen panes laid out by [SplitLayout]. */
    fun render(frames: List<GpuSceneFrame>, width: Int, height: Int,
               gutter: PackedColor = SplitLayout.DefaultGutter, gapPixels: Int = 0): BufferedImage {
        require(frames.size in 1..SplitLayout.MAX_PANES && width > 0 && height > 0)
        val bgra = framebuffer.renderPixels(frames, width, height, gutter, gapPixels)
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val row = IntArray(width)
        for (y in 0 until height) {
            // GL rows are bottom-up.
            val source = (height - 1 - y) * width * 4
            for (x in 0 until width) {
                val i = source + x * 4
                row[x] = ((bgra[i + 2].toInt() and 0xff) shl 16) or ((bgra[i + 1].toInt() and 0xff) shl 8) or
                    (bgra[i].toInt() and 0xff)
            }
            image.setRGB(0, y, width, 1, row, 0, width)
        }
        return image
    }

    fun render(frame: GpuSceneFrame, width: Int, height: Int): BufferedImage = render(listOf(frame), width, height)

    override fun close() = framebuffer.close()
}
