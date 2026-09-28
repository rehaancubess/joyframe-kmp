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
actual fun GameView(frame: GpuSceneFrame, modifier: Modifier, active: Boolean) {
    SplitGameView(listOf(frame), modifier, active)
}

@Composable
actual fun SplitGameView(frames: List<GpuSceneFrame>, modifier: Modifier, active: Boolean, gutter: PackedColor, gapDp: Float) {
    require(frames.size in 1..SplitLayout.MAX_PANES) { "SplitGameView needs 1 to ${SplitLayout.MAX_PANES} frames" }
    val host = remember { MetalHost(frames) }
    SideEffect { host.frames = frames; host.gutter = gutter; host.gapDp = gapDp; host.view.paused = !active }
    UIKitView(factory = { host.view }, modifier = modifier, onRelease = {
        it.paused = true
        it.delegate = null
    })
}

private class MetalHost(var frames: List<GpuSceneFrame>) {
    var gutter = SplitLayout.DefaultGutter
    var gapDp = 3f
    private val device = checkNotNull(MTLCreateSystemDefaultDevice()) { "Joyframe requires Metal" }
    private val renderer = checkNotNull(MetalSceneBackend.create(device, MTLPixelFormatBGRA8Unorm,
        MTLPixelFormatDepth32Float, 4uL)) { "Joyframe Metal shaders failed to compile" }
    val view = MTKView(frame = CGRectZero.readValue(), device = device).apply {
        colorPixelFormat = MTLPixelFormatBGRA8Unorm
        depthStencilPixelFormat = MTLPixelFormatDepth32Float
        sampleCount = 4uL
        preferredFramesPerSecond = 60
        framebufferOnly = true
        setUserInteractionEnabled(false)
    }
    // MTKView stores a weak delegate; this strong reference is essential.
    private val retainedDelegate = object : NSObject(), MTKViewDelegateProtocol {
        override fun drawInMTKView(view: MTKView) {
            val pass = view.currentRenderPassDescriptor ?: return
            val current = frames
            val (width, height) = view.drawableSize.useContents { width to height }
            if (current.size == 1) {
                renderer.render(current[0], (width / height.coerceAtLeast(1.0)).toFloat(), pass, view.currentDrawable)
            } else {
                val gap = (gapDp * view.contentScaleFactor).roundToInt()
                val panes = SplitLayout.panes(current.size, width.toInt(), height.toInt(), gap)
                renderer.renderSplit(current, panes, gutter, pass, view.currentDrawable)
            }
        }
        override fun mtkView(view: MTKView, drawableSizeWillChange: CValue<CGSize>) = Unit
    }
    init { view.delegate = retainedDelegate }
}
