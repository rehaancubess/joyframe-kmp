package io.github.rehaancubess.joyframe

import androidx.compose.ui.input.key.Key
import io.github.rehaancubess.joyframe.audio.*
import io.github.rehaancubess.joyframe.input.*
import io.github.rehaancubess.joyframe.render.*
import io.github.rehaancubess.joyframe.render.gpu.*
import io.github.rehaancubess.joyframe.render.math.Vec3
import kotlin.math.sqrt
import kotlin.test.*

class ConvenienceApiTest {
    @Test fun quickKeyboardActionBetweenFramesIsNotLost() {
        val input=ActionInput()
        input.key(Key.Spacebar,true); input.key(Key.Spacebar,false)
        val frame=input.poll()
        assertEquals(setOf(GameAction.Interact),frame.pressed)
        assertTrue(frame.held.isEmpty()); assertTrue(input.poll().pressed.isEmpty())
    }
    @Test fun inputCombinesSourcesWithoutDiagonalSpeedBoost() {
        val input = ActionInput()
        input.key(Key.W,true); input.key(Key.D,true)
        val frame = input.poll(GamepadState(leftStickX=1f,leftStickY=1f))
        assertEquals(1f,sqrt(frame.movement.x*frame.movement.x+frame.movement.y*frame.movement.y),.0001f)
        assertTrue(frame.movement.y>0f)
        input.clear(); assertEquals(Movement(),input.poll().movement)
    }
    @Test fun actionsUseEdgesAcrossKeyboardTouchAndController() {
        val input = ActionInput()
        input.key(Key.Spacebar,true)
        assertEquals(setOf(GameAction.Interact),input.poll().pressed)
        assertTrue(input.poll().pressed.isEmpty())
        input.touch(GameAction.Interact,true); input.key(Key.Spacebar,false)
        assertTrue(input.poll().pressed.isEmpty())
        input.touch(GameAction.Interact,false); input.poll()
        assertEquals(setOf(GameAction.Interact),input.poll(GamepadState(action=true)).pressed)
    }
    @Test fun invalidAxesAndDeadzoneAreSafe() {
        val input = ActionInput()
        input.touchMovement=Movement(Float.NaN,Float.POSITIVE_INFINITY)
        assertEquals(Movement(),input.poll(GamepadState(leftStickX=.1f,leftStickY=Float.NaN)).movement)
    }
    @Test fun sessionPauseDoesNotRetriggerOrAccumulateResumeTime() {
        val session = GameSession()
        session.step(0); assertEquals(.016f,session.step(16_000_000).deltaSeconds,.00001f)
        session.step(32_000_000,GamepadState(pause=true)); assertTrue(session.paused)
        session.step(48_000_000,GamepadState(pause=true)); assertTrue(session.paused)
        session.step(64_000_000)
        assertEquals(0f,session.step(9_000_000_000,GamepadState(pause=true)).deltaSeconds)
        assertFalse(session.paused)
        assertEquals(.016f,session.step(9_016_000_000).deltaSeconds,.00001f)
        session.foreground=false; assertEquals(0f,session.step(10_000_000_000).deltaSeconds)
        session.foreground=true; assertEquals(0f,session.step(11_000_000_000).deltaSeconds)
        session.close(); session.close(); assertFailsWith<IllegalStateException> { session.step(12_000_000_000) }
    }
    @Test fun primitivesHaveBoundedIndicesAndOutwardNormals() {
        val meshes = listOf(Primitives.box("box",Vec3(2f,4f,6f),"m"),Primitives.cone("cone",3f,7f,"m"))
        meshes.forEach { mesh ->
            assertTrue(mesh.indices.all { it in mesh.positions.indices })
            assertTrue(mesh.normals.all { abs(it.length()-1f)<.0001f })
        }
        assertFailsWith<IllegalArgumentException> { Primitives.box("bad",Vec3(-1f,1f,1f),"m") }
        assertFailsWith<IllegalArgumentException> { Primitives.water("bad",1f,"m",100000) }
    }
    @Test fun waterGridCarriesShaderAttributes() {
        val water = Primitives.water("lake",100f,"water",4)
        assertEquals(25,water.positions.size); assertEquals(96,water.indices.size)
        assertTrue(water.normals.all { it.x==1f }); assertTrue(water.textureCoordinates.all { it.x==1f })
    }
    @Test fun sceneBuilderValidatesReferencesAndDuplicates() {
        val assets = sceneAssets("test") {
            material("m",0xffffffff.toInt()); box("cube",Vec3(1f,1f,1f),"m"); model("cube",ModelPart("cube"))
        }
        assertEquals(1,assets.models.size)
        assertFailsWith<IllegalArgumentException> { sceneAssets("bad") { model("unknown",ModelPart("missing")) } }
        assertFailsWith<IllegalArgumentException> { sceneAssets("bad") { material("m",0); material("m",1) } }
    }
    @Test fun wavRoundTripAndTruncation() {
        val pcm=shortArrayOf(-32768,-100,0,100,32767)
        assertContentEquals(pcm,Wav.decode(Wav.encode(pcm)))
        assertContentEquals(Wav.encode(pcm),PcmSound.fromWav(Wav.encode(pcm)).wav)
        assertFailsWith<IllegalArgumentException> { Wav.decode(Wav.encode(pcm).copyOf(45)) }
        assertFailsWith<IllegalArgumentException> { Wav.decode(ByteArray(12)) }
    }
    @Test fun wavRejectsUnsupportedFormatAndOverflowingChunk() {
        val wav=Wav.encode(shortArrayOf(1,2))
        wav[20]=3
        assertFailsWith<IllegalArgumentException> { Wav.decode(wav) }
        val huge=Wav.encode(shortArrayOf(1,2))
        (40..43).forEach { huge[it]=255.toByte() }
        assertFailsWith<IllegalArgumentException> { Wav.decode(huge) }
    }
    private fun abs(value: Float)=kotlin.math.abs(value)
}
