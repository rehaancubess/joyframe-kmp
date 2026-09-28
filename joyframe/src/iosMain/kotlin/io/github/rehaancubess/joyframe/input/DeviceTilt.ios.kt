// SPDX-License-Identifier: Apache-2.0
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package io.github.rehaancubess.joyframe.input

import kotlinx.cinterop.useContents
import platform.CoreMotion.CMMotionManager

/** Core Motion keeps its latest gravity ready; [steer] reads it when asked instead of being pushed. */
actual object DeviceTilt {
    private val manager = CMMotionManager().apply { deviceMotionUpdateInterval = 1.0 / 60.0 }
    private val tracker = TiltTracker()

    actual val available: Boolean get() = manager.deviceMotionAvailable

    actual fun start(fullLockDegrees: Float) {
        tracker.fullLockDegrees = fullLockDegrees
        if (!manager.deviceMotionActive && manager.deviceMotionAvailable) {
            tracker.recenter()
            manager.startDeviceMotionUpdates()
        }
    }

    actual fun stop() {
        if (manager.deviceMotionActive) manager.stopDeviceMotionUpdates()
        tracker.recenter()
    }

    actual fun recenter() = tracker.recenter()

    actual val steer: Float get() {
        val motion = manager.deviceMotion ?: return 0f
        motion.gravity.useContents { tracker.reading(x, y) }
        return tracker.steer
    }
}
