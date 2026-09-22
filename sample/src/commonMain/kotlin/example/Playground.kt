package example

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.rehaancubess.joyframe.FrameClock
import io.github.rehaancubess.joyframe.audio.*
import io.github.rehaancubess.joyframe.input.PlatformGamepad
import io.github.rehaancubess.joyframe.render.GameView
import io.github.rehaancubess.joyframe.render.gpu.*
import io.github.rehaancubess.joyframe.render.math.Vec3
import kotlinx.coroutines.isActive

private val ping = SoundId("ping")
private val assets = run {
    val points = listOf(Vec3(-100f,-80f,0f), Vec3(100f,-80f,0f), Vec3(0f,100f,0f))
    val mesh = IndexedMesh("triangle", points, List(3) { Vec3(0f,0f,1f) }, emptyList(),
        listOf(0,1,2), listOf(MeshPrimitive(0,3,"mint")), Bounds3(Vec3(-100f,-80f,0f),Vec3(100f,100f,0f)))
    GpuSceneAssets("playground-v1", mapOf("triangle" to mesh),
        mapOf("mint" to GpuMaterial("mint",PackedColor(0xff3cdbc0.toInt()))),
        mapOf("triangle" to ModelDefinition("triangle",listOf(ModelPart("triangle")))))
}

/** No game assets, server, credentials or app resources are needed by this sample. */
@Composable
fun Playground() {
    var seconds by remember { mutableStateOf(0f) }
    var active by remember { mutableStateOf(true) }
    var yaw by remember { mutableStateOf(0f) }
    var controller by remember { mutableStateOf("Connect a controller, or use Rotate") }
    LaunchedEffect(Unit) { Audio.prepare(mapOf(ping to PcmSound.tone())) }
    DisposableEffect(Unit) { onDispose { Audio.stop() } }
    LaunchedEffect(active) {
        Audio.setForeground(active)
        if (!active) return@LaunchedEffect
        val clock = FrameClock()
        var previousAction = false
        while (isActive) {
            val dt = clock.advance(withFrameNanos { it })
            seconds += dt
            val pad = PlatformGamepad.poll()
            yaw += (pad?.horizontal ?: 0f) * dt * 2f
            if (pad?.action == true && !previousAction) Audio.play(ping)
            previousAction = pad?.action == true
            controller = PlatformGamepad.status().displayName
        }
    }
    val transform = Transform3D(rotation = Vec3(0f,yaw,0f))
    val frame = GpuSceneFrame(assets,
        GpuCamera(Vec3(0f,60f,430f),Vec3.ZERO,verticalFovDegrees=55f,nearPlane=1f,farPlane=5000f),
        SceneEnvironment(), listOf(DirectionalLight(Vec3(-1f,-1f,-1f))),
        listOf(SceneInstance("demo",InstanceKind.Dynamic,"triangle",transform,transform,1f,emptyMap())),
        simulationTick=(seconds*60).toLong(),renderTimeSeconds=seconds)
    MaterialTheme {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick={ active = !active }) { Text(if(active) "Pause" else "Resume") }
                Button(onClick={ yaw += .3f }) { Text("Rotate") }
                Button(onClick={ Audio.play(ping) }) { Text("Play sound") }
                Text(controller)
            }
            GameView(frame, Modifier.fillMaxWidth().weight(1f), active)
        }
    }
}
