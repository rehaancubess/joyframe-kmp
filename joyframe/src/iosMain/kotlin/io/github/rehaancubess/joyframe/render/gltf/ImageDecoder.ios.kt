// SPDX-License-Identifier: Apache-2.0
@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package io.github.rehaancubess.joyframe.render.gltf

import io.github.rehaancubess.joyframe.render.gpu.TextureData
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGImageAlphaInfo
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIImage

internal actual fun decodeImage(bytes: ByteArray, label: String): TextureData? =
    decodeImageScaled(bytes, label, maxEdge = Int.MAX_VALUE)

internal actual suspend fun decodeImageAsync(
    bytes: ByteArray,
    mimeType: String?,
    label: String,
    maxEdge: Int,
): TextureData? = decodeImageScaled(bytes, label, maxEdge)

private fun decodeImageScaled(bytes: ByteArray, label: String, maxEdge: Int): TextureData? {
    if (bytes.isEmpty()) return null

    val data = bytes.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
    }
    val cgImage = UIImage.imageWithData(data)?.CGImage ?: run {
        println("gltf: $label is not an image UIKit recognises")
        return null
    }

    val sourceWidth = CGImageGetWidth(cgImage).toInt()
    val sourceHeight = CGImageGetHeight(cgImage).toInt()
    if (sourceWidth <= 0 || sourceHeight <= 0) return null
    var width = sourceWidth
    var height = sourceHeight
    while (maxOf(width, height) > maxEdge && width > 1 && height > 1) {
        width /= 2
        height /= 2
    }

    val rgba = ByteArray(width * height * 4)
    val colorSpace = CGColorSpaceCreateDeviceRGB() ?: return null
    rgba.usePinned { pinned ->
        val context = CGBitmapContextCreate(
            data = pinned.addressOf(0),
            width = width.toULong(),
            height = height.toULong(),
            bitsPerComponent = 8uL,
            bytesPerRow = (width * 4).toULong(),
            space = colorSpace,
            bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value,
        )
        if (context != null) {
            CGContextDrawImage(
                context,
                CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()),
                cgImage,
            )
            CGContextRelease(context)
        }
    }
    CGColorSpaceRelease(colorSpace)

    unpremultiply(rgba)
    return TextureData(label, width, height, rgba)
}

private fun unpremultiply(rgba: ByteArray) {
    var i = 0
    while (i < rgba.size) {
        val alpha = rgba[i + 3].toInt() and 0xFF
        if (alpha != 0 && alpha != 255) {
            val scale = 255f / alpha
            rgba[i] = (((rgba[i].toInt() and 0xFF) * scale).toInt().coerceAtMost(255)).toByte()
            rgba[i + 1] = (((rgba[i + 1].toInt() and 0xFF) * scale).toInt().coerceAtMost(255)).toByte()
            rgba[i + 2] = (((rgba[i + 2].toInt() and 0xFF) * scale).toInt().coerceAtMost(255)).toByte()
        }
        i += 4
    }
}
