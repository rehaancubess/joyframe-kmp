# Joyframe for AI coding assistants

Read this before writing game code with Joyframe, or before changing Joyframe itself.
Working snippets for every feature are in `docs/quickstart.md`.

## Build and verify

- JDK 17. Gradle configures an Android library, so `ANDROID_HOME` must point at an SDK with platform 36.
- Tests: `./gradlew :joyframe:desktopTest :sample:desktopTest`.
- Look at your change: `./gradlew :sample:captureDemo` (macOS) renders stills to
  `sample/build/capture/`. In your own game, render a frame with `OffscreenRenderer` and inspect the PNG.
- Judge smoothness with `FramePacing` numbers from a release build, never from a debug build.

## Shape of a Joyframe game

- Your code owns the loop: `GameSession.step(frameNanos, pad)` once per frame, gameplay in
  `repeat(fixed.advance(step.deltaSeconds)) { ... }` with a `FixedTimestep`.
- `GameView` / `SplitGameView` only display a `GpuSceneFrame`. Pausing the view never pauses gameplay.
- Build `GpuSceneAssets` once (`remember`) with a stable `cacheKey`. Change the key only when meshes,
  materials or textures change; the backend re-uploads on every new key.
- Build a new frame every frame with `assets.frame(camera, seconds) { instance(...) }`. Frames are data.

## Conventions

- Y is up. A vehicle model's bow faces +X and turns with `Transform3D.rotation.y`; its forward is
  `ChaseCamera.forwardFromYaw(yaw)` = (cos yaw, 0, -sin yaw). Its right side is +Z at yaw 0.
- Stick and movement Y is positive up/forward on every platform.
- Defaults assume vehicles 60-80 units long; use `ChaseCameraStyle().scaled(k)` and the `scale` of
  `weather(...)` for other sizes.
- Water height/slope queries must use the same energy value as the water mesh (`Buoyancy.pose` takes
  an `energy` function for this).

## Do not

- Do not create an `AudioPlayer` per sound or per frame. Create one per scene, `close()` it on dispose.
- Do not put Compose content over a `GameView` on Android, desktop Windows/Linux or the browser; those
  are native surfaces. Put controls beside the view.
- Do not mount `ComposeViewport` on `document.body` in the browser; use a dedicated div.
- Do not share one `ChaseCamera` between split-screen panes. Call `cut()` after respawns and resets.
- Do not give split-screen panes different `GpuSceneAssets`; they should share one.
- Do not read `DeviceTilt` without checking `available`; start it from a tap for browser permission.
- Do not add game rules, private assets, credentials or absolute paths to this repository;
  `node tools/audit-public-source.mjs` checks for the last three.

## When changing Joyframe itself

- Keep water math identical in Kotlin (`WaterConfig.heightAt/slopeAt`), GLSL and Metal shaders.
- Audio: native calls happen only on the `QueuedSoundEffects` worker; callers must never block.
- Anything in `commonMain` must compile for desktop, Android, Wasm and iOS:
  `./gradlew :joyframe:compileKotlinDesktop :joyframe:compileDebugKotlinAndroid :joyframe:compileKotlinWasmJs :joyframe:compileKotlinIosSimulatorArm64`.
- Record what was actually verified, on what hardware, in `docs/verification.md`.
