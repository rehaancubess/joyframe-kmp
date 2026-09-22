// SPDX-License-Identifier: Apache-2.0
package io.github.rehaancubess.joyframe.render.gl

import android.opengl.GLES20
import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer

internal class AndroidGl : GlApi {

    private val name = IntArray(1)
    private val status = IntArray(1)
    private var scratch: ByteBuffer? = null

    private fun scratch(minimumBytes: Int): ByteBuffer {
        val existing = scratch
        if (existing != null && existing.capacity() >= minimumBytes) return existing.also { it.clear() }
        return ByteBuffer
            .allocateDirect(minimumBytes)
            .order(ByteOrder.nativeOrder())
            .also { scratch = it }
    }

    private fun floats(data: FloatArray, count: Int): FloatBuffer =
        scratch(count * 4).asFloatBuffer().apply {
            put(data, 0, count)
            position(0)
        }

    private fun ints(data: IntArray): IntBuffer =
        scratch(data.size * 4).asIntBuffer().apply {
            put(data)
            position(0)
        }

    override fun viewport(x: Int, y: Int, width: Int, height: Int) = GLES30.glViewport(x, y, width, height)
    override fun clearColor(red: Float, green: Float, blue: Float, alpha: Float) =
        GLES30.glClearColor(red, green, blue, alpha)

    override fun clear(mask: Int) = GLES30.glClear(mask)
    override fun enable(capability: Int) = GLES30.glEnable(capability)
    override fun disable(capability: Int) = GLES30.glDisable(capability)
    override fun depthFunc(function: Int) = GLES30.glDepthFunc(function)
    override fun depthMask(enabled: Boolean) = GLES30.glDepthMask(enabled)
    override fun colorMask(red: Boolean, green: Boolean, blue: Boolean, alpha: Boolean) =
        GLES30.glColorMask(red, green, blue, alpha)

    override fun blendFuncSeparate(sourceRgb: Int, destinationRgb: Int, sourceAlpha: Int, destinationAlpha: Int) =
        GLES30.glBlendFuncSeparate(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha)

    override fun cullFace(mode: Int) = GLES30.glCullFace(mode)
    override fun frontFace(mode: Int) = GLES30.glFrontFace(mode)
    override fun polygonOffset(factor: Float, units: Float) = GLES30.glPolygonOffset(factor, units)
    override fun getError(): Int = GLES30.glGetError()

    override fun createShader(type: Int): Int = GLES30.glCreateShader(type)
    override fun shaderSource(shader: Int, source: String) = GLES30.glShaderSource(shader, source)
    override fun compileShader(shader: Int) = GLES30.glCompileShader(shader)
    override fun shaderCompiled(shader: Int): Boolean {
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        return status[0] == GLES30.GL_TRUE
    }

    override fun shaderInfoLog(shader: Int): String = GLES30.glGetShaderInfoLog(shader)
    override fun deleteShader(shader: Int) = GLES30.glDeleteShader(shader)

    override fun createProgram(): Int = GLES30.glCreateProgram()
    override fun attachShader(program: Int, shader: Int) = GLES30.glAttachShader(program, shader)
    override fun bindAttribLocation(program: Int, index: Int, name: String) =
        GLES30.glBindAttribLocation(program, index, name)

    override fun linkProgram(program: Int) = GLES30.glLinkProgram(program)
    override fun programLinked(program: Int): Boolean {
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0)
        return status[0] == GLES30.GL_TRUE
    }

    override fun programInfoLog(program: Int): String = GLES30.glGetProgramInfoLog(program)
    override fun useProgram(program: Int) = GLES30.glUseProgram(program)
    override fun deleteProgram(program: Int) = GLES30.glDeleteProgram(program)

    override fun uniformLocation(program: Int, name: String): Int = GLES30.glGetUniformLocation(program, name)
    override fun uniformMatrix4(location: Int, value: FloatArray) {
        if (location >= 0) GLES30.glUniformMatrix4fv(location, 1, false, value, 0)
    }

    override fun uniform4(location: Int, x: Float, y: Float, z: Float, w: Float) {
        if (location >= 0) GLES30.glUniform4f(location, x, y, z, w)
    }

    override fun uniform2(location: Int, x: Float, y: Float) {
        if (location >= 0) GLES30.glUniform2f(location, x, y)
    }

    override fun uniform1f(location: Int, value: Float) {
        if (location >= 0) GLES30.glUniform1f(location, value)
    }

    override fun uniform1i(location: Int, value: Int) {
        if (location >= 0) GLES30.glUniform1i(location, value)
    }

    override fun genBuffer(): Int {
        GLES30.glGenBuffers(1, name, 0)
        return name[0]
    }

    override fun bindBuffer(target: Int, buffer: Int) = GLES30.glBindBuffer(target, buffer)

    override fun bufferData(target: Int, data: FloatArray, usage: Int) =
        GLES30.glBufferData(target, data.size * 4, floats(data, data.size), usage)

    override fun bufferData(target: Int, data: IntArray, usage: Int) =
        GLES30.glBufferData(target, data.size * 4, ints(data), usage)

    override fun bufferStorage(target: Int, sizeBytes: Int, usage: Int) =
        GLES30.glBufferData(target, sizeBytes, null, usage)

    override fun bufferSubData(target: Int, offsetBytes: Int, data: FloatArray, count: Int) =
        GLES30.glBufferSubData(target, offsetBytes, count * 4, floats(data, count))

    override fun deleteBuffer(buffer: Int) {
        name[0] = buffer
        GLES30.glDeleteBuffers(1, name, 0)
    }

    override fun genVertexArray(): Int {
        GLES30.glGenVertexArrays(1, name, 0)
        return name[0]
    }

    override fun bindVertexArray(array: Int) = GLES30.glBindVertexArray(array)

    override fun deleteVertexArray(array: Int) {
        name[0] = array
        GLES30.glDeleteVertexArrays(1, name, 0)
    }

    override fun enableVertexAttribArray(index: Int) = GLES30.glEnableVertexAttribArray(index)
    override fun vertexAttribPointer(
        index: Int,
        size: Int,
        type: Int,
        normalized: Boolean,
        strideBytes: Int,
        offsetBytes: Int,
    ) = GLES30.glVertexAttribPointer(index, size, type, normalized, strideBytes, offsetBytes)

    override fun genTexture(): Int {
        GLES30.glGenTextures(1, name, 0)
        return name[0]
    }

    override fun bindTexture(target: Int, texture: Int) = GLES30.glBindTexture(target, texture)
    override fun activeTexture(unit: Int) = GLES30.glActiveTexture(unit)
    override fun texParameteri(target: Int, name: Int, value: Int) = GLES30.glTexParameteri(target, name, value)

    override fun texImage2DRgba(width: Int, height: Int, pixels: ByteArray?) {
        val buffer = pixels?.let {
            scratch(it.size).apply {
                put(it)
                position(0)
            }
        }
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, width, height, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buffer,
        )
    }

    override fun texImage2DDepth(width: Int, height: Int) = GLES30.glTexImage2D(
        GLES30.GL_TEXTURE_2D, 0, GLES30.GL_DEPTH_COMPONENT32F, width, height, 0,
        GLES30.GL_DEPTH_COMPONENT, GLES30.GL_FLOAT, null,
    )

    override fun generateMipmap(target: Int) = GLES30.glGenerateMipmap(target)

    override fun deleteTexture(texture: Int) {
        name[0] = texture
        GLES30.glDeleteTextures(1, name, 0)
    }

    override fun genFramebuffer(): Int {
        GLES30.glGenFramebuffers(1, name, 0)
        return name[0]
    }

    override fun bindFramebuffer(framebuffer: Int) = GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer)

    override fun framebufferDepthTexture(texture: Int) = GLES30.glFramebufferTexture2D(
        GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_TEXTURE_2D, texture, 0,
    )

    override fun framebufferComplete(): Boolean =
        GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) == GLES30.GL_FRAMEBUFFER_COMPLETE

    override fun deleteFramebuffer(framebuffer: Int) {
        name[0] = framebuffer
        GLES30.glDeleteFramebuffers(1, name, 0)
    }

    override fun disableColorBuffers() {
        GLES30.glDrawBuffers(1, intArrayOf(GLES30.GL_NONE), 0)
        GLES30.glReadBuffer(GLES30.GL_NONE)
    }

    override fun drawElements(mode: Int, count: Int, type: Int, offsetBytes: Int) =
        GLES20.glDrawElements(mode, count, type, offsetBytes)

    override fun drawArrays(mode: Int, first: Int, count: Int) = GLES30.glDrawArrays(mode, first, count)
}
