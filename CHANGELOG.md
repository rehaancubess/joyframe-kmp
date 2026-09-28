# Changelog

## 0.1.0-alpha04 — smooth by default, tilt steering

- `GameViewOptions` on `GameView`/`SplitGameView`, with the source game's measured defaults:
  0.8 render scale on Android, 1920-pixel / 2.07 MP cap on iOS and browsers, 60 Hz on high-refresh
  Android panels, sustained performance mode. `GameViewOptions.Full` renders every native pixel.
- Android `GameView` now draws on a free-running GL thread from a frame mailbox instead of being
  pumped by composition (the change that took the game from 39 to 60 fps on a mid-range phone).
- iOS sizes the Metal drawable explicitly; browsers size the WebGL buffer from the options.
- `FramePacing`/`FramePacingReport`: fps, late-frame %, p95/p99 and worst interval. Lake Lab shows it.
- `DeviceTilt`/`TiltMath` and `ActionInput.tiltSteer`: roll-to-steer from gravity on Android, iOS and
  phone browsers; Lake Lab's "Tilt to steer".
- Audio: loop-volume changes under 0.005 are skipped and `MusicPlayer` fades step at 20 Hz.
- iOS ignores the Simulator's synthetic "Gamepad" stand-in, as the source game does.
- Docs: `why.md` (the measured problems behind the library), `comparison.md`, `vibe-coding.md`,
  and `AGENTS.md` / `CLAUDE.md` for AI coding assistants.

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
