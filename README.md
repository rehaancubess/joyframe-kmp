# Joyframe KMP

A Kotlin Multiplatform game toolkit with rendering, audio and controller support.

**Experimental 0.1.0-alpha02.** One library, clean subsystem packages. Extracted
from a real game, but this standalone integration is new: do not assume the
original app's device coverage transfers automatically. Not yet on Maven Central.

## What is included

- A shared scene/mesh/material API with desktop OpenGL, Android GLES3, browser
  WebGL2 and iOS Metal implementations, plus a Compose `GameView`.
- Static GLB model loading, directional shadows, transparency, stylized water,
  and matching CPU water-height/slope queries.
- First-controller detection and a documented arcade mapping, deadzones and
  best-effort rumble. No multi-controller/raw-all-buttons API yet.
- Scene builders and procedural boxes, cones and water grids alongside the low-level API.
- Shared keyboard/touch/controller actions and a pause-safe `GameSession`.
- Scene-owned `AudioPlayer` banks with readiness/failure state, PCM16 WAV loading,
  bounded cue queues and resource disposal. No spatial audio or streaming music.
- Lake Lab: an asset-free boat scene with editable water, camera and lighting.
  You own the game loop and rules; no private app assets are required.

No game assets, accounts, server, analytics, purchases or backend credentials are
included. This is a toolkit, not a complete engine or editor.

## Try the desktop playground

Requires JDK 17, an Android SDK with platform 36 installed (Gradle configures the
Android library), and a GPU supporting OpenGL 3.3 / macOS OpenGL 4.1.

```sh
export ANDROID_HOME=/path/to/android-sdk
./gradlew :sample:run
```

Lake Lab contains square water, 36 border trees and a movable boat. Drag the
stick, use WASD/arrows, or connect a controller and move its left stick. Space,
RB/west or **Horn** plays a generated cue. Pause/reset, mute, and adjust wave
strength, camera distance, sunlight and controller deadzone. The code panel
explains the setup; diagnostics report UI timing (not GPU frame time) and audio state.

Browser: `./gradlew :sample:wasmJsBrowserDevelopmentRun`.
Android: `./gradlew :sample-android:assembleDebug`.
See [sample hosts and test checklist](docs/showcase.md), including the iOS framework entry point.

See [the complete sample](sample/src/commonMain/kotlin/example/Playground.kt).

## Use it locally

```sh
./gradlew :joyframe:publishToMavenLocal
```

Then enable `mavenLocal()` in your app's repositories and add to `commonMain`:

```kotlin
implementation("io.github.rehaancubess:joyframe:0.1.0-alpha02")
```

These coordinates are a **local development build**, not a published Central
release. iOS artifacts must be built on macOS with Xcode. The browser target is
Kotlin/Wasm, not Kotlin/JS.

## The basic pieces

```kotlin
val sound = SoundId("jump")
val audio = AudioPlayer(mapOf(sound to PcmSound.tone()))
// audio.status: Loading / AwaitingGesture / Ready / Failed / Closed
audio.play(sound)

val session = GameSession()
val step = session.step(frameNanos, PlatformGamepad.poll())
val movement = step.input.movement // normalized XY; positive Y = forward

// Inside Compose, using a scene you build from your own assets:
GameView(frame, Modifier.fillMaxSize(), active = !paused)
// When leaving the scene:
audio.close()
session.close()
```

Imports are under `io.github.rehaancubess.joyframe`: `audio`, `input`, `render`,
`render.gpu`, `render.gltf`, `render.math`, and `render.water`.

On Android initialize `JoyframeAndroid` with the application context before
preparing audio, and forward controller events from the Activity. Browser audio
requires a user gesture. Read [platform setup and limitations](docs/platforms.md)
before integrating. Feed host foreground state into `session.foreground`, and
pause rendering/audio with it. The library does not install an Android Activity observer.

For a complete scene-builder example, see [the quickstart](docs/quickstart.md).

## Development

```sh
./gradlew :joyframe:desktopTest :sample:compileKotlinDesktop
./gradlew :joyframe:compileDebugKotlinAndroid :joyframe:compileKotlinWasmJs
# macOS + Xcode:
./gradlew :joyframe:compileKotlinIosSimulatorArm64 :joyframe:compileKotlinIosArm64
# Package consumption without a project dependency:
./gradlew :joyframe:publishAllPublicationsToStagingRepository
./gradlew -p consumer-check test
./gradlew :sample:compileKotlinDesktop -Pjoyframe.usePublished=true
```

[Architecture](docs/architecture.md) · [Publication checklist](docs/publishing.md)
· [Contributing](CONTRIBUTING.md) · [License](LICENSE)
