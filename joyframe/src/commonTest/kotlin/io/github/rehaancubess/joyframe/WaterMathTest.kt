package io.github.rehaancubess.joyframe

import io.github.rehaancubess.joyframe.render.water.*
import io.github.rehaancubess.joyframe.render.math.*
import io.github.rehaancubess.joyframe.render.gpu.Bounds3
import kotlin.test.*

class WaterMathTest {
    @Test fun cpuWaterSlopesMatchHeightDerivatives() {
        val water=WaterConfig()
        val step=.1f
        for(x in listOf(-250f,0f,312f)) for(z in listOf(-85f,40f)) {
            val slope=water.slopeAt(x,z,3.2f,1f)
            val dx=(water.heightAt(x+step,z,3.2f,1f)-water.heightAt(x-step,z,3.2f,1f))/(2*step)
            val dz=(water.heightAt(x,z+step,3.2f,1f)-water.heightAt(x,z-step,3.2f,1f))/(2*step)
            assertEquals(dx,slope.alongX,.003f)
            assertEquals(dz,slope.alongZ,.003f)
        }
    }
    @Test fun zeroEnergyMeansNoDisplacement() {
        assertEquals(0f,WaterConfig().heightAt(12f,34f,56f,0f))
    }
    @Test fun clipDepthConventionsHaveDifferentNearPlanes() {
        val identity=FloatArray(16).also(Mat::identity)
        val bounds=Bounds3(Vec3(-.1f,-.1f,-.8f),Vec3(.1f,.1f,-.6f))
        val frustum=BoundsFrustum()
        frustum.update(identity,DepthRange.NegativeOneToOne)
        assertTrue(frustum.intersects(bounds,identity))
        frustum.update(identity,DepthRange.ZeroToOne)
        assertFalse(frustum.intersects(bounds,identity))
    }
}
