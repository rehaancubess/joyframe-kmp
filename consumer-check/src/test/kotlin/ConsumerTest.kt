import io.github.rehaancubess.joyframe.GameSession
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
}
