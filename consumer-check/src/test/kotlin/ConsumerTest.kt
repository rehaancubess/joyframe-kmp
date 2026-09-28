import io.github.rehaancubess.joyframe.FixedTimestep
import io.github.rehaancubess.joyframe.GameSession
import io.github.rehaancubess.joyframe.audio.SpatialMix
import io.github.rehaancubess.joyframe.input.GamepadSeats
import io.github.rehaancubess.joyframe.render.ChaseCamera
import io.github.rehaancubess.joyframe.render.SplitLayout
import io.github.rehaancubess.joyframe.render.math.Vec3
import io.github.rehaancubess.joyframe.render.water.Whirlpool
import io.github.rehaancubess.joyframe.audio.PcmSound
import io.github.rehaancubess.joyframe.input.GamepadState
import io.github.rehaancubess.joyframe.render.sceneAssets
import io.github.rehaancubess.joyframe.render.gpu.ModelPart
import io.github.rehaancubess.joyframe.render.gltf.GltfModel
import io.github.rehaancubess.joyframe.render.gltf.fitTo
import kotlin.test.*

class ConsumerTest {
    @Test fun publicApiWorksFromMavenArtifactWithoutProjectDependency() {
        val assets=sceneAssets("consumer") {
            material("blue",0xff087e8b.toInt(),water=true)
            water("surface",100f,"blue",8)
            model("lake",ModelPart("surface"))
        }
        assertEquals(81,assets.meshes.getValue("surface").positions.size)
        val fitted=GltfModel(assets.meshes,assets.materials,assets.textures,assets.models.getValue("lake"))
            .fitTo(200f,100f)
        assertEquals(200f,fitted.meshes.getValue("surface").bounds.let { it.maximum.x-it.minimum.x })
        val session=GameSession()
        assertEquals(1f,session.step(0,GamepadState(leftStickY=1f)).input.movement.y)
        PcmSound.tone()
        session.close()
    }
    @Test fun alpha03ApisAreInThePublishedArtifact() {
        assertTrue(ChaseCamera().desired(Vec3.ZERO,Vec3(1f,0f,0f)).eye.x < 0f)
        assertEquals(2,FixedTimestep(.5f).advance(1f))
        assertEquals(2,SplitLayout.panes(2,800,600).size)
        assertEquals(0,GamepadSeats().occupied)
        assertTrue(Whirlpool(0f,0f).influenceAt(0f,0f) > 0f)
        assertTrue(SpatialMix.of(0f,0f,1f,0f,0f,100f,1000f).pan > 0f)
    }
    @Test fun alpha04SmoothnessAndTiltApisArePublished() {
        assertEquals(1920,io.github.rehaancubess.joyframe.render.GameViewOptions(renderScale=1f).renderSize(2796,1290).first)
        assertEquals(0,io.github.rehaancubess.joyframe.FramePacing().report.frames)
        assertEquals(0f,io.github.rehaancubess.joyframe.input.TiltMath.steer(0f,0f,26f))
    }
}
