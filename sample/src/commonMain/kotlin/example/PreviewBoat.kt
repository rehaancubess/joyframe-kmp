package example

import example.resources.Res
import io.github.rehaancubess.joyframe.render.gltf.*
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.MissingResourceException
import kotlin.math.PI

/** Optional local-only resource, injected by -Pjoyframe.demoBoat. No app dependency or hardcoded path. */
suspend fun loadPreviewBoat(): GltfModel? {
    if(Res.readBytes("files/preview-mode.txt").decodeToString() != "local") return null
    val bytes=try { Res.readBytes("files/demo-boat.glb") }
    catch(cancelled: CancellationException) { throw cancelled }
    catch(missing: MissingResourceException) { return null }
    return previewBoatFrom(bytes)
}

/** Fits a boat GLB (bow along +Z) to Lake Lab's hull size and waterline. */
suspend fun previewBoatFrom(bytes: ByteArray): GltfModel {
    val loaded=GltfLoader.loadGlbAsync(bytes,"preview-boat",maxTextureEdge=1024)
        .fitTo(length=300f,beam=156f,bowYawRadians=PI.toFloat()/2,sink=22f)
    return GltfModel(loaded.meshes,loaded.materials.mapValues { (_,m) -> m.copy(roughness=.42f,metallic=.06f) },
        loaded.textures,loaded.model)
}
