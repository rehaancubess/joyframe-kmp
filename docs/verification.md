# Verification of the initial extraction

Local checks on 22 September 2026, macOS / JDK 17 / Kotlin 2.3.20:

- **18 desktop-executed tests passed**, no skipped or failing tests: frame timing,
  input mapping, PCM encoding, scene validation, water derivatives, depth ranges,
  GLB geometry/material/texture decoding and malformed data, queued-audio
  non-blocking calls, stop invalidation and foreground/loop state.
- Desktop sample Kotlin compilation passed.
- Android Debug, Kotlin/Wasm, iOS simulator ARM64 and iOS device ARM64 compilation
  passed. All target publications, including iOS x64, generated locally.
- Maven POMs, source artifacts and platform artifacts generated under ignored
  `build/staging`. No Central upload or signing was performed by these checks.
- The packaged macOS playground was visually tested: scene and Compose controls
  are both visible, rotation updates the geometry, and pause/resume updates the
  UI and rendering. The sound button was exercised without an observed crash.
  Runtime startup reached the macOS OpenAL mixer. Audibility and physical
  controller behaviour have not been verified for this extraction.

The unit tests do not render through a GPU; the separate macOS smoke test does.
Neither proves visual correctness or performance on a phone or browser. New view hosts still
need physical-device background/resume, disposal, resize and overlay testing.

Known build warnings: Kotlin expect/actual classes remain beta; native metadata
resolution reports duplicate Compose/AndroidX unique names. They did not fail
compilation, but full native app linking is a separate check not covered above.

Local macOS packaging used the installed Homebrew JDK with
`-Pcompose.desktop.packaging.checkJdkVendor=false`. LWJGL/JNA require the
`jdk.unsupported` module in the packaged runtime; the sample declares it.

Before a stable release, run the independent sample on Android, iOS, macOS,
Windows/Linux and browsers with real controllers, and record exact versions and
outcomes here. Do not substitute the original private app's results.
