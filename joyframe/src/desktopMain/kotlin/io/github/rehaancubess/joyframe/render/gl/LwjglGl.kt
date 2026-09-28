// SPDX-License-Identifier: Apache-2.0
package io.github.rehaancubess.joyframe.render.gl

import org.lwjgl.opengl.GL33C
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.nio.FloatBuffer

internal class LwjglGl : GlApi {

    private var scratchBytes: ByteBuffer? = null
    private var scratchFloats: FloatBuffer? = null

    private fun bytes(minimum: Int): ByteBuffer {
        val existing = scratchBytes
        if (existing != null && existing.capacity() >= minimum) return existing.also { it.clear() }
        existing?.let(MemoryUtil::memFree)
        return MemoryUtil.memAlloc(minimum).also { scratchBytes = it }
    }

    private fun floats(minimum: Int): FloatBuffer {
        val existing = scratchFloats
        if (existing != null && existing.capacity() >= minimum) return existing.also { it.clear() }
        existing?.let(MemoryUtil::memFree)
        return MemoryUtil.memAllocFloat(minimum).also { scratchFloats = it }
    }

    override fun viewport(x: Int, y: Int, width: Int, height: Int) = GL33C.glViewport(x, y, width, height)
    override fun scissor(x: Int, y: Int, width: Int, height: Int) = GL33C.glScissor(x, y, width, height)
    override fun clearColor(red: Float, green: Float, blue: Float, alpha: Float) =
        GL33C.glClearColor(red, green, blue, alpha)

    override fun clear(mask: Int) = GL33C.glClear(mask)
    override fun enable(capability: Int) = GL33C.glEnable(capability)
    override fun disable(capability: Int) = GL33C.glDisable(capability)
    override fun depthFunc(function: Int) = GL33C.glDepthFunc(function)
    override fun depthMask(enabled: Boolean) = GL33C.glDepthMask(enabled)
    override fun colorMask(red: Boolean, green: Boolean, blue: Boolean, alpha: Boolean) =
        GL33C.glColorMask(red, green, blue, alpha)

    override fun blendFuncSeparate(sourceRgb: Int, destinationRgb: Int, sourceAlpha: Int, destinationAlpha: Int) =
        GL33C.glBlendFuncSeparate(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha)

    override fun cullFace(mode: Int) = GL33C.glCullFace(mode)
    override fun frontFace(mode: Int) = GL33C.glFrontFace(mode)
    override fun polygonOffset(factor: Float, units: Float) = GL33C.glPolygonOffset(factor, units)
    override fun getError(): Int = GL33C.glGetError()

    override fun createShader(type: Int): Int = GL33C.glCreateShader(type)
    override fun shaderSource(shader: Int, source: String) = GL33C.glShaderSource(shader, source)
    override fun compileShader(shader: Int) = GL33C.glCompileShader(shader)
    override fun shaderCompiled(shader: Int): Boolean =
        GL33C.glGetShaderi(shader, GL33C.GL_COMPILE_STATUS) == GL33C.GL_TRUE

    override fun shaderInfoLog(shader: Int): String = GL33C.glGetShaderInfoLog(shader)
    override fun deleteShader(shader: Int) = GL33C.glDeleteShader(shader)

    override fun createProgram(): Int = GL33C.glCreateProgram()
    override fun attachShader(program: Int, shader: Int) = GL33C.glAttachShader(program, shader)
    override fun bindAttribLocation(program: Int, index: Int, name: String) =
        GL33C.glBindAttribLocation(program, index, name)

    override fun linkProgram(program: Int) = GL33C.glLinkProgram(program)
    override fun programLinked(program: Int): Boolean =
        GL33C.glGetProgrami(program, GL33C.GL_LINK_STATUS) == GL33C.GL_TRUE

    override fun programInfoLog(program: Int): String = GL33C.glGetProgramInfoLog(program)
    override fun useProgram(program: Int) = GL33C.glUseProgram(program)
    override fun deleteProgram(program: Int) = GL33C.glDeleteProgram(program)

    override fun uniformLocation(program: Int, name: String): Int = GL33C.glGetUniformLocation(program, name)
    override fun uniformMatrix4(location: Int, value: FloatArray) {
        if (location >= 0) GL33C.glUniformMatrix4fv(location, false, value)
    }

    override fun uniform4(location: Int, x: Float, y: Float, z: Float, w: Float) {
        if (location >= 0) GL33C.glUniform4f(location, x, y, z, w)
    }

    override fun uniform2(location: Int, x: Float, y: Float) {
        if (location >= 0) GL33C.glUniform2f(location, x, y)
    }

    override fun uniform1f(location: Int, value: Float) {
        if (location >= 0) GL33C.glUniform1f(location, value)
    }

    override fun uniform1i(location: Int, value: Int) {
        if (location >= 0) GL33C.glUniform1i(location, value)
    }

    override fun genBuffer(): Int = GL33C.glGenBuffers()
    override fun bindBuffer(target: Int, buffer: Int) = GL33C.glBindBuffer(target, buffer)
    override fun bufferData(target: Int, data: FloatArray, usage: Int) = GL33C.glBufferData(target, data, usage)
    override fun bufferData(target: Int, data: IntArray, usage: Int) = GL33C.glBufferData(target, data, usage)
    override fun bufferStorage(target: Int, sizeBytes: Int, usage: Int) =
        GL33C.glBufferData(target, sizeBytes.toLong(), usage)

    override fun bufferSubData(target: Int, offsetBytes: Int, data: FloatArray, count: Int) {
        val buffer = floats(count)
        buffer.put(data, 0, count)
        buffer.flip()
        GL33C.glBufferSubData(target, offsetBytes.toLong(), buffer)
    }

    override fun deleteBuffer(buffer: Int) = GL33C.glDeleteBuffers(buffer)

    override fun genVertexArray(): Int = GL33C.glGenVertexArrays()
    override fun bindVertexArray(array: Int) = GL33C.glBindVertexArray(array)
    override fun deleteVertexArray(array: Int) = GL33C.glDeleteVertexArrays(array)
    override fun enableVertexAttribArray(index: Int) = GL33C.glEnableVertexAttribArray(index)
    override fun vertexAttribPointer(
        index: Int,
        size: Int,
        type: Int,
        normalized: Boolean,
        strideBytes: Int,
        offsetBytes: Int,
    ) = GL33C.glVertexAttribPointer(index, size, type, normalized, strideBytes, offsetBytes.toLong())

    override fun genTexture(): Int = GL33C.glGenTextures()
    override fun bindTexture(target: Int, texture: Int) = GL33C.glBindTexture(target, texture)
    override fun activeTexture(unit: Int) = GL33C.glActiveTexture(unit)
    override fun texParameteri(target: Int, name: Int, value: Int) = GL33C.glTexParameteri(target, name, value)

    override fun texImage2DRgba(width: Int, height: Int, pixels: ByteArray?) {
        val buffer = pixels?.let {
            bytes(it.size).apply {
                put(it)
                flip()
            }
        }
        GL33C.glTexImage2D(
            GL33C.GL_TEXTURE_2D, 0, GL33C.GL_RGBA8, width, height, 0,
            GL33C.GL_RGBA, GL33C.GL_UNSIGNED_BYTE, buffer,
        )
    }

    override fun texImage2DDepth(width: Int, height: Int) = GL33C.glTexImage2D(
        GL33C.GL_TEXTURE_2D, 0, GL33C.GL_DEPTH_COMPONENT32F, width, height, 0,
        GL33C.GL_DEPTH_COMPONENT, GL33C.GL_FLOAT, null as ByteBuffer?,
    )

    override fun generateMipmap(target: Int) = GL33C.glGenerateMipmap(target)
    override fun deleteTexture(texture: Int) = GL33C.glDeleteTextures(texture)

    override fun genFramebuffer(): Int = GL33C.glGenFramebuffers()
    override fun bindFramebuffer(framebuffer: Int) = GL33C.glBindFramebuffer(GL33C.GL_FRAMEBUFFER, framebuffer)
    override fun framebufferDepthTexture(texture: Int) = GL33C.glFramebufferTexture2D(
        GL33C.GL_FRAMEBUFFER, GL33C.GL_DEPTH_ATTACHMENT, GL33C.GL_TEXTURE_2D, texture, 0,
    )

    override fun framebufferComplete(): Boolean =
        GL33C.glCheckFramebufferStatus(GL33C.GL_FRAMEBUFFER) == GL33C.GL_FRAMEBUFFER_COMPLETE

    override fun deleteFramebuffer(framebuffer: Int) = GL33C.glDeleteFramebuffers(framebuffer)

    override fun disableColorBuffers() {
        GL33C.glDrawBuffer(GL33C.GL_NONE)
        GL33C.glReadBuffer(GL33C.GL_NONE)
    }

    override fun drawElements(mode: Int, count: Int, type: Int, offsetBytes: Int) =
        GL33C.glDrawElements(mode, count, type, offsetBytes.toLong())

    override fun drawArrays(mode: Int, first: Int, count: Int) = GL33C.glDrawArrays(mode, first, count)


    fun dispose() {
        scratchBytes?.let(MemoryUtil::memFree)
        scratchFloats?.let(MemoryUtil::memFree)
        scratchBytes = null
        scratchFloats = null
    }
}
