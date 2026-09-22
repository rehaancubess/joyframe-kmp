// SPDX-License-Identifier: Apache-2.0
package io.github.rehaancubess.joyframe.render.gl

internal object GlProgramBuilder {

    fun link(gl: GlApi, label: String, vertexSource: String, fragmentSource: String): Int? {
        val vertex = compile(gl, label, GlConst.VERTEX_SHADER, vertexSource) ?: return null
        val fragment = compile(gl, label, GlConst.FRAGMENT_SHADER, fragmentSource)
        if (fragment == null) {
            gl.deleteShader(vertex)
            return null
        }

        val program = gl.createProgram()
        gl.attachShader(program, vertex)
        gl.attachShader(program, fragment)
        gl.bindAttribLocation(program, GlShaders.ATTRIB_POSITION, "aPosition")
        gl.bindAttribLocation(program, GlShaders.ATTRIB_NORMAL, "aNormal")
        gl.bindAttribLocation(program, GlShaders.ATTRIB_COLOR, "aColor")
        gl.bindAttribLocation(program, GlShaders.ATTRIB_TEXCOORD, "aTexCoord")
        gl.linkProgram(program)
        gl.deleteShader(vertex)
        gl.deleteShader(fragment)

        if (!gl.programLinked(program)) {
            println("GL: could not link the '$label' program — ${gl.programInfoLog(program).trim()}")
            gl.deleteProgram(program)
            return null
        }
        return program
    }

    private fun compile(gl: GlApi, label: String, type: Int, source: String): Int? {
        val stage = if (type == GlConst.VERTEX_SHADER) "vertex" else "fragment"
        val shader = gl.createShader(type)
        if (shader == 0) {
            println("GL: could not create the '$label' $stage shader")
            return null
        }
        gl.shaderSource(shader, source)
        gl.compileShader(shader)
        if (!gl.shaderCompiled(shader)) {
            println("GL: could not compile the '$label' $stage shader — ${gl.shaderInfoLog(shader).trim()}")
            gl.deleteShader(shader)
            return null
        }
        val log = gl.shaderInfoLog(shader).trim()
        if (log.isNotEmpty()) println("GL: '$label' $stage shader compiled with notes — $log")
        return shader
    }
}
