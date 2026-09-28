@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
package io.github.rehaancubess.joyframe.render

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import io.github.rehaancubess.joyframe.render.gpu.GpuSceneFrame
import io.github.rehaancubess.joyframe.render.gpu.PackedColor
import io.github.rehaancubess.joyframe.render.metal.MetalSceneBackend
import kotlinx.cinterop.*
import platform.CoreGraphics.*
import platform.Metal.*
import platform.MetalKit.*
import platform.darwin.NSObject
import kotlin.math.roundToInt

@Composable
actual fun GameView(frame: GpuSceneFrame, modifier: Modifier, active: Boolean, options: GameViewOptions) {
    SplitGameView(listOf(frame), modifier, active, options = options)
}

@Composable
actual fun SplitGameView(frames: List<GpuSceneFrame>, modifier: Modifier, active: Boolean, gutter: PackedColor,
                         gapDp: Float, options: GameViewOptions) {
    require(frames.size in 1..SplitLayout.MAX_PANES) { "SplitGameView needs 1 to ${SplitLayout.MAX_PANES} frames" }
    val host = remember { MetalHost(frames) }
    SideEffect {
        host.frames = frames; host.gutter = gutter; host.gapDp = gapDp; host.options = options
        host.view.paused = !active
    }
    UIKitView(factory = { host.view }, modifier = modifier, onRelease = {
        it.paused = true
        it.delegate = null
    })
}

private class MetalHost(var frames: List<GpuSceneFrame>) {
    var gutter = SplitLayout.DefaultGutter
    var gapDp = 3f
    var options = GameViewOptions.Default
    private var sizedFor: Triple<Double, Double, GameViewOptions>? = null
    private val device = checkNotNull(MTLCreateSystemDefaultDevice()) { "Joyframe requires Metal" }
    private val renderer = checkNotNull(MetalSceneBackend.create(device, MTLPixelFormatBGRA8Unorm,
        MTLPixelFormatDepth32Float, 4uL)) { "Joyframe Metal shaders failed to compile" }
    val view = MTKView(frame = CGRectZero.readValue(), device = device).apply {
        colorPixelFormat = MTLPixelFormatBGRA8Unorm
        depthStencilPixelFormat = MTLPixelFormatDepth32Float
        sampleCount = 4uL
        preferredFramesPerSecond = 60
        framebufferOnly = true
        // The drawable is sized explicitly (capped below native); see sizeDrawable.
        autoResizeDrawable = false
        setUserInteractionEnabled(false)
    }
    // MTKView stores a weak delegate; this strong reference is essential.
    private val retainedDelegate = object : NSObject(), MTKViewDelegateProtocol {
        override fun drawInMTKView(view: MTKView) {
            sizeDrawable(view)
            val pass = view.currentRenderPassDescriptor ?: return
            val current = frames
            val (width, height) = view.drawableSize.useContents { width to height }
            if (current.size == 1) {
                renderer.render(current[0], (width / height.coerceAtLeast(1.0)).toFloat(), pass, view.currentDrawable)
            } else {
                val native = view.bounds.useContents { size.width * view.contentScaleFactor }
                val gap = (gapDp * view.contentScaleFactor * (width / native.coerceAtLeast(1.0))).roundToInt()
                val panes = SplitLayout.panes(current.size, width.toInt(), height.toInt(), gap)
                renderer.renderSplit(current, panes, gutter, pass, view.currentDrawable)
            }
        }
        override fun mtkView(view: MTKView, drawableSizeWillChange: CValue<CGSize>) = Unit
    }
    init { view.delegate = retainedDelegate }

    /** Caps only the 3D drawable; the HUD and touch coordinates stay at native resolution. */
    private fun sizeDrawable(view: MTKView) {
        val scale = view.contentScaleFactor
        val (width, height) = view.bounds.useContents { size.width * scale to size.height * scale }
        val key = Triple(width, height, options)
        if (key == sizedFor || width <= 0.0 || height <= 0.0) return
        sizedFor = key
        val (w, h) = options.renderSize(width.toInt(), height.toInt())
        view.drawableSize = CGSizeMake(w.toDouble(), h.toDouble())
    }
}
