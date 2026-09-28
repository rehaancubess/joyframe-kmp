// SPDX-License-Identifier: Apache-2.0
package example

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rehaancubess.joyframe.FixedTimestep
import io.github.rehaancubess.joyframe.GameSession
import io.github.rehaancubess.joyframe.audio.*
import io.github.rehaancubess.joyframe.input.*
import io.github.rehaancubess.joyframe.render.*
import io.github.rehaancubess.joyframe.render.gpu.GpuCamera
import io.github.rehaancubess.joyframe.render.gltf.GltfModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

enum class LabMode(val label: String) { Lake("Lake"), Split("Split screen"), Hangar("Hangar") }

private val codeFor = mapOf(
    LabMode.Lake to """
        // The game's chase camera
        val cam = chase.update(boat.position(),
            boat.forward(), boat.speed, dt)

        // Hulls ride the swell the shader draws
        Buoyancy.pose(water, x, z, yaw, t,
            energy = ::lakeEnergyAt)

        // Whirlpools pull; the dish shades
        val (dx, dz) = pool.velocityChange(x, z, dt)

        // A bell you can locate by ear
        audio.play(bell, cam.hear(buoy, 3200f))
    """.trimIndent(),
    LabMode.Split to """
        // Two players, one surface
        seats.update(PlatformGamepad.pollAll())
        val two = ActionInput(KeyBindings.Arrows)

        SplitGameView(listOf(
            frameFor(boatOne, chaseOne),
            frameFor(boatTwo, chaseTwo)))

        // Physics at a fixed 60 Hz, drawn smooth
        repeat(fixed.advance(dt)) { step() }
    """.trimIndent(),
    LabMode.Hangar to """
        // A spinning showcase, framed for you
        ModelTurntable(assets, "boat",
            secondsPerTurn = 9f,
            materials = mapOf("hull" to paint))
    """.trimIndent(),
)

@Composable
fun Playground() {
    val session = remember { GameSession(ActionInput(KeyBindings.Wasd)) }
    val second = remember { ActionInput(KeyBindings.Arrows) }
    val seats = remember { GamepadSeats(2) }
    val fixed = remember { FixedTimestep() }
    val boats = remember { listOf(BoatMotion(), BoatMotion(-260f,260f)) }
    val chases = remember { listOf(ChaseCamera(), ChaseCamera()) }
    val focus = remember { FocusRequester() }
    val window = LocalWindowInfo.current
    var mode by remember { mutableStateOf(LabMode.Lake) }
    var seconds by remember { mutableStateOf(0f) }
    var revision by remember { mutableStateOf(0L) }
    var paused by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }
    var chase by remember { mutableStateOf(false) }
    var look by remember { mutableStateOf(LakeLook()) }
    var music by remember { mutableStateOf(false) }
    var waves by remember { mutableStateOf(1f) }
    var zoom by remember { mutableStateOf(1.12f) }
    var deadzone by remember { mutableStateOf(.15f) }
    var paint by remember { mutableStateOf("hull") }
    var spin by remember { mutableStateOf(0f) }
    var controllers by remember { mutableStateOf("No controller") }
    var frameMs by remember { mutableStateOf(0f) }
    var alpha by remember { mutableStateOf(1f) }
    var cameras by remember { mutableStateOf(listOf(overviewCamera(1.12f))) }
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
    val assets=remember(previewBoat,look.whirlpools) {
        val scene=lakeAssets(look.whirlpools)
        previewBoat?.mergeInto(scene,scene.cacheKey+"-preview") ?: scene
    }
    val water = remember(waves) { baseWater.copy(swell=baseWater.swell.map { it.copy(amplitude=it.amplitude*waves) }) }
    val audio = remember { AudioPlayer(LakeSounds.bank()) }
    val soundtrack = remember { lazy { MusicPlayer(lakeMusic(),.32f) } }
    val audioStatus by audio.status.collectAsState()
    val latest by rememberUpdatedState(Triple(mode,chase,look))
    val latestMuted by rememberUpdatedState(muted)
    val latestZoom by rememberUpdatedState(zoom)
    DisposableEffect(Unit) { onDispose {
        session.close(); audio.close(); if(soundtrack.isInitialized()) soundtrack.value.close()
    } }
    SideEffect {
        session.foreground = window.isWindowFocused
        session.input.options = GamepadOptions(deadzone=deadzone)
        second.options = GamepadOptions(deadzone=deadzone)
        audio.setForeground(session.active && !muted)
        if(soundtrack.isInitialized()) soundtrack.value.setForeground(session.active && !muted)
    }
    LaunchedEffect(music) {
        if(music) soundtrack.value.play(fadeInSeconds=1.5f)
        else if(soundtrack.isInitialized()) soundtrack.value.pause(fadeOutSeconds=.8f)
    }
    fun toggleCamera() { chase=!chase; chases.forEach { it.cut() } }
    LaunchedEffect(Unit) {
        // BoxWithConstraints subcomposes the controls during layout; focus only after that frame.
        withFrameNanos { }
        focus.requestFocus()
        var last: Long? = null
        var frames = 0
        var duration = 0f
        var northWasDown = false
        var nextBell = 2f
        while(isActive) {
            val now = withFrameNanos { it }
            val (currentMode, chasing, currentLook) = latest
            val split = currentMode == LabMode.Split
            session.foreground = window.isWindowFocused
            seats.update(PlatformGamepad.pollAll())
            val step = session.step(now,seats.state(0))
            val two = second.poll(seats.state(1))
            if(split && GameAction.Pause in two.pressed) session.paused = !session.paused
            val north = seats.state(0)?.isPressed(GamepadButton.North) == true
            if(north && !northWasDown && currentMode == LabMode.Lake) { chase=!chase; chases.forEach { it.cut() } }
            northWasDown = north
            val steering = if(split || chasing) Steering.Drive else Steering.Map
            fun controls(frame: ActionFrame) = if(steering == Steering.Drive) frame.drive else frame.movement
            // One player: the arrow keys and the second controller also steer boat one.
            val one = if(split) controls(step.input) else controls(step.input).plus(controls(two))
            val pools = if(currentLook.whirlpools) lakePools else emptyList()
            repeat(fixed.advance(step.deltaSeconds)) {
                val swallowed = listOf(boats[0].step(one,steering,fixed.stepSeconds,pools)) +
                    if(split) listOf(boats[1].step(controls(two),Steering.Drive,fixed.stepSeconds,pools).also {
                        boats[0].separateFrom(boats[1])
                    }) else emptyList()
                swallowed.forEachIndexed { i, gone -> if(gone) {
                    chases[i].cut(); chases[i].shake(30f)
                    if(!latestMuted) audio.play(LakeSounds.splash,.9f)
                    PlatformGamepad.rumble(260,.8f)
                } }
            }
            alpha = fixed.alpha
            if(session.active && !latestMuted) {
                if(GameAction.Interact in step.input.pressed || (!split && GameAction.Interact in two.pressed)) audio.play(LakeSounds.hornOne)
                if(split && GameAction.Interact in two.pressed) audio.play(LakeSounds.hornTwo)
            }
            val style = lakeChaseStyle(latestZoom)
            cameras = List(if(split) 2 else 1) { i ->
                if(split || chasing) chases[i].also { it.style = style }
                    .update(boats[i].position(alpha),boats[i].forward(alpha),boats[i].speed,step.deltaSeconds)
                else overviewCamera(latestZoom)
            }
            if(currentLook.buoy && currentMode != LabMode.Hangar && step.seconds >= nextBell) {
                nextBell = step.seconds + 4f
                if(session.active && !latestMuted) audio.play(LakeSounds.bell,cameras[0].hear(buoyPosition,3200f),.8f)
            }
            paused = session.paused
            audio.setForeground(session.active && !latestMuted)
            seconds = step.seconds; revision++
            last?.let { duration += (now-it)/1_000_000f; frames++ }
            if(frames >= 30) {
                frameMs=duration/frames; frames=0; duration=0f
                val pads = PlatformGamepad.pollAll()
                controllers = when(pads.size) { 0 -> "No controller"; 1 -> pads[0].name; else -> "${pads.size} controllers: ${pads.joinToString { it.name }}" }
            }
            last = now
        }
    }
    @Suppress("UNUSED_VARIABLE") val frameRevision = revision
    val importedId = previewBoat?.model?.id
    val split = mode == LabMode.Split
    val visibleBoats = if(split) boats else boats.take(1)
    val frames = cameras.take(visibleBoats.size).mapIndexed { i, camera ->
        lakeFrame(assets,camera,visibleBoats,water,seconds,session.tick,look,alpha,importedId,viewer=visibleBoats[i])
    }
    MaterialTheme(colors=darkColors(primary=Color(0xff82ead0),background=Color(0xff102730),surface=Color(0xff18323d))) {
        Surface(Modifier.fillMaxSize()) {
            // Phones draw edge to edge: keep the UI clear of status bars, notches and gesture areas.
            Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(Modifier.fillMaxWidth().padding(20.dp,12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("JOYFRAME / LAKE LAB",style=MaterialTheme.typography.h6)
                        Text("One small scene. Shared Kotlin. Real rendering.",color=Color(0xffabc6cc),fontSize=12.sp)
                    }
                    Text(if(paused) "PAUSED" else if(!window.isWindowFocused) "IN BACKGROUND" else "LIVE",color=MaterialTheme.colors.primary)
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=16.dp),
                    horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    LabMode.entries.forEach { entry ->
                        Choice(entry.label,mode==entry) { mode=entry; chases.forEach { it.cut() }; focus.requestFocus() }
                    }
                }
                BoxWithConstraints(Modifier.weight(1f)) {
                    val wide = maxWidth >= 850.dp
                    val viewport: @Composable (Modifier) -> Unit = { modifier ->
                        Column(modifier) {
                            val sceneModifier = Modifier.fillMaxWidth().weight(1f)
                            when {
                                mode == LabMode.Hangar -> ModelTurntable(assets,importedId ?: "boat",sceneModifier,session.active,
                                    extraYawRadians=spin,materials=if(importedId==null) mapOf("hull" to paint) else emptyMap())
                                frames.size > 1 -> SplitGameView(frames,sceneModifier,session.active)
                                else -> GameView(frames.first(),sceneModifier,session.active)
                            }
                            Row(Modifier.fillMaxWidth().background(Color(0xff102730)).padding(horizontal=12.dp),
                                verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                TouchStick(session.input,Modifier.size(104.dp).focusRequester(focus)
                                    .onFocusChanged { if(!it.isFocused) { session.input.clear(); second.clear() } }
                                    .onKeyEvent { if(it.type==KeyEventType.KeyDown && it.key==Key.C && mode==LabMode.Lake) { toggleCamera(); true } else false }
                                    .gameKeys(session.input).gameKeys(second).focusable(),onEngaged={ focus.requestFocus() })
                                Column(Modifier.weight(1f)) {
                                    Text(when { mode == LabMode.Hangar -> "HANGAR"; split -> "TWO PLAYERS"; chase -> "CHASE CAMERA"; else -> "DRAG TO SAIL" },
                                        fontSize=11.sp,color=MaterialTheme.colors.primary)
                                    Text(when {
                                        split -> "P1: WASD + Space or controller 1\nP2: arrows + Enter or controller 2"
                                        mode == LabMode.Hangar -> "Pick a paint and turn the boat"
                                        chase -> "W/S or triggers: throttle · A/D: steer\nC or controller Y: overview"
                                        else -> "WASD / arrows / left stick\nC or controller Y: chase camera"
                                    },fontSize=11.sp)
                                    Text("Boat: ${boats[0].x.toInt()}, ${boats[0].z.toInt()}",fontSize=10.sp,color=Color(0xffabc6cc))
                                }
                                Button(onClick={ if(session.active && !muted) audio.play(LakeSounds.hornOne); focus.requestFocus() }) { Text("Horn") }
                            }
                        }
                    }
                    val panel: @Composable (Modifier) -> Unit = { modifier ->
                        Column(modifier.background(MaterialTheme.colors.surface).verticalScroll(rememberScrollState()).padding(18.dp),
                            verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            Text("MAKE IT YOURS",color=MaterialTheme.colors.primary,fontSize=12.sp)
                            Text(when(mode) {
                                LabMode.Lake -> "A boat, water, a few trees."
                                LabMode.Split -> "Two boats, one screen."
                                LabMode.Hangar -> "The boat, up close."
                            },style=MaterialTheme.typography.h6)
                            Text("No objectives. Explore the scene, then change the values that drive it.",fontSize=13.sp,color=Color(0xffabc6cc))
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                Button(onClick={ session.paused=!session.paused; paused=session.paused; focus.requestFocus() }) { Text(if(paused) "Resume" else "Pause") }
                                OutlinedButton(onClick={
                                    boats.forEach { it.reset() }; chases.forEach { it.cut() }
                                    session.reset(); fixed.reset(); seconds=0f; revision++; focus.requestFocus()
                                }) { Text("Reset") }
                            }
                            if(mode == LabMode.Hangar) {
                                Text("Paint",fontSize=12.sp)
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    Choice("Coral",paint=="hull") { paint="hull" }
                                    Choice("Blue",paint=="hull-blue") { paint="hull-blue" }
                                }
                                Setting("Turn",spin,-3.14f..3.14f) { spin=it }
                            } else {
                                if(mode == LabMode.Lake) Row(verticalAlignment=Alignment.CenterVertically) {
                                    Switch(chase,{ toggleCamera() }); Text("Chase camera (as in the game)",fontSize=13.sp)
                                }
                                Row(verticalAlignment=Alignment.CenterVertically) {
                                    Switch(look.whirlpools,{ look=look.copy(whirlpools=it) }); Text("Whirlpools",fontSize=13.sp)
                                }
                                Text("Weather",fontSize=12.sp)
                                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                    Weather.entries.forEach { kind -> Choice(kind.name,look.weather==kind) { look=look.copy(weather=kind) } }
                                }
                                Row(verticalAlignment=Alignment.CenterVertically) {
                                    Switch(look.buoy,{ look=look.copy(buoy=it) }); Text("Bell buoy (positional sound)",fontSize=13.sp)
                                }
                                Setting("Wave strength",waves,.2f..1.8f) { waves=it }
                                Setting("Camera distance",zoom,.75f..1.4f) { zoom=it }
                                Setting("Sunlight",look.sunlight,.5f..4f) { look=look.copy(sunlight=it) }
                            }
                            Setting("Stick deadzone",deadzone,0f.. .4f) { deadzone=it }
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Switch(music,{ music=it }); Text("Music",fontSize=13.sp)
                            }
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Switch(muted,{ muted=it }); Text("Mute audio",fontSize=13.sp)
                            }
                            Divider()
                            Text("HOW IT WORKS",color=MaterialTheme.colors.primary,fontSize=12.sp)
                            Text(codeFor.getValue(mode),fontFamily=FontFamily.Monospace,fontSize=11.sp,lineHeight=17.sp)
                            Text("Excerpt • full working source in sample/",fontSize=10.sp,color=Color(0xffabc6cc))
                            Divider()
                            Text("DIAGNOSTICS",color=MaterialTheme.colors.primary,fontSize=12.sp)
                            Text("${frameMs.toInt()} ms / UI frame (not GPU timing)\n$controllers\nAudio: $audioStatus\n" +
                                "Physics: fixed 60 Hz, ${fixed.steps} steps\n36 procedural trees\n$modelStatus",fontSize=11.sp,lineHeight=17.sp)
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

private fun Movement.plus(other: Movement) = Movement((x+other.x).coerceIn(-1f,1f),(y+other.y).coerceIn(-1f,1f))

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    if(selected) Button(onClick=onClick,contentPadding=PaddingValues(12.dp,6.dp)) { Text(label,fontSize=12.sp) }
    else OutlinedButton(onClick=onClick,contentPadding=PaddingValues(12.dp,6.dp)) { Text(label,fontSize=12.sp) }
}

@Composable
private fun Setting(label: String, value: Float, range: ClosedFloatingPointRange<Float>, change: (Float)->Unit) {
    Column {
        Text("$label  ${((value*100).roundToInt()/100f)}",fontSize=12.sp)
        Slider(value,change,valueRange=range)
    }
}
