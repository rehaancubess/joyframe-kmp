package io.github.rehaancubess.joyframe.render

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import io.github.rehaancubess.joyframe.render.gl.*
import io.github.rehaancubess.joyframe.render.gpu.GpuSceneFrame
import kotlinx.coroutines.*
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ImageInfo
import org.lwjgl.opengl.CGL.*
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL33C.*
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/** Dedicated CGL context, composited by Compose so clipping, layout and controls have one owner.
 * Readback is a deliberate compatibility/performance tradeoff; capped at a 1200px long edge.
 */
@Composable
internal fun MacGameView(frame: GpuSceneFrame, modifier: Modifier, active: Boolean) {
    val latest = rememberUpdatedState(frame)
    var size by remember { mutableStateOf(IntSize.Zero) }
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    val executor = remember { Executors.newSingleThreadExecutor { task ->
        Thread(task,"joyframe-render").apply { isDaemon = true }
    } }
    val dispatcher = remember { executor.asCoroutineDispatcher() }
    val renderer = remember { MacFramebuffer() }
    LaunchedEffect(size, active) {
        if (size.width <= 0 || size.height <= 0) return@LaunchedEffect
        val scale = minOf(1f,1200f / size.width,1200f / size.height)
        val width = (size.width*scale).roundToInt().coerceAtLeast(1)
        val height = (size.height*scale).roundToInt().coerceAtLeast(1)
        do {
            withFrameNanos { }
            val current = latest.value
            image = withContext(dispatcher) { renderer.render(current,width,height) }
        } while (active && isActive)
    }
    DisposableEffect(renderer) {
        onDispose { executor.execute { try { renderer.close() } finally { dispatcher.close() } } }
    }
    Canvas(modifier.onSizeChanged { size = it }) {
        image?.let { bitmap -> scale(1f,-1f) {
            drawImage(bitmap,dstSize=IntSize(this.size.width.toInt(),this.size.height.toInt()),filterQuality=FilterQuality.Low)
        } }
    }
}

private class MacFramebuffer {
    private var context = 0L
    private var api: LwjglGl? = null
    private var backend: GlSceneBackend? = null
    private var multisample = 0
    private var resolve = 0
    private var color = 0
    private var depth = 0
    private var resolvedColor = 0
    private var width = 0
    private var height = 0
    private var readback: ByteBuffer? = null
    private var bytes = ByteArray(0)

    private fun initialize() {
        if (context != 0L) return
        MemoryStack.stackPush().use { stack ->
            val format = stack.mallocPointer(1)
            val count = stack.mallocInt(1)
            check(CGLChoosePixelFormat(stack.ints(kCGLPFAOpenGLProfile,0x4100,
                kCGLPFAAccelerated,kCGLPFAAllowOfflineRenderers,0),format,count)==0) { "No CGL pixel format" }
            try {
                val output = stack.mallocPointer(1)
                check(CGLCreateContext(format[0],0L,output)==0) { "Cannot create CGL context" }
                context = output[0]
            } finally { CGLDestroyPixelFormat(format[0]) }
        }
        check(CGLSetCurrentContext(context)==0)
        GL.createCapabilities()
        api = LwjglGl()
        backend = checkNotNull(GlSceneBackend.create(api!!,GlslDialect.Core330)) { "Joyframe shader compilation failed" }
    }
    fun render(frame: GpuSceneFrame, wantedWidth: Int, wantedHeight: Int): ImageBitmap {
        initialize()
        if (width!=wantedWidth || height!=wantedHeight) allocate(wantedWidth,wantedHeight)
        backend!!.render(frame,width,height,multisample)
        glBindFramebuffer(GL_READ_FRAMEBUFFER,multisample)
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER,resolve)
        glBlitFramebuffer(0,0,width,height,0,0,width,height,GL_COLOR_BUFFER_BIT,GL_NEAREST)
        glBindFramebuffer(GL_READ_FRAMEBUFFER,resolve)
        val buffer = readback!!
        buffer.clear()
        glReadPixels(0,0,width,height,GL_BGRA,GL_UNSIGNED_BYTE,buffer)
        buffer.get(bytes)
        val bitmap = Bitmap()
        bitmap.allocPixels(ImageInfo.makeN32(width,height,ColorAlphaType.OPAQUE))
        bitmap.installPixels(bytes)
        return bitmap.asComposeImageBitmap()
    }
    private fun allocate(w: Int,h: Int) {
        releaseBuffers()
        width=w; height=h
        multisample=glGenFramebuffers()
        glBindFramebuffer(GL_FRAMEBUFFER,multisample)
        color=glGenRenderbuffers()
        glBindRenderbuffer(GL_RENDERBUFFER,color)
        glRenderbufferStorageMultisample(GL_RENDERBUFFER,4,GL_RGBA8,width,height)
        glFramebufferRenderbuffer(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_RENDERBUFFER,color)
        depth=glGenRenderbuffers()
        glBindRenderbuffer(GL_RENDERBUFFER,depth)
        glRenderbufferStorageMultisample(GL_RENDERBUFFER,4,GL_DEPTH_COMPONENT24,width,height)
        glFramebufferRenderbuffer(GL_FRAMEBUFFER,GL_DEPTH_ATTACHMENT,GL_RENDERBUFFER,depth)
        check(glCheckFramebufferStatus(GL_FRAMEBUFFER)==GL_FRAMEBUFFER_COMPLETE)
        resolve=glGenFramebuffers()
        glBindFramebuffer(GL_FRAMEBUFFER,resolve)
        resolvedColor=glGenRenderbuffers()
        glBindRenderbuffer(GL_RENDERBUFFER,resolvedColor)
        glRenderbufferStorage(GL_RENDERBUFFER,GL_RGBA8,width,height)
        glFramebufferRenderbuffer(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_RENDERBUFFER,resolvedColor)
        check(glCheckFramebufferStatus(GL_FRAMEBUFFER)==GL_FRAMEBUFFER_COMPLETE)
        readback=MemoryUtil.memAlloc(width*height*4)
        bytes=ByteArray(width*height*4)
    }
    private fun releaseBuffers() {
        if(multisample!=0) glDeleteFramebuffers(multisample)
        if(resolve!=0) glDeleteFramebuffers(resolve)
        for(buffer in intArrayOf(color,depth,resolvedColor)) if(buffer!=0) glDeleteRenderbuffers(buffer)
        multisample=0; resolve=0; color=0; depth=0; resolvedColor=0
        readback?.let(MemoryUtil::memFree); readback=null
    }
    fun close() {
        if(context==0L) return
        try { backend?.dispose(); releaseBuffers(); api?.dispose() }
        finally { GL.setCapabilities(null); CGLSetCurrentContext(0L); CGLDestroyContext(context); context=0L }
    }
}
