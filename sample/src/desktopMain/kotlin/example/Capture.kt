// SPDX-License-Identifier: Apache-2.0
package example

import io.github.rehaancubess.joyframe.FixedTimestep
import io.github.rehaancubess.joyframe.input.Movement
import io.github.rehaancubess.joyframe.render.*
import io.github.rehaancubess.joyframe.render.gpu.GpuSceneFrame
import io.github.rehaancubess.joyframe.render.gpu.InstanceKind
import io.github.rehaancubess.joyframe.render.gpu.SceneEnvironment
import io.github.rehaancubess.joyframe.render.gpu.PackedColor
import io.github.rehaancubess.joyframe.render.gpu.Transform3D
import io.github.rehaancubess.joyframe.render.math.Vec3
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.PI

/**
 * Renders Lake Lab without a window: reference stills of every feature, plus numbered frames of a
 * short scripted drive for the README animation. macOS only (OffscreenRenderer).
 * Pass -Pjoyframe.demoBoat=path/to/boat.glb to sail a local model instead of the procedural boat;
 * the model is only read, never copied into the repository.
 *
 *     ./gradlew :sample:captureDemo
 */
object Capture {
    @JvmStatic fun main(args: Array<String>) {
        val out = File(args.firstOrNull() ?: "build/capture").apply { mkdirs() }
        val water = lagoonWater()
        val local = args.getOrNull(1)?.let { path -> kotlinx.coroutines.runBlocking { previewBoatFrom(File(path).readBytes()) } }
        val importedId = local?.model?.id
        val scene = lakeAssets(true)
        val pools = local?.mergeInto(scene, scene.cacheKey + "-local") ?: scene
        println(if (local == null) "procedural boat" else "local boat: ${args[1]}")
        OffscreenRenderer().use { renderer ->
            fun still(name: String, frames: List<GpuSceneFrame>, width: Int = 1280, height: Int = 720) {
                ImageIO.write(renderer.render(frames, width, height, gapPixels = 6), "png", File(out, "$name.png"))
                println("wrote $name.png")
            }
            val boat = BoatMotion(-120f, 180f, .55f)
            val chase = ChaseCamera(lakeChaseStyle(1.12f))
            fun chaseFrame(look: LakeLook, seconds: Float = 4f, boats: List<BoatMotion> = listOf(boat), viewer: BoatMotion = boat) =
                lakeFrame(pools, chase.desired(viewer.position(), viewer.forward(), .6f), boats, water, seconds, 1, look,
                    importedModelId = importedId, viewer = viewer)
            still("overview", listOf(lakeFrame(pools, overviewCamera(1.12f), listOf(BoatMotion()), water, 4f, 1, LakeLook(),
                importedModelId = importedId)))
            still("chase", listOf(chaseFrame(LakeLook())))
            Weather.entries.filter { it != Weather.Clear }.forEach { kind ->
                still("weather-${kind.name.lowercase()}", listOf(chaseFrame(LakeLook(weather = kind))))
            }
            val rival = BoatMotion(260f, -120f, 2.4f)
            val both = listOf(boat, rival)
            still("split", listOf(
                lakeFrame(pools, ChaseCamera(lakeChaseStyle(1.12f)).desired(boat.position(), boat.forward(), .5f), both, water, 4f, 1, LakeLook(),
                    importedModelId = importedId, viewer = boat),
                lakeFrame(pools, ChaseCamera(lakeChaseStyle(1.12f)).desired(rival.position(), rival.forward(), .5f), both, water, 4f, 1, LakeLook(),
                    importedModelId = importedId, viewer = rival),
            ))
            val pool = lakePools[0]
            val close = io.github.rehaancubess.joyframe.render.gpu.GpuCamera(Vec3(pool.x + 520f, 520f, pool.z + 520f),
                Vec3(pool.x, 0f, pool.z), verticalFovDegrees = 45f, nearPlane = 10f, farPlane = 9000f)
            still("whirlpool", listOf(lakeFrame(pools, close, emptyList(), water, 4f, 1, LakeLook())))
            val hangarCamera = turntableCamera(pools, importedId ?: "boat")
            still("hangar", listOf(pools.frame(hangarCamera, 1f, environment = SceneEnvironment(
                clearColor = PackedColor(0xff16323d.toInt()), fogStart = 1e6f, fogEnd = 2e6f)) {
                instance("boat", importedId ?: "boat", Transform3D(rotation = Vec3(0f, .7f, 0f)), kind = InstanceKind.Dynamic,
                    materials = if (importedId == null) mapOf("hull" to "hull") else emptyMap())
            }), 900, 600)
            animation(renderer, File(out, "frames").apply { deleteRecursively(); mkdirs() }, water, pools, importedId)
        }
    }

    /** A scripted six-second drive: chase camera past a whirlpool, then two players, then snow. */
    private fun animation(renderer: OffscreenRenderer, dir: File, water: io.github.rehaancubess.joyframe.render.water.WaterConfig,
                          assets: io.github.rehaancubess.joyframe.render.gpu.GpuSceneAssets, importedId: String?) {
        val fps = 20
        // Both boats circle the middle of the lake, skirting the whirlpools' outer pull.
        val one = BoatMotion(0f, 380f, 0f)
        val two = BoatMotion(0f, -380f, PI.toFloat())
        val cameras = listOf(ChaseCamera(lakeChaseStyle(1.05f)), ChaseCamera(lakeChaseStyle(1.05f)))
        val fixed = FixedTimestep()
        val total = fps * 7
        for (index in 0 until total) {
            val seconds = index / fps.toFloat()
            val split = seconds in 2.6f..4.8f
            val look = LakeLook(weather = if (seconds > 4.8f) Weather.Snow else Weather.Clear)
            repeat(fixed.advance(1f / fps)) {
                one.step(Movement(-.72f, 1f), Steering.Drive, fixed.stepSeconds, lakePools)
                two.step(Movement(-.72f, .95f), Steering.Drive, fixed.stepSeconds, lakePools)
                one.separateFrom(two)
            }
            val boats = if (split) listOf(one, two) else listOf(one)
            val frames = boats.mapIndexed { i, boat ->
                val camera = cameras[i].update(boat.position(fixed.alpha), boat.forward(fixed.alpha), boat.speed, 1f / fps)
                lakeFrame(assets, camera, if (split) listOf(one, two) else listOf(one), water, seconds, index.toLong(), look,
                    fixed.alpha, importedId, viewer = boat)
            }
            ImageIO.write(renderer.render(frames, 640, 360, gapPixels = 3), "png", File(dir, "frame-%03d.png".format(index)))
        }
        println("wrote $total animation frames to $dir")
    }
}
