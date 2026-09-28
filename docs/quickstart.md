# From an empty scene to a game loop

Use the artifact from the README. The alpha is locally staged, not yet on Central.

```kotlin
import io.github.rehaancubess.joyframe.render.*
import io.github.rehaancubess.joyframe.render.gpu.*
import io.github.rehaancubess.joyframe.render.math.Vec3

val assets = sceneAssets("my-scene-v1") {
    material("water", 0xff168d9b.toInt(), water = true)
    water("surface", 2200f, "water", energy = .45f, depth = .6f)
    model("lake", ModelPart("surface"))
    material("mint", 0xff82ead0.toInt())
    box("cube", Vec3(100f, 100f, 100f), "mint")
    model("marker", ModelPart("cube"))
}
val frame = assets.frame(
    camera = GpuCamera(Vec3(0f, 1800f, 2200f), Vec3.ZERO,
        verticalFovDegrees = 48f, nearPlane = 10f, farPlane = 10000f),
    seconds = elapsedSeconds,
) {
    instance("water", "lake")
    instance("marker", "marker", Transform3D(Vec3(0f, 70f, 0f)), kind = InstanceKind.Dynamic)
}
// Inside a Composable:
GameView(frame, modifier, active = foreground && !paused)
```

Build `assets` once (for example with `remember`). Change the cache key when
geometry/materials change; animate instance transforms, not mesh uploads.
Helpers validate dimensions, subdivisions, duplicate IDs and missing references.
The full `IndexedMesh`/`GpuSceneFrame` API is still available.

Water grids encode wave energy and depth in UVs. Sample height/slope using the
**same energy** as the mesh: `water.heightAt(x, z, time, .45f)`. The sample boat
also follows `slopeAt`; this is visual buoyancy, not a physics engine.

## One input path

`ActionInput` combines keyboard, touch and raw controller-stick movement, clamps
diagonal speed, and exposes `held` and `pressed` action sets. Movement is positive
right/up. `Modifier.gameKeys(input)` belongs on a focused Compose surface;
`TouchStick(input, Modifier.size(104.dp))` supplies the same movement input.
Clear input when focus is lost. Put controls beside native viewports, not over them.

`GameSession.step(nanos, PlatformGamepad.poll())` uses capped frame deltas, freezes
simulation while paused/backgrounded, and prevents a large resume jump. It must
still be called while paused to observe controller pause-button release/press.
Call `session.close()` on disposal. It does not own a rendering or audio device.

## Owned audio

```kotlin
val horn = SoundId("horn")
val audio = AudioPlayer(mapOf(horn to PcmSound.tone(220f, .25f)))
// Or PcmSound.fromWav(bytes): 44.1 kHz PCM16 mono/stereo, mixed to mono.
val result = audio.awaitReady() // inspect Failed/Closed too; use a timeout if appropriate
audio.play(horn)
audio.setForeground(foreground && !paused && !muted)
audio.close() // idempotent; stops/releases resources, drops pending cues
```

In Compose use `remember` plus `DisposableEffect`, and observe `audio.status`
with `collectAsState`. Web readiness also waits for an actual browser gesture.
The compatibility `Audio` singleton remains available; preparing a new bank
releases its old bank. Scene-owned players are recommended.

## Fixed-step physics, drawn smoothly

```kotlin
val fixed = remember { FixedTimestep() }          // 60 Hz by default
repeat(fixed.advance(step.deltaSeconds)) { boat.tick(step.input.drive, fixed.stepSeconds) }
instance("boat", "boat", previous = boat.previous, transform = boat.current,
    alpha = fixed.alpha, kind = InstanceKind.Dynamic)
```

A stall is capped at `maxStepsPerFrame`, so a slow frame drops time instead of
spiralling. Lake Lab interpolates position and heading itself, then floats the result.

## The game's chase camera

```kotlin
val chase = remember { ChaseCamera(ChaseCameraStyle().scaled(2f)) }
val camera = chase.update(position, forward, speed01, step.deltaSeconds)
chase.shake(20f)   // a hit: gone in a quarter of a second
chase.cut()        // after a respawn or reset, so it does not sweep across the map
```

`forward` is horizontal; `ChaseCamera.forwardFromYaw(yaw)` converts a model's
`rotation.y` when the model's bow faces +X. The default style suits vehicles about
60 to 80 units long; `scaled` keeps the same framing for bigger or smaller models.
Keep one `ChaseCamera` per view. A gap over 0.2 s (a resume) cuts instead of sweeping.

## Floating hulls and whirlpools

```kotlin
val pose = Buoyancy.pose(water, x, z, yaw, seconds, lift = 20f, energy = ::energyAt)

val pool = Whirlpool(x = 400f, z = -300f, radius = 330f, coreRadius = 70f)
val (dvx, dvz) = pool.velocityChange(boatX, boatZ, dt)   // add to your velocity
if (pool.swallows(boatX, boatZ)) respawn()
```

To draw it, cut a hole in the water and drop a funnel dish on top. The dish uses the
same `clockwise` flag as the physics, so the water never turns against the pull.

```kotlin
val dish = pool.radius * 1.06f
sceneAssets("lake-v2") {
    material("water", 0xff14b4c8.toInt(), water = true)
    whirlpoolMaterial("pool", 0xff0f8ca3.toInt())
    water("lake", 2200f, "water", 80, holes = listOf(WaterHole.forWhirlpool(pool.x, pool.z, dish)))
    whirlpool("pool", dish, "pool", clockwise = pool.clockwise)
    model("pool", ModelPart("pool"))
}
// Per frame: instance("pool", "pool", Transform3D(Vec3(pool.x, 0f, pool.z)))
// and frame(..., vortex = VortexConfig(dishDepth = lakeDepthThere), vortexSurge = pool.surge(boatX, boatZ))
```

Keep the lake's grid cells under about a tenth of the dish radius so the dish covers
the hole's edge, and match `dishDepth` to the lake's depth there so the rim blends.

## Weather

```kotlin
sceneAssets("lake-v2") { /* ... */ weatherParticles() }
val look = Weather.Snow
assets.frame(camera, seconds, environment = look.environment(baseEnvironment),
    lights = listOf(DirectionalLight(sunDirection, intensity = 2f * look.sunlight))) {
    // ...your instances...
    weather(look, camera, seconds, density = 2f, scale = 1.4f)
}
```

Particles fill layers ahead of the camera and shrink away near the lens. They are
Effects: no shadows, no gameplay.

## Split screen and several controllers

```kotlin
val seats = remember { GamepadSeats(maxSeats = 2) }
val one = remember { GameSession(ActionInput(KeyBindings.Wasd)) }
val two = remember { ActionInput(KeyBindings.Arrows) }

seats.update(PlatformGamepad.pollAll())
val p1 = one.step(frameNanos, seats.state(0)).input
val p2 = two.poll(seats.state(1))

SplitGameView(listOf(frameFor(boatOne, chaseOne), frameFor(boatTwo, chaseTwo)), Modifier.fillMaxSize())
```

Chain keyboard handlers on your focused control: `.gameKeys(one.input).gameKeys(two)`.
All panes should share one `GpuSceneAssets` (one upload). Two panes split along the
long edge; three and four use quadrants. `GamepadState.buttons`, both sticks and both
triggers are available when you need more than the arcade mapping.

## Positional sound and music

```kotlin
audio.play(bell, camera.hear(bellPosition, range = 3200f))   // pan + distance fade
audio.play(horn, volume = .8f, pan = -.5f)                   // or pan directly
audio.setLoopVolume(.2f + .6f * speed01)                     // an engine that revs

val music = remember { MusicPlayer(track, volume = .35f) }
music.play(fadeInSeconds = 1.5f)
music.pause(fadeOutSeconds = .8f)
music.close()
```

Music is decoded into memory like other sounds (44.1 kHz PCM16), so keep loops short.

## A model turntable

```kotlin
ModelTurntable(assets, "boat", Modifier.size(320.dp), secondsPerTurn = 9f,
    materials = mapOf("hull" to "hull-blue"))
```

The camera frames the model's bounds (`modelBounds`, `turntableCamera`) at any yaw.

## Screenshots without a window (macOS)

```kotlin
OffscreenRenderer().use { renderer ->
    ImageIO.write(renderer.render(frame, 1280, 720), "png", File("shot.png"))
}
```

`./gradlew :sample:captureDemo` uses this to render Lake Lab's reference stills and
the frames of the README animation.
