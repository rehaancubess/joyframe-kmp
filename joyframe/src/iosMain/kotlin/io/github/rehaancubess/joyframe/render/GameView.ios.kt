@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
package io.github.rehaancubess.joyframe.render

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import io.github.rehaancubess.joyframe.render.gpu.GpuSceneFrame
import io.github.rehaancubess.joyframe.render.metal.MetalSceneBackend
import kotlinx.cinterop.*
import platform.CoreGraphics.*
import platform.Metal.*
import platform.MetalKit.*
import platform.darwin.NSObject

@Composable
actual fun GameView(frame: GpuSceneFrame, modifier: Modifier, active: Boolean) {
    val host = remember { MetalHost(frame) }
    SideEffect { host.frame = frame; host.view.paused = !active }
    UIKitView(factory = { host.view }, modifier = modifier, onRelease = {
        it.paused = true
        it.delegate = null
    })
}
private class MetalHost(var frame: GpuSceneFrame) {
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
            val aspect = view.drawableSize.useContents { (width / height.coerceAtLeast(1.0)).toFloat() }
            renderer.render(frame, aspect, pass, view.currentDrawable)
        }
        override fun mtkView(view: MTKView, drawableSizeWillChange: CValue<CGSize>) = Unit
    }
    init { view.delegate = retainedDelegate }
}
