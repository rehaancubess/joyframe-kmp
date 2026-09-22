// SPDX-License-Identifier: Apache-2.0
package io.github.rehaancubess.joyframe.render.gl

internal interface GlApi {

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

    fun createShader(type: Int): Int
    fun shaderSource(shader: Int, source: String)
    fun compileShader(shader: Int)
    fun shaderCompiled(shader: Int): Boolean
    fun shaderInfoLog(shader: Int): String
    fun deleteShader(shader: Int)

    fun createProgram(): Int
    fun attachShader(program: Int, shader: Int)
    fun bindAttribLocation(program: Int, index: Int, name: String)
    fun linkProgram(program: Int)
    fun programLinked(program: Int): Boolean
    fun programInfoLog(program: Int): String
    fun useProgram(program: Int)
    fun deleteProgram(program: Int)


    fun uniformLocation(program: Int, name: String): Int
    fun uniformMatrix4(location: Int, value: FloatArray)
    fun uniform4(location: Int, x: Float, y: Float, z: Float, w: Float)
    fun uniform2(location: Int, x: Float, y: Float)
    fun uniform1f(location: Int, value: Float)
    fun uniform1i(location: Int, value: Int)

    fun genBuffer(): Int
    fun bindBuffer(target: Int, buffer: Int)
    fun bufferData(target: Int, data: FloatArray, usage: Int)
    fun bufferData(target: Int, data: IntArray, usage: Int)

    fun bufferStorage(target: Int, sizeBytes: Int, usage: Int)

    fun bufferSubData(target: Int, offsetBytes: Int, data: FloatArray, count: Int)
    fun deleteBuffer(buffer: Int)

    fun genVertexArray(): Int
    fun bindVertexArray(array: Int)
    fun deleteVertexArray(array: Int)
    fun enableVertexAttribArray(index: Int)
    fun vertexAttribPointer(
        index: Int,
        size: Int,
        type: Int,
        normalized: Boolean,
        strideBytes: Int,
        offsetBytes: Int,
    )

    fun genTexture(): Int
    fun bindTexture(target: Int, texture: Int)
    fun activeTexture(unit: Int)
    fun texParameteri(target: Int, name: Int, value: Int)

    fun texImage2DRgba(width: Int, height: Int, pixels: ByteArray?)

    fun texImage2DDepth(width: Int, height: Int)
    fun generateMipmap(target: Int)
    fun deleteTexture(texture: Int)

    fun genFramebuffer(): Int
    fun bindFramebuffer(framebuffer: Int)
    fun framebufferDepthTexture(texture: Int)
    fun framebufferComplete(): Boolean
    fun deleteFramebuffer(framebuffer: Int)

    fun disableColorBuffers()

    fun drawElements(mode: Int, count: Int, type: Int, offsetBytes: Int)
    fun drawArrays(mode: Int, first: Int, count: Int)
}

internal object GlConst {
    const val DEPTH_TEST = 0x0B71
    const val BLEND = 0x0BE2
    const val CULL_FACE = 0x0B44
    const val POLYGON_OFFSET_FILL = 0x8037

    const val COLOR_BUFFER_BIT = 0x4000
    const val DEPTH_BUFFER_BIT = 0x0100

    const val LESS = 0x0201
    const val LEQUAL = 0x0203
    const val ALWAYS = 0x0207

    const val SRC_ALPHA = 0x0302
    const val ONE_MINUS_SRC_ALPHA = 0x0303
    const val ONE = 1
    const val ZERO = 0

    const val BACK = 0x0405
    const val FRONT = 0x0404
    const val CCW = 0x0901

    const val VERTEX_SHADER = 0x8B31
    const val FRAGMENT_SHADER = 0x8B30

    const val ARRAY_BUFFER = 0x8892
    const val ELEMENT_ARRAY_BUFFER = 0x8893
    const val STATIC_DRAW = 0x88E4
    const val DYNAMIC_DRAW = 0x88E8

    const val FLOAT = 0x1406
    const val UNSIGNED_INT = 0x1405

    const val TEXTURE_2D = 0x0DE1
    const val TEXTURE0 = 0x84C0
    const val TEXTURE_MIN_FILTER = 0x2801
    const val TEXTURE_MAG_FILTER = 0x2800
    const val TEXTURE_WRAP_S = 0x2802
    const val TEXTURE_WRAP_T = 0x2803
    const val NEAREST = 0x2600
    const val LINEAR = 0x2601
    const val LINEAR_MIPMAP_LINEAR = 0x2703
    const val CLAMP_TO_EDGE = 0x812F
    const val REPEAT = 0x2901
    const val TEXTURE_COMPARE_MODE = 0x884C
    const val TEXTURE_COMPARE_FUNC = 0x884D
    const val COMPARE_REF_TO_TEXTURE = 0x884E

    const val TRIANGLES = 0x0004
}
