# Verification

## Alpha04 — smooth-by-default views and tilt

- **Maven Central:** released through the GitHub release workflow (signed on a macOS runner;
  12 of 12 components validated) and published on 28 September 2026. About 20 minutes later a
  separate project whose only repositories were Maven Central and Google resolved
  `io.github.rehaancubess:joyframe:0.1.0-alpha04` (desktop variant) with `--refresh-dependencies`
  and passed its 3 tests.
Local checks on 28 September 2026, macOS / JDK 17 / Kotlin 2.3.20:

- 51 library desktop tests passed (new: render sizing against the game's measured iPhone case,
  frame pacing windows and late-frame counting, tilt math, neutral/flip/wrap handling, tilt into
  drive). The library compiled for desktop, Android, Wasm and iOS simulator; the sample built for
  desktop, browser and as an Android APK.
- iOS simulator: Lake Lab ran with the capped Metal drawable in Lake and Split screen, and the new
  diagnostics read 60.0 fps, 0.0% late, worst 16.6 ms in split screen (simulator, not a device
  benchmark). The simulator stand-in pad filter was added after that run and not re-checked on screen.
- The Android render loop, 60 Hz hold and sustained-mode code are ported from the source game,
  where they were measured on a Snapdragon 730G phone. **This library build has not run on an
  Android device yet**, and tilt has not run on any device. See `device-testing.md`.

## Alpha03 — camera, split screen, controllers, weather and sound

Local checks on 28 September 2026, macOS / JDK 17 / Kotlin 2.3.20:

- 45 library desktop tests and 5 sample tests passed. New coverage: chase camera
  framing, damping, cuts and shake; fixed-step counting and caps; buoyancy against the
  swell; whirlpool pull, swirl direction and swallow; lake holes and funnel geometry
  (including the dish staying above the lake across the hole edge); split layouts
  without overlap; weather placement and near-lens fade; model bounds and turntable
  framing; controller seats across disconnects; two-player key bindings and trigger
  throttle; spatial pan and fade; audio pan, loop volume and coalesced state syncs.
- Built: Android sample APK, browser distribution, iOS simulator framework **linked**,
  iOS device sample compiled. Library compiled for desktop, Android, Wasm and iOS.
- Staged 0.1.0-alpha03 Maven artifacts; the independent `consumer-check` (2 tests,
  including the new APIs) passed, and the sample compiled for desktop and browser
  against the staged artifact.
- Offscreen macOS renders (`:sample:captureDemo`) were inspected: overview, chase,
  split screen, snow, rain, sand, a whirlpool close-up and the hangar. Two defects
  found this way were fixed: the lake's hole edge showed through the whirlpool dish,
  and weather particles filled the view near the lens.
- Browser (built-in Chromium): the page loads; overview renders; the C key switches
  to the chase camera; Split screen shows two panes; Hangar shows the boat on its turntable.
  Held-key driving could not be exercised by the test tool (it sends instant taps);
  boat handling is covered by unit tests and the rendered animation instead.

- iOS simulator (iPhone 17 Pro, iOS 26), through `tools/ios-simulator/run.sh`: Lake Lab
  launched and rendered with Metal; touch-stick sailing, the chase camera, **split screen
  (the new single-pass Metal path) at runtime**, and the Hangar all worked, and a local
  GLB loaded from bundled Compose resources. This found that the UI ignored safe areas
  (header under the Dynamic Island); fixed with `WindowInsets.safeDrawing`, which
  Android's enforced edge-to-edge needs too.
- `:sample:windowsJar` was checked to contain the Windows x64 Skia, LWJGL and GLFW
  natives, and the same jar ran on macOS (window opened, audio ready, no errors).
  It has **not** been run on Windows.

Not verified: any physical phone, tablet or controller; Windows or Linux at all; two
real controllers at once; audible output, including pan and music; Android split screen
at runtime; performance with weather on mobile GPUs. See `device-testing.md`.

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
