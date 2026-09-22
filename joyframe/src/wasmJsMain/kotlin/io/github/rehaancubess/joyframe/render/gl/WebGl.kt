// SPDX-License-Identifier: Apache-2.0
package io.github.rehaancubess.joyframe.render.gl

import org.khronos.webgl.Float32Array
import org.khronos.webgl.Int32Array
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.toFloat32Array
import org.khronos.webgl.toInt8Array
import org.khronos.webgl.toInt32Array
import org.w3c.dom.HTMLCanvasElement

internal class WebGl(private val gl: WebGL2) : GlApi {

    private val buffers = GlObjects<WebGlBuffer>()
    private val programs = GlObjects<WebGlProgram>()
    private val shaders = GlObjects<WebGlShader>()
    private val textures = GlObjects<WebGlTexture>()
    private val framebuffers = GlObjects<WebGlFramebuffer>()
    private val vertexArrays = GlObjects<WebGlVertexArray>()


    private val uniformLocations = GlObjects<WebGlUniformLocation>()

    override fun viewport(x: Int, y: Int, width: Int, height: Int) = gl.viewport(x, y, width, height)

    override fun clearColor(red: Float, green: Float, blue: Float, alpha: Float) =
        gl.clearColor(red, green, blue, alpha)

    override fun clear(mask: Int) = gl.clear(mask)
    override fun enable(capability: Int) = gl.enable(capability)
    override fun disable(capability: Int) = gl.disable(capability)
    override fun depthFunc(function: Int) = gl.depthFunc(function)
    override fun depthMask(enabled: Boolean) = gl.depthMask(enabled)
    override fun colorMask(red: Boolean, green: Boolean, blue: Boolean, alpha: Boolean) =
        gl.colorMask(red, green, blue, alpha)

    override fun blendFuncSeparate(sourceRgb: Int, destinationRgb: Int, sourceAlpha: Int, destinationAlpha: Int) =
        gl.blendFuncSeparate(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha)

    override fun cullFace(mode: Int) = gl.cullFace(mode)
    override fun frontFace(mode: Int) = gl.frontFace(mode)
    override fun polygonOffset(factor: Float, units: Float) = gl.polygonOffset(factor, units)
    override fun getError(): Int = gl.getError()

    override fun createShader(type: Int): Int =
        gl.createShader(type)?.let(shaders::put) ?: 0

    override fun shaderSource(shader: Int, source: String) = gl.shaderSource(shaders[shader], source)
    override fun compileShader(shader: Int) = gl.compileShader(shaders[shader])
    override fun shaderCompiled(shader: Int): Boolean = shaderCompileStatus(gl, shaders[shader])
    override fun shaderInfoLog(shader: Int): String = gl.getShaderInfoLog(shaders[shader]).orEmpty()

    override fun deleteShader(shader: Int) {
        gl.deleteShader(shaders[shader])
        shaders.remove(shader)
    }

    override fun createProgram(): Int = gl.createProgram()?.let(programs::put) ?: 0
    override fun attachShader(program: Int, shader: Int) = gl.attachShader(programs[program], shaders[shader])
    override fun bindAttribLocation(program: Int, index: Int, name: String) =
        gl.bindAttribLocation(programs[program], index, name)

    override fun linkProgram(program: Int) = gl.linkProgram(programs[program])
    override fun programLinked(program: Int): Boolean = programLinkStatus(gl, programs[program])
    override fun programInfoLog(program: Int): String = gl.getProgramInfoLog(programs[program]).orEmpty()
    override fun useProgram(program: Int) = gl.useProgram(programs[program])

    override fun deleteProgram(program: Int) {
        gl.deleteProgram(programs[program])
        programs.remove(program)
    }

    override fun uniformLocation(program: Int, name: String): Int =
        gl.getUniformLocation(programs[program], name)?.let(uniformLocations::put) ?: -1

    override fun uniformMatrix4(location: Int, value: FloatArray) {
        if (location >= 0) gl.uniformMatrix4fv(uniformLocations[location], false, value.toFloat32Array())
    }

    override fun uniform4(location: Int, x: Float, y: Float, z: Float, w: Float) {
        if (location >= 0) gl.uniform4f(uniformLocations[location], x, y, z, w)
    }

    override fun uniform2(location: Int, x: Float, y: Float) {
        if (location >= 0) gl.uniform2f(uniformLocations[location], x, y)
    }

    override fun uniform1f(location: Int, value: Float) {
        if (location >= 0) gl.uniform1f(uniformLocations[location], value)
    }

    override fun uniform1i(location: Int, value: Int) {
        if (location >= 0) gl.uniform1i(uniformLocations[location], value)
    }

    override fun genBuffer(): Int = gl.createBuffer()?.let(buffers::put) ?: 0
    override fun bindBuffer(target: Int, buffer: Int) = gl.bindBuffer(target, buffers[buffer])

    override fun bufferData(target: Int, data: FloatArray, usage: Int) =
        gl.bufferData(target, data.toFloat32Array(), usage)

    override fun bufferData(target: Int, data: IntArray, usage: Int) =
        gl.bufferData(target, data.toInt32Array(), usage)

    override fun bufferStorage(target: Int, sizeBytes: Int, usage: Int) =
        gl.bufferData(target, sizeBytes, usage)


    override fun bufferSubData(target: Int, offsetBytes: Int, data: FloatArray, count: Int) {
        val source = if (count == data.size) data else data.copyOfRange(0, count)
        gl.bufferSubData(target, offsetBytes, source.toFloat32Array())
    }

    override fun deleteBuffer(buffer: Int) {
        gl.deleteBuffer(buffers[buffer])
        buffers.remove(buffer)
    }

    override fun genVertexArray(): Int = gl.createVertexArray()?.let(vertexArrays::put) ?: 0
    override fun bindVertexArray(array: Int) = gl.bindVertexArray(vertexArrays[array])

    override fun deleteVertexArray(array: Int) {
        gl.deleteVertexArray(vertexArrays[array])
        vertexArrays.remove(array)
    }

    override fun enableVertexAttribArray(index: Int) = gl.enableVertexAttribArray(index)

    override fun vertexAttribPointer(
        index: Int,
        size: Int,
        type: Int,
        normalized: Boolean,
        strideBytes: Int,
        offsetBytes: Int,
    ) = gl.vertexAttribPointer(index, size, type, normalized, strideBytes, offsetBytes)

    override fun genTexture(): Int = gl.createTexture()?.let(textures::put) ?: 0
    override fun bindTexture(target: Int, texture: Int) = gl.bindTexture(target, textures[texture])
    override fun activeTexture(unit: Int) = gl.activeTexture(unit)
    override fun texParameteri(target: Int, name: Int, value: Int) = gl.texParameteri(target, name, value)

    override fun texImage2DRgba(width: Int, height: Int, pixels: ByteArray?) {
        val view = pixels?.toInt8Array()?.let { Uint8Array(it.buffer, 0, it.length) }
        gl.texImage2D(
            GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0,
            GL_RGBA, GL_UNSIGNED_BYTE, view,
        )
    }

    override fun texImage2DDepth(width: Int, height: Int) = gl.texImage2D(
        GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT32F, width, height, 0,
        GL_DEPTH_COMPONENT, GL_FLOAT, null,
    )

    override fun generateMipmap(target: Int) = gl.generateMipmap(target)

    override fun deleteTexture(texture: Int) {
        gl.deleteTexture(textures[texture])
        textures.remove(texture)
    }

    override fun genFramebuffer(): Int = gl.createFramebuffer()?.let(framebuffers::put) ?: 0

    override fun bindFramebuffer(framebuffer: Int) =
        gl.bindFramebuffer(GL_FRAMEBUFFER, framebuffers[framebuffer])

    override fun framebufferDepthTexture(texture: Int) = gl.framebufferTexture2D(
        GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, textures[texture], 0,
    )

    override fun framebufferComplete(): Boolean =
        gl.checkFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE

    override fun deleteFramebuffer(framebuffer: Int) {
        gl.deleteFramebuffer(framebuffers[framebuffer])
        framebuffers.remove(framebuffer)
    }

    override fun disableColorBuffers() = detachColorBuffers(gl)

    override fun drawElements(mode: Int, count: Int, type: Int, offsetBytes: Int) =
        gl.drawElements(mode, count, type, offsetBytes)

    override fun drawArrays(mode: Int, first: Int, count: Int) = gl.drawArrays(mode, first, count)
}

private class GlObjects<T : JsAny> {
    private val slots = ArrayList<T?>().apply { add(null) }
    private val free = ArrayList<Int>()

    fun put(value: T): Int {
        val recycled = free.removeLastOrNull()
        if (recycled != null) {
            slots[recycled] = value
            return recycled
        }
        slots.add(value)
        return slots.size - 1
    }

    operator fun get(id: Int): T? = if (id > 0 && id < slots.size) slots[id] else null

    fun remove(id: Int) {
        if (id <= 0 || id >= slots.size || slots[id] == null) return
        slots[id] = null
        free.add(id)
    }
}
private const val GL_TEXTURE_2D = 0x0DE1
private const val GL_RGBA = 0x1908
private const val GL_RGBA8 = 0x8058
private const val GL_UNSIGNED_BYTE = 0x1401
private const val GL_FLOAT = 0x1406
private const val GL_DEPTH_COMPONENT = 0x1902
private const val GL_DEPTH_COMPONENT32F = 0x8CAC
private const val GL_FRAMEBUFFER = 0x8D40
private const val GL_DEPTH_ATTACHMENT = 0x8D00
private const val GL_FRAMEBUFFER_COMPLETE = 0x8CD5

internal external interface WebGlBuffer : JsAny
internal external interface WebGlProgram : JsAny
internal external interface WebGlShader : JsAny
internal external interface WebGlTexture : JsAny
internal external interface WebGlFramebuffer : JsAny
internal external interface WebGlVertexArray : JsAny
internal external interface WebGlUniformLocation : JsAny

internal external interface WebGL2 : JsAny {
    fun viewport(x: Int, y: Int, width: Int, height: Int)
    fun clearColor(red: Float, green: Float, blue: Float, alpha: Float)
    fun clear(mask: Int)
    fun enable(capability: Int)
    fun disable(capability: Int)
    fun depthFunc(function: Int)
    fun depthMask(enabled: Boolean)
    fun colorMask(red: Boolean, green: Boolean, blue: Boolean, alpha: Boolean)
    fun blendFuncSeparate(sourceRgb: Int, destinationRgb: Int, sourceAlpha: Int, destinationAlpha: Int)
    fun cullFace(mode: Int)
    fun frontFace(mode: Int)
    fun polygonOffset(factor: Float, units: Float)
    fun getError(): Int

    fun createShader(type: Int): WebGlShader?
    fun shaderSource(shader: WebGlShader?, source: String)
    fun compileShader(shader: WebGlShader?)
    fun getShaderInfoLog(shader: WebGlShader?): String?
    fun deleteShader(shader: WebGlShader?)

    fun createProgram(): WebGlProgram?
    fun attachShader(program: WebGlProgram?, shader: WebGlShader?)
    fun bindAttribLocation(program: WebGlProgram?, index: Int, name: String)
    fun linkProgram(program: WebGlProgram?)
    fun getProgramInfoLog(program: WebGlProgram?): String?
    fun useProgram(program: WebGlProgram?)
    fun deleteProgram(program: WebGlProgram?)

    fun getUniformLocation(program: WebGlProgram?, name: String): WebGlUniformLocation?
    fun uniformMatrix4fv(location: WebGlUniformLocation?, transpose: Boolean, value: Float32Array)
    fun uniform4f(location: WebGlUniformLocation?, x: Float, y: Float, z: Float, w: Float)
    fun uniform2f(location: WebGlUniformLocation?, x: Float, y: Float)
    fun uniform1f(location: WebGlUniformLocation?, x: Float)
    fun uniform1i(location: WebGlUniformLocation?, x: Int)

    fun createBuffer(): WebGlBuffer?
    fun bindBuffer(target: Int, buffer: WebGlBuffer?)
    fun bufferData(target: Int, data: Float32Array, usage: Int)
    fun bufferData(target: Int, data: Int32Array, usage: Int)
    fun bufferData(target: Int, sizeBytes: Int, usage: Int)
    fun bufferSubData(target: Int, offsetBytes: Int, data: Float32Array)
    fun deleteBuffer(buffer: WebGlBuffer?)

    fun createVertexArray(): WebGlVertexArray?
    fun bindVertexArray(array: WebGlVertexArray?)
    fun deleteVertexArray(array: WebGlVertexArray?)
    fun enableVertexAttribArray(index: Int)
    fun vertexAttribPointer(
        index: Int,
        size: Int,
        type: Int,
        normalized: Boolean,
        strideBytes: Int,
        offsetBytes: Int,
    )

    fun createTexture(): WebGlTexture?
    fun bindTexture(target: Int, texture: WebGlTexture?)
    fun activeTexture(unit: Int)
    fun texParameteri(target: Int, name: Int, value: Int)
    fun texImage2D(
        target: Int,
        level: Int,
        internalFormat: Int,
        width: Int,
        height: Int,
        border: Int,
        format: Int,
        type: Int,
        pixels: Uint8Array?,
    )
    fun generateMipmap(target: Int)
    fun deleteTexture(texture: WebGlTexture?)

    fun createFramebuffer(): WebGlFramebuffer?
    fun bindFramebuffer(target: Int, framebuffer: WebGlFramebuffer?)
    fun framebufferTexture2D(target: Int, attachment: Int, textureTarget: Int, texture: WebGlTexture?, level: Int)
    fun checkFramebufferStatus(target: Int): Int
    fun deleteFramebuffer(framebuffer: WebGlFramebuffer?)

    fun drawElements(mode: Int, count: Int, type: Int, offsetBytes: Int)
    fun drawArrays(mode: Int, first: Int, count: Int)
}

internal fun webGl2Context(canvas: HTMLCanvasElement, antialias: Boolean): WebGL2? = js(
    """canvas.getContext('webgl2', {
        alpha: false,
        depth: true,
        stencil: false,
        antialias: antialias,
        premultipliedAlpha: false,
        preserveDrawingBuffer: false,
        powerPreference: 'high-performance',
    })"""
)

private fun shaderCompileStatus(gl: WebGL2, shader: WebGlShader?): Boolean =
    js("!!gl.getShaderParameter(shader, 0x8B81)")

private fun programLinkStatus(gl: WebGL2, program: WebGlProgram?): Boolean =
    js("!!gl.getProgramParameter(program, 0x8B82)")

private fun detachColorBuffers(gl: WebGL2) {
    js("(function() { gl.drawBuffers([gl.NONE]); gl.readBuffer(gl.NONE); })()")
}
