# Verification

## Alpha02 — Lake Lab and owned APIs

Local checks on 22 September 2026, macOS / JDK 17 / Kotlin 2.3.20:

- 32 library desktop tests and 2 sample desktop tests passed. New coverage includes
  input normalization/edges, quick keyboard taps, pause/resume, model fitting,
  mesh builders, WAV validation and audio failure/close-during-preload cleanup.
- Desktop sample packaged; Android sample debug APK built; Wasm browser distribution built;
  iOS device sample compiled and iOS ARM64 simulator framework **linked**, not just compiled.
- Maven artifacts for all library targets staged locally. The standalone `consumer-check`
  passed its integration test without a project dependency (35 total tests across the
  three builds). The final desktop/browser/Android/iOS showcase builds also passed
  with `-Pjoyframe.usePublished=true`, consuming the locally staged Maven artifact.
- Native macOS preview rendered the lake, border trees and actual locally injected textured boat.
  Audio reported Ready. Its automated window session remained backgrounded; the complete
  gameplay interaction smoke test below was performed in the browser instead.
- Browser preview visually rendered the same imported boat/water. Drag steering changed
  boat coordinates; pause froze them despite further drag; reset returned to 0,0;
  resume and horn were exercised. Audio changed from AwaitingGesture to Ready.
  Keyboard P paused the game after pointer focus. Narrow layout was visibly checked.
  Audibility itself was not verified.
- Found and fixed a real browser integration error: ComposeViewport must own a dedicated
  div, not body, so its shadow root does not hide the WebGL canvas.
- Public-source audit passed. Preview GLB is opt-in and staged only under ignored build
  outputs. A build without the property was checked to remove it from the browser distribution.

Not yet verified: physical Android/iOS devices, Windows/Linux runtime, physical gamepads,
audible output, memory stability under repeated native scene recreation, browser context-loss
recovery, or an independently signed iOS sample app. No public release/upload was performed.
The original game's existing device coverage is not proof of this new host's coverage.

Known warnings remain: expect/actual beta declarations and duplicate native
Compose/AndroidX metadata names. They did not prevent the simulator framework link.

## Alpha01 — initial extraction

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
