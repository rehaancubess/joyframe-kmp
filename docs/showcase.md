# Lake Lab

A deliberately small library consumer: a square lake, procedural border trees,
and one boat. No score, combat, networking, private models or game-specific services.
The boat's movement is in the **sample**, not the library API.

## Run

- Desktop: `./gradlew :sample:run` (JDK 17, Android SDK 36 configured).
- Browser: `./gradlew :sample:wasmJsBrowserDevelopmentRun`.
- Static local browser output: `./gradlew :sample:wasmJsBrowserDevelopmentExecutableDistribution`;
  serve `sample/build/dist/wasmJs/developmentExecutable/` with an HTTP server, not `file://`.
- Android: `./gradlew :sample-android:assembleDebug`; install the resulting debug APK
  on a GLES3-capable device. This is a separate sample app ID, not the private game's app.
- iOS: `./gradlew :sample:linkDebugFrameworkIosSimulatorArm64` produces
  `sample/build/bin/iosSimulatorArm64/debugFramework/LakeLab.framework`.
  The Kotlin entry is `example.MainViewController()`, exported as
  `MainKt.MainViewController()` in Swift. Add the framework to a simulator host
  and display that controller. An Xcode app/signing project is not bundled yet.
  Device builds use `linkDebugFrameworkIosArm64` and require a device host/signing setup.

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

## Learn by changing it

- `LakeScene.kt`: scene materials, mesh helpers, boat model, trees and movement.
- `Playground.kt`: Compose controls, action mapping, session/audio ownership and diagnostics.
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

See `verification.md` for checks actually completed, not just this checklist.
