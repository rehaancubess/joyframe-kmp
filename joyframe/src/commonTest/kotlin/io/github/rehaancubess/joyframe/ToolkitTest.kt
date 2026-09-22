package io.github.rehaancubess.joyframe

import io.github.rehaancubess.joyframe.input.*
import io.github.rehaancubess.joyframe.audio.*
import io.github.rehaancubess.joyframe.render.gpu.*
import io.github.rehaancubess.joyframe.render.math.Vec3
import kotlin.test.*

class ToolkitTest {
    @Test fun clockStartsAtZeroAndCapsResumeGap() {
        val clock = FrameClock()
        assertEquals(0f, clock.advance(100))
        assertEquals(.05f, clock.advance(2_000_000_100))
        clock.reset()
        assertEquals(0f, clock.advance(3_000_000_100))
    }
    @Test fun deadzoneAndNonFiniteInputAreSafe() {
        assertEquals(0f,GamepadMapping.axis(.1f))
        assertEquals(0f,GamepadMapping.axis(Float.NaN))
        assertEquals(1f,GamepadMapping.axis(1f))
        assertEquals(-1f,GamepadMapping.axis(1f,invert=true))
        assertFailsWith<IllegalArgumentException> { GamepadOptions(deadzone=1f) }
    }
    @Test fun mappingKeepsIndependentButtonsIndependent() {
        val state=GamepadMapping.apply(GamepadState(cancel=true),GamepadOptions(swapConfirmCancel=true))
        assertTrue(state.confirm)
        assertFalse(state.cancel)
        assertFalse(state.action)
    }
    @Test fun pcmIsCopiedAndEncoded() {
        val samples=shortArrayOf(123,456)
        val sound=PcmSound(samples)
        samples[0]=0
        assertEquals(48,sound.wav.size)
        assertEquals(123,sound.wav[44].toInt())
        assertEquals("RIFF",sound.wav.take(4).map { it.toInt().toChar() }.joinToString(""))
        assertFailsWith<IllegalArgumentException> { PcmSound.tone(seconds=Float.NaN) }
    }
    @Test fun transformInterpolationTakesShortRotationPath() {
        val a=Transform3D(rotation=Vec3(0f,3f,0f))
        val b=Transform3D(rotation=Vec3(0f,-3f,0f))
        assertTrue(a.interpolateTo(b,.5f).rotation.y > 3f)
    }
    @Test fun sceneRejectsMissingMesh() {
        assertFailsWith<IllegalArgumentException> {
            GpuSceneAssets("bad",emptyMap(),emptyMap(),mapOf("model" to ModelDefinition("model",listOf(ModelPart("missing")))))
        }
    }
}
