// SPDX-License-Identifier: Apache-2.0
package io.github.rehaancubess.joyframe.render.water

actual fun defaultWaterQuality(): WaterQuality =
    if (isCoarsePointerDevice()) WaterQuality.Low else WaterQuality.High

private fun isCoarsePointerDevice(): Boolean = js("window.matchMedia('(pointer: coarse)').matches")
