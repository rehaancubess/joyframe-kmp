// SPDX-License-Identifier: Apache-2.0
package example

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rehaancubess.joyframe.GameSession
import io.github.rehaancubess.joyframe.audio.*
import io.github.rehaancubess.joyframe.input.*
import io.github.rehaancubess.joyframe.render.GameView
import io.github.rehaancubess.joyframe.render.water.WaterConfig
import io.github.rehaancubess.joyframe.render.gltf.GltfModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

private val horn = SoundId("boat-horn")
private val sampleCode = """
    // Same loader on every target
    GltfLoader.loadGlbAsync(bytes, "boat")
        .fitTo(300f, 156f, PI / 2, 22f)

    // Match shoreline wave energy
    val energy = lakeEnergyAt(x, z)
    water.heightAt(x, z, time, energy)

    // Keyboard / touch / controller
    val step = session.step(nanos, pad)
    boat.advance(step.input.movement,
                 step.deltaSeconds)
""".trimIndent()

@Composable
fun Playground() {
    val session = remember { GameSession() }
    val boat = remember { BoatMotion() }
    val focus = remember { FocusRequester() }
    val window = LocalWindowInfo.current
    var seconds by remember { mutableStateOf(0f) }
    var revision by remember { mutableStateOf(0L) }
    var paused by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }
    var waves by remember { mutableStateOf(1f) }
    var zoom by remember { mutableStateOf(1.12f) }
    var sunlight by remember { mutableStateOf(1.876f) }
    var deadzone by remember { mutableStateOf(.15f) }
    var controller by remember { mutableStateOf("No controller") }
    var frameMs by remember { mutableStateOf(0f) }
    val baseWater = remember { lagoonWater() }
    var previewBoat by remember { mutableStateOf<GltfModel?>(null) }
    var modelStatus by remember { mutableStateOf("Loading optional model") }
    LaunchedEffect(Unit) {
        try {
            previewBoat=loadPreviewBoat()
            modelStatus=if(previewBoat==null) "Procedural boat" else "Local GLB preview"
        } catch(cancelled: CancellationException) { throw cancelled }
        catch(error: Throwable) { modelStatus="GLB failed: ${error.message}" }
    }
    val assets=remember(previewBoat) { previewBoat?.mergeInto(lakeAssets,"lake-preview-v1") ?: lakeAssets }
    val water = remember(waves) { baseWater.copy(swell=baseWater.swell.map { it.copy(amplitude=it.amplitude*waves) }) }
    val audio = remember { AudioPlayer(mapOf(horn to PcmSound.tone(220f,.25f,.25f))) }
    val audioStatus by audio.status.collectAsState()
    val latestMuted by rememberUpdatedState(muted)
    DisposableEffect(Unit) { onDispose { session.close(); audio.close() } }
    SideEffect {
        session.foreground = window.isWindowFocused
        session.input.options = GamepadOptions(deadzone=deadzone)
        audio.setForeground(session.active && !muted)
    }
    LaunchedEffect(Unit) {
        // BoxWithConstraints subcomposes the controls during layout; focus only after that frame.
        withFrameNanos { }
        focus.requestFocus()
        var last: Long? = null
        var frames = 0
        var duration = 0f
        while(isActive) {
            val now = withFrameNanos { it }
            session.foreground = window.isWindowFocused
            val step = session.step(now,PlatformGamepad.poll())
            boat.advance(step.input.movement,step.deltaSeconds)
            if(GameAction.Interact in step.input.pressed && session.active && !latestMuted) audio.play(horn)
            paused = session.paused
            audio.setForeground(session.active && !latestMuted)
            seconds = step.seconds; revision++
            last?.let { duration += (now-it)/1_000_000f; frames++ }
            if(frames >= 30) { frameMs=duration/frames; frames=0; duration=0f; controller=PlatformGamepad.status().displayName }
            last = now
        }
    }
    @Suppress("UNUSED_VARIABLE") val frameRevision = revision
    val scene = lakeFrame(boat,water,seconds,session.tick,zoom,sunlight,assets,previewBoat?.model?.id)
    MaterialTheme(colors=darkColors(primary=Color(0xff82ead0),background=Color(0xff102730),surface=Color(0xff18323d))) {
        Surface(Modifier.fillMaxSize()) {
            Column {
                Row(Modifier.fillMaxWidth().padding(20.dp,12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("JOYFRAME / LAKE LAB",style=MaterialTheme.typography.h6)
                        Text("One small scene. Shared Kotlin. Real rendering.",color=Color(0xffabc6cc),fontSize=12.sp)
                    }
                    Text(if(paused) "PAUSED" else if(!window.isWindowFocused) "IN BACKGROUND" else "LIVE",color=MaterialTheme.colors.primary)
                }
                BoxWithConstraints(Modifier.weight(1f)) {
                    val wide = maxWidth >= 850.dp
                    val viewport: @Composable (Modifier) -> Unit = { modifier ->
                        Column(modifier) {
                            GameView(scene,Modifier.fillMaxWidth().weight(1f),session.active)
                            Row(Modifier.fillMaxWidth().background(Color(0xff102730)).padding(horizontal=12.dp),
                                verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                TouchStick(session.input,Modifier.size(104.dp).focusRequester(focus)
                                    .onFocusChanged { if(!it.isFocused) session.input.clear() }
                                    .gameKeys(session.input).focusable(),onEngaged={ focus.requestFocus() })
                                Column(Modifier.weight(1f)) {
                                    Text("DRAG TO SAIL",fontSize=11.sp,color=MaterialTheme.colors.primary)
                                    Text("WASD / arrows / left stick\nSpace or controller action: horn",fontSize=11.sp)
                                    Text("Boat: ${boat.x.toInt()}, ${boat.z.toInt()}",fontSize=10.sp,color=Color(0xffabc6cc))
                                }
                                Button(onClick={ if(session.active && !muted) audio.play(horn); focus.requestFocus() }) { Text("Horn") }
                            }
                        }
                    }
                    val panel: @Composable (Modifier) -> Unit = { modifier ->
                        Column(modifier.background(MaterialTheme.colors.surface).verticalScroll(rememberScrollState()).padding(18.dp),
                            verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            Text("MAKE IT YOURS",color=MaterialTheme.colors.primary,fontSize=12.sp)
                            Text("A boat, water, a few trees.",style=MaterialTheme.typography.h6)
                            Text("No objectives. Explore the scene, then change the values that drive it.",fontSize=13.sp,color=Color(0xffabc6cc))
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                Button(onClick={ session.paused=!session.paused; paused=session.paused; focus.requestFocus() }) { Text(if(paused) "Resume" else "Pause") }
                                OutlinedButton(onClick={ boat.reset(); session.reset(); seconds=0f; revision++; focus.requestFocus() }) { Text("Reset") }
                            }
                            Setting("Wave strength",waves,.2f..1.8f) { waves=it }
                            Setting("Camera distance",zoom,.75f..1.4f) { zoom=it }
                            Setting("Sunlight",sunlight,.5f..4f) { sunlight=it }
                            Setting("Stick deadzone",deadzone,0f.. .4f) { deadzone=it }
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Switch(muted,{ muted=it }); Text("Mute audio",fontSize=13.sp)
                            }
                            Divider()
                            Text("HOW IT WORKS",color=MaterialTheme.colors.primary,fontSize=12.sp)
                            Text(sampleCode,
                                fontFamily=FontFamily.Monospace,fontSize=11.sp,lineHeight=17.sp)
                            Text("Excerpt • full working source in sample/",fontSize=10.sp,color=Color(0xffabc6cc))
                            Divider()
                            Text("DIAGNOSTICS",color=MaterialTheme.colors.primary,fontSize=12.sp)
                            Text("${frameMs.toInt()} ms / UI frame (not GPU timing)\n$controller\nAudio: $audioStatus\n36 procedural trees\n$modelStatus",fontSize=11.sp,lineHeight=17.sp)
                            OutlinedButton(onClick={ focus.requestFocus() }) { Text("Focus keyboard controls") }
                        }
                    }
                    if(wide) Row { viewport(Modifier.weight(1f)); panel(Modifier.width(320.dp).fillMaxHeight()) }
                    else Column { viewport(Modifier.fillMaxWidth().weight(1f)); panel(Modifier.fillMaxWidth().height(230.dp)) }
                }
            }
        }
    }
}

@Composable
private fun Setting(label: String, value: Float, range: ClosedFloatingPointRange<Float>, change: (Float)->Unit) {
    Column {
        Text("$label  ${((value*100).roundToInt()/100f)}",fontSize=12.sp)
        Slider(value,change,valueRange=range)
    }
}
