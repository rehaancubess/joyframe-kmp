package example

import io.github.rehaancubess.joyframe.input.Movement
import io.github.rehaancubess.joyframe.render.water.WaterConfig
import kotlin.test.*

class LakeSceneTest {
    @Test fun boatStaysInsideBanksAndResetReturnsToOrigin() {
        val boat=BoatMotion()
        repeat(2000) { boat.step(Movement(1f,1f),Steering.Map,1f/60) }
        assertTrue(boat.x in 800f..900f); assertTrue(boat.z in -900f..-800f)
        boat.reset(); assertEquals(0f,boat.x); assertEquals(0f,boat.z); assertEquals(0f,boat.yaw)
    }
    @Test fun driveSteeringThrottlesAlongTheBowAndTurnsRightOnRightInput() {
        val boat=BoatMotion()
        repeat(60) { boat.step(Movement(0f,1f),Steering.Drive,1f/60) }
        assertTrue(boat.x > 100f); assertEquals(0f,boat.z,.001f); assertTrue(boat.speed > .3f)
        repeat(30) { boat.step(Movement(1f,1f),Steering.Drive,1f/60) }
        assertTrue(boat.yaw < 0f, "right steer turns toward +Z, the boat's right")
        assertTrue(boat.z > 0f)
    }
    @Test fun whirlpoolCoreSwallowsAndRespawnsTheBoat() {
        val boat=BoatMotion(lakePools[0].x+40f,lakePools[0].z)
        val swallowed = (0 until 240).any { boat.step(Movement(),Steering.Drive,1f/60,lakePools) }
        assertTrue(swallowed)
        assertEquals(lakePools[0].x+40f,boat.x,.001f)
    }
    @Test fun lakeHasBoatTreesPoolsAndUsesMatchingWaterHeight() {
        val boat=BoatMotion(); val water=WaterConfig()
        val frame=lakeFrame(lakeAssets(true),overviewCamera(1f),listOf(boat),water,2f,10,LakeLook())
        assertEquals(36,frame.instances.count { it.modelId=="tree" })
        assertEquals(2,frame.instances.count { it.modelId.startsWith("pool-") })
        assertEquals(water.heightAt(0f,0f,2f,lakeEnergyAt(0f,0f))+24f,frame.instances.single { it.id=="boat-0" }.transform.translation.y,.0001f)
        val plain=lakeFrame(lakeAssets(false),overviewCamera(1f),listOf(boat,BoatMotion(300f,0f)),water,2f,10,LakeLook(whirlpools=false))
        assertEquals(setOf("hull","hull-blue"),plain.instances.filter { it.id.startsWith("boat-") }.map { it.materials.getValue("hull") }.toSet())
    }
    @Test fun generatedAudioIsAnEightSecondLoopAndAFullBank() {
        assertEquals(8f,lakeMusic().durationSeconds,.0001f)
        assertEquals(setOf(LakeSounds.hornOne,LakeSounds.hornTwo,LakeSounds.bell,LakeSounds.splash),LakeSounds.bank().keys)
    }
}
