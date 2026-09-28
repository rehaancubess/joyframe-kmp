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
