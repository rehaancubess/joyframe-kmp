# Joyframe KMP

A Kotlin Multiplatform game toolkit with rendering, audio and controller support.

**Experimental 0.1.0-alpha01.** One library, clean subsystem packages. Extracted
from a real game, but this standalone integration is new: do not assume the
original app's device coverage transfers automatically. Not yet on Maven Central.

## What is included

- A shared scene/mesh/material API with desktop OpenGL, Android GLES3, browser
  WebGL2 and iOS Metal implementations, plus a Compose `GameView`.
- Static GLB model loading, directional shadows, transparency, stylized water,
  and matching CPU water-height/slope queries.
- First-controller detection and a documented arcade mapping, deadzones and
  best-effort rumble. No multi-controller/raw-all-buttons API yet.
- Your own PCM sounds, preloaded playback, bounded native cue queues, a single
  optional loop and a small tone generator. No spatial audio or streaming music.
- A frame clock and an asset-free playground. You own the game loop and rules.

No game assets, accounts, server, analytics, purchases or backend credentials are
included. This is a toolkit, not a complete engine or editor.

## Try the desktop playground

Requires JDK 17, an Android SDK with platform 36 installed (Gradle configures the
Android library), and a GPU supporting OpenGL 3.3 / macOS OpenGL 4.1.

```sh
export ANDROID_HOME=/path/to/android-sdk
./gradlew :sample:run
```

The playground displays a rotatable triangle. Use **Rotate**, **Play sound**,
**Pause**, or connect a controller and move its left stick. RB / the west face
button plays a cue. No external models or sound files need downloading.

See [the complete sample](sample/src/commonMain/kotlin/example/Playground.kt).

## Use it locally

```sh
./gradlew :joyframe:publishToMavenLocal
```

Then enable `mavenLocal()` in your app's repositories and add to `commonMain`:

```kotlin
implementation("io.github.rehaancubess:joyframe:0.1.0-alpha01")
```

These coordinates are a **local development build**, not a published Central
release. iOS artifacts must be built on macOS with Xcode. The browser target is
Kotlin/Wasm, not Kotlin/JS.

## The basic pieces

```kotlin
val sound = SoundId("jump")
Audio.prepare(mapOf(sound to PcmSound.tone())) // once, at startup
Audio.play(sound)

val controller = PlatformGamepad.poll() // once per frame
val x = controller?.horizontal ?: 0f

// Inside Compose, using a scene you build from your own assets:
GameView(frame, Modifier.fillMaxSize(), active = !paused)
```

Imports are under `io.github.rehaancubess.joyframe`: `audio`, `input`, `render`,
`render.gpu`, `render.gltf`, `render.math`, and `render.water`.

On Android initialize `JoyframeAndroid` with the application context before
preparing audio, and forward controller events from the Activity. Browser audio
requires a user gesture. Read [platform setup and limitations](docs/platforms.md)
before integrating. The library does not automatically pause your simulation.

## Development

```sh
./gradlew :joyframe:desktopTest :sample:compileKotlinDesktop
./gradlew :joyframe:compileDebugKotlinAndroid :joyframe:compileKotlinWasmJs
# macOS + Xcode:
./gradlew :joyframe:compileKotlinIosSimulatorArm64 :joyframe:compileKotlinIosArm64
```

[Architecture](docs/architecture.md) · [Publication checklist](docs/publishing.md)
· [Contributing](CONTRIBUTING.md) · [License](LICENSE)
