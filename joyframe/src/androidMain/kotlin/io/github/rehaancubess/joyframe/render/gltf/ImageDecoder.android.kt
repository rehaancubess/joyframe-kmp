// SPDX-License-Identifier: Apache-2.0
package io.github.rehaancubess.joyframe.render.gltf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.github.rehaancubess.joyframe.render.gpu.TextureData

internal actual fun decodeImage(bytes: ByteArray, label: String): TextureData? = decodeBitmap(bytes, label, sampleSize = 1)

internal actual suspend fun decodeImageAsync(
    bytes: ByteArray,
    mimeType: String?,
    label: String,
    maxEdge: Int,
): TextureData? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sampleSize = 1
    val longest = maxOf(bounds.outWidth, bounds.outHeight)
    while (longest > 0 && longest / (sampleSize * 2) >= maxEdge) sampleSize *= 2
    return decodeBitmap(bytes, label, sampleSize)?.downsampledTo(maxEdge)
}

private fun decodeBitmap(bytes: ByteArray, label: String, sampleSize: Int): TextureData? = try {
    val options = BitmapFactory.Options().apply {
        inPremultiplied = false
        inPreferredConfig = Bitmap.Config.ARGB_8888
        inSampleSize = sampleSize
    }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.let { bitmap ->
        val width = bitmap.width
        val height = bitmap.height
        val argb = IntArray(width * height)
        bitmap.getPixels(argb, 0, width, 0, 0, width, height)
        bitmap.recycle()
        argbToTexture(label, width, height, argb)
    }
} catch (t: Throwable) {
    println("gltf: $label failed to decode — $t")
    null
}
