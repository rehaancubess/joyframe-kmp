# Lake Lab

A deliberately small library consumer: a square lake, procedural border trees,
two whirlpools, a bell buoy and a boat. No score, combat, networking, private models
or game-specific services. Boat handling is in the **sample**, not the library API.

Three modes:

- **Lake**: overview camera with map steering (the stick points where you go), or the
  game's chase camera with throttle and steer. Switch with the toggle, C, or controller Y.
- **Split screen**: two boats and two chase cameras in one `SplitGameView`. Player one:
  WASD + Space, the touch stick, or controller one. Player two: arrows + Enter or
  controller two. Controllers keep their seats across disconnects.
- **Hangar**: `ModelTurntable` with a paint choice and a manual turn slider.

World toggles: whirlpools (pull, swirl, swallow and respawn with a camera cut, shake
and splash), weather (snow, rain, sand), a bell buoy heard through `camera.hear`,
and a generated music loop through `MusicPlayer`. Physics runs at a fixed 60 Hz.

## Run

- Desktop: `./gradlew :sample:run` (JDK 17, Android SDK 36 configured).
- Browser: `./gradlew :sample:wasmJsBrowserDevelopmentRun`.
- Static local browser output: `./gradlew :sample:wasmJsBrowserDevelopmentExecutableDistribution`;
  serve `sample/build/dist/wasmJs/developmentExecutable/` with an HTTP server, not `file://`.
- Android: `./gradlew :sample-android:assembleDebug`; install the resulting debug APK
  on a GLES3-capable device. This is a separate sample app ID, not the private game's app.
- Windows without Gradle or an Android SDK: on any OS run `./gradlew :sample:windowsJar`,
  copy `sample/build/windows/LakeLab-windows-x64.jar` to the Windows PC, install Java 17+
  and run `java -jar LakeLab-windows-x64.jar`. The jar also runs on the machine that built it.
- iOS simulator: `tools/ios-simulator/run.sh` builds the framework and its resources,
  links a minimal Swift host (`tools/ios-simulator/LakeLabApp.swift`), installs it on the
  booted simulator and launches it. No Xcode project is needed. The Kotlin entry is
  `example.MainViewController()`, exported as `MainKt.MainViewController()` in Swift.
  Physical devices use `linkDebugFrameworkIosArm64` in your own signed app.

No app-store submission or public hosting is performed by these commands.

## Optional private-model preview

Pass `-Pjoyframe.demoBoat=/absolute/path/to/your-model.glb` to a sample build/run
to package an optional local GLB. The sample assumes a +Z bow, fits it to the
demo footprint with the public `fitTo` helper and retains its textures. Change
those parameters in `PreviewBoat.kt` for differently authored models.

The file is staged **only under ignored build outputs**. It is never copied into
library sources/artifacts or tracked sample resources. A subsequent build without
the property removes the generated preview asset. Do not upload a preview bundle
containing private or unlicensed art. Public sample source retains a procedural fallback.
Confirm redistribution rights before including an actual game asset in a public release.

Shared asset packaging follows [Compose resource custom directories](https://kotlinlang.org/docs/multiplatform/compose-multiplatform-resources-setup.html).
The lagoon uses the extracted wave shaders, shoreline energy/depth masks and
the original lagoon's optical/lighting values; camera, arena size and quality tier
still affect its appearance. It is not a pixel-identical gameplay capture.

## Capture stills and the README animation (macOS)

`./gradlew :sample:captureDemo` renders reference stills of every feature and 140
frames of a scripted drive into `sample/build/capture/`, using `OffscreenRenderer`.
Add `-Pjoyframe.demoBoat=...` to render a local model. The README GIF is those
frames through ffmpeg:

```sh
ffmpeg -framerate 20 -i sample/build/capture/frames/frame-%03d.png \
  -vf "fps=12,scale=480:-1:flags=lanczos,split[a][b];[a]palettegen=max_colors=96:stats_mode=diff[p];[b][p]paletteuse=dither=bayer:bayer_scale=5:diff_mode=rectangle" \
  -loop 0 docs/media/lake-lab.gif
```

Only rendered images under `docs/media/` are allowed by the source audit; model files are not.

## Learn by changing it

- `LakeScene.kt`: materials, whirlpool dishes and lake holes, boat and buoy models,
  weather, and the overview/chase framing.
- `Boats.kt`: fixed-step boat handling for both steering styles and the whirlpool pull.
- `LakeAudio.kt`: horns, bell, splash and the music loop, all generated in code.
- `Playground.kt`: modes, input seats, cameras, audio ownership and diagnostics.
- The live sliders modify wave amplitude, camera distance, light intensity and stick deadzone.
- Boat X/Z in the control strip makes motion/reset easy to verify.
- Diagnostics show UI frame interval, not GPU timing/FPS or performance promises.

The responsive layout moves the settings below the viewport on narrow windows.
The native surface is never covered by Compose controls. Pause is independent
of mute; losing window focus suspends simulation/audio. Controls changed while
paused may only redraw after resume on Android/iOS hosts.

## Release smoke checklist

For each real target record OS/device, library version, controller model and result:

1. Load the scene; no shader failure, clipping or blank viewport.
2. Steer by touch, keyboard where available, then real controller; axes agree.
3. Reach every bank; the boat remains in the water. Reset returns to origin.
4. Pause, release controls, resume; no jumps or stuck movement. Background/resume too.
5. Audio becomes ready (browser first needs a gesture). Horn is audible; mute silences it.
6. Close/reopen the scene repeatedly; no old audio, duplicated listeners or device leaks.
7. Resize/rotate; controls remain usable, rendering bounds stay aligned.
8. Disconnect/reconnect a controller; no stuck input. Record unsupported rumble honestly.

The alpha03 features have their own checklist in [device-testing.md](device-testing.md).

See `verification.md` for checks actually completed, not just this checklist.
