# Changelog

## 0.1.0-alpha03 — the game's feel, couch play and sound (local, not yet released)

- `ChaseCamera`: the source game's damped chase framing, speed-widened lens, cuts and shake.
- `FixedTimestep` for constant-rate physics with interpolated drawing.
- `Buoyancy.pose` (hull rides the drawn swell) and `Whirlpool` pull/swallow physics.
- Whirlpool funnel dishes (`Primitives.whirlpool`, `whirlpoolMaterial`) and lake holes (`WaterHole`).
- `Weather`: camera-following snow, rain and blowing sand, with matching fog.
- `SplitGameView` and `SplitLayout`: up to four panes in one GPU surface on every target.
- `PlatformGamepad.pollAll()`, `GamepadSeats`, full raw buttons/triggers/right stick.
- `KeyBindings` (two players on one keyboard) and `ActionFrame.drive` (steer + throttle incl. triggers).
- Stereo pan on `AudioPlayer.play`, `SpatialMix`/`GpuCamera.hear`, loop volume, and `MusicPlayer` with fades.
- `ModelTurntable`, `turntableCamera`, `modelBounds`, `Primitives.octahedron`.
- `OffscreenRenderer` (desktop macOS) for screenshots and GIFs.
- Lake Lab: Lake / Split screen / Hangar modes, chase toggle, whirlpools, weather, bell buoy,
  generated music, and `:sample:captureDemo`. GitHub Pages workflow for the browser build.
- Behaviour changes: D-pad now moves `ActionFrame.movement`; browser raw `leftStickX` no longer
  includes the D-pad; `AudioBackend.play` gained a pan argument (internal).

## 0.1.0-alpha02 — Lake Lab and consumer APIs (local, not yet released)

- Procedural lake/boat/tree showcase with live settings, code panel and diagnostics.
- Scene builders, mesh primitives, shared input actions, touch stick and GameSession.
- Uniform positive-up raw controller Y (behavior change on Android/browser/GLFW).
- Owned AudioPlayer, readiness/failure/closed states, PCM16 WAV input and cleanup.
- Android sample host, browser executable, iOS framework entry and independent Maven consumer.
- Additional API/lifecycle/audio/scene regression tests. See verification.md for actual runtime coverage.

## 0.1.0-alpha01 — initial extraction (not yet a Central release)

- Shared scene, material, mesh, GLB and water APIs; OpenGL/GLES/WebGL2 and Metal backends.
- Compose game viewport, including offscreen CGL compositing on macOS.
- First-controller arcade mapping and configurable deadzones/sensitivity.
- User-defined PCM sound bank, native bounded cue queues and optional loop.
- Standalone asset-free desktop playground, 18 tests, platform compile CI,
  source-disclosure guard and local/Central publishing configuration.

Experimental: see the platform guide for mapping/rumble limitations, audio
bank lifetime, browser context loss and viewport-overlay restrictions.
