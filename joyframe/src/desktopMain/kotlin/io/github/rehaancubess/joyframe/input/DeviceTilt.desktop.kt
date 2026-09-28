// SPDX-License-Identifier: Apache-2.0
package io.github.rehaancubess.joyframe.input

/** Desktops have no gravity sensor. */
actual object DeviceTilt {
    actual val available: Boolean get() = false
    actual fun start(fullLockDegrees: Float) = Unit
    actual fun stop() = Unit
    actual fun recenter() = Unit
    actual val steer: Float get() = 0f
}
