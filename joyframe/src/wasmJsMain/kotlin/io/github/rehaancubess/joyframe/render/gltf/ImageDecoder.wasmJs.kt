// SPDX-License-Identifier: Apache-2.0
package io.github.rehaancubess.joyframe.render.gltf

import io.github.rehaancubess.joyframe.render.gpu.TextureData
import kotlinx.coroutines.suspendCancellableCoroutine
import org.khronos.webgl.Int8Array
import org.khronos.webgl.toByteArray
import org.khronos.webgl.toInt8Array
import kotlin.coroutines.resume

internal actual fun decodeImage(bytes: ByteArray, label: String): TextureData? {
    println("gltf: $label not decoded — the browser build has no synchronous image decoder")
    return null
}

internal actual suspend fun decodeImageAsync(
    bytes: ByteArray,
    mimeType: String?,
    label: String,
    maxEdge: Int,
): TextureData? {
    if (bytes.isEmpty()) return null
    return suspendCancellableCoroutine { continuation ->
        decodeInBrowser(bytes.toInt8Array(), mimeType ?: "", maxEdge, { width, height, pixels ->
            if (continuation.isActive) {
                val rgba = pixels.toByteArray()
                continuation.resume(if (width > 0 && height > 0 && rgba.size == width * height * 4)
                    TextureData(label, width, height, rgba) else null)
            }
        }, { error ->
            println("gltf: $label failed to decode in the browser — $error")
            if (continuation.isActive) continuation.resume(null)
        })
    }
}

@JsFun(
    """(bytes, mime, maxEdge, success, failure) => {
        const blob = mime ? new Blob([bytes], { type: mime }) : new Blob([bytes]);
        createImageBitmap(blob, { premultiplyAlpha: 'none', colorSpaceConversion: 'none' }).then((bitmap) => {
            try {
            const scale = Math.min(1, Math.max(1, maxEdge) / Math.max(bitmap.width, bitmap.height));
            const width = Math.max(1, Math.round(bitmap.width * scale));
            const height = Math.max(1, Math.round(bitmap.height * scale));
            const canvas = new OffscreenCanvas(width, height);
            const context = canvas.getContext('2d', { willReadFrequently: true });
            context.drawImage(bitmap, 0, 0, width, height);
            const data = context.getImageData(0, 0, width, height).data;
            success(width, height, new Int8Array(data.buffer, data.byteOffset, data.byteLength));
            } finally { bitmap.close(); }
        }).catch(error => failure(String(error)));
    }""",
)
private external fun decodeInBrowser(bytes: Int8Array, mime: String, maxEdge: Int,
    success: (Int, Int, Int8Array) -> Unit, failure: (String) -> Unit)
