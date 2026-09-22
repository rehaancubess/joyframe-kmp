// SPDX-License-Identifier: Apache-2.0
package io.github.rehaancubess.joyframe.render.gltf

import io.github.rehaancubess.joyframe.render.gpu.TextureData
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

internal actual fun decodeImage(bytes: ByteArray, label: String): TextureData? = try {
    ImageIO.read(ByteArrayInputStream(bytes))?.let { image ->
        val width = image.width
        val height = image.height
        argbToTexture(label, width, height, image.getRGB(0, 0, width, height, null, 0, width))
    }
} catch (t: Throwable) {
    println("gltf: $label failed to decode — $t")
    null
}

internal actual suspend fun decodeImageAsync(
    bytes: ByteArray,
    mimeType: String?,
    label: String,
    maxEdge: Int,
): TextureData? = decodeImage(bytes, label)?.downsampledTo(maxEdge)
