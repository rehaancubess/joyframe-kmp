package example

import io.github.rehaancubess.joyframe.input.Movement
import io.github.rehaancubess.joyframe.render.water.WaterConfig
import kotlin.test.*

class LakeSceneTest {
    @Test fun boatStaysInsideBanksAndResetReturnsToOrigin() {
        val boat=BoatMotion()
        repeat(1000) { boat.advance(Movement(1f,1f),.05f) }
        assertEquals(900f,boat.x); assertEquals(-900f,boat.z)
        boat.reset(); assertEquals(0f,boat.x); assertEquals(0f,boat.z); assertEquals(0f,boat.yaw)
    }
    @Test fun lakeHasBoatAndThirtySixTreesAndUsesMatchingWaterHeight() {
        val boat=BoatMotion(); val water=WaterConfig()
        val frame=lakeFrame(boat,water,2f,10,1f,2.5f)
        assertEquals(36,frame.instances.count { it.modelId=="tree" })
        assertEquals(water.heightAt(0f,0f,2f,lakeEnergyAt(0f,0f))+24f,frame.instances.single { it.id=="boat" }.transform.translation.y)
    }
}
