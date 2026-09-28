# Changelog

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
