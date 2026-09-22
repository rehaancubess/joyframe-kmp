// SPDX-License-Identifier: Apache-2.0
package io.github.rehaancubess.joyframe.render.gltf

import io.github.rehaancubess.joyframe.render.gpu.TextureData

internal expect fun decodeImage(bytes: ByteArray, label: String): TextureData?

internal fun argbToTexture(label: String, width: Int, height: Int, argb: IntArray): TextureData {
    val rgba = ByteArray(width * height * 4)
    for (i in argb.indices) {
        val pixel = argb[i]
        val base = i * 4
        rgba[base] = ((pixel ushr 16) and 0xFF).toByte()
        rgba[base + 1] = ((pixel ushr 8) and 0xFF).toByte()
        rgba[base + 2] = (pixel and 0xFF).toByte()
        rgba[base + 3] = ((pixel ushr 24) and 0xFF).toByte()
    }
    return TextureData(label, width, height, rgba)
}

internal expect suspend fun decodeImageAsync(
    bytes: ByteArray,
    mimeType: String?,
    label: String,
    maxEdge: Int,
): TextureData?

internal fun TextureData.downsampledTo(maxEdge: Int): TextureData {
    var current = this
    while (maxOf(current.width, current.height) > maxEdge && current.width > 1 && current.height > 1) {
        current = current.halved()
    }
    return current
}

private fun TextureData.halved(): TextureData {
    val outWidth = width / 2
    val outHeight = height / 2
    val out = ByteArray(outWidth * outHeight * 4)
    val stride = width * 4
    for (y in 0 until outHeight) {
        val top = (y * 2) * stride
        val bottom = top + stride
        for (x in 0 until outWidth) {
            val left = x * 8
            val o = (y * outWidth + x) * 4
            for (channel in 0 until 4) {
                val sum = (rgba[top + left + channel].toInt() and 0xFF) +
                    (rgba[top + left + 4 + channel].toInt() and 0xFF) +
                    (rgba[bottom + left + channel].toInt() and 0xFF) +
                    (rgba[bottom + left + 4 + channel].toInt() and 0xFF)
                out[o + channel] = ((sum + 2) / 4).toByte()
            }
        }
    }
    return TextureData(id, outWidth, outHeight, out)
}
