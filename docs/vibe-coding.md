# Built for vibe coding

More and more games are written with an AI assistant doing much of the typing. That changes what a
good game library looks like. An assistant cannot click through an editor, cannot feel a stutter,
and will happily write the obvious code - which is often the slow code. Joyframe is designed around
those three facts.

## Everything is code and plain data

There is no editor and no binary scene file. A scene is Kotlin: `sceneAssets { ... }` builds the
meshes and materials, and every frame is a `GpuSceneFrame` data class built with `frame { ... }`.
An assistant can read all of it, write all of it, diff it and review it in a pull request.

## One language, one place, every platform

Game code goes in `commonMain` and runs on Android, iOS, desktop and the browser. There are no
per-platform scene files or bindings to keep in sync, so there is no second copy for an assistant
to forget to update. Platform differences stay inside the library.

## The obvious code is the fast code

The mistakes that cost our game weeks are the ones an assistant would make first, so the library
makes them hard to make:

- On Android, iOS and desktop, `AudioPlayer.play()` only queues a request; native audio runs on a
  background worker, so calling it every frame cannot stall the game the way per-frame audio calls
  stalled ours on iPhone. Browsers use Web Audio, which does not block.
- Android's `GameView` draws on its own thread, so building frames in composition cannot hold the
  display at 39 fps. Phones get the measured render scale and resolution cap by default.
- `ChaseCamera` damps and cuts correctly; `FixedTimestep` makes physics frame-rate independent;
  `GamepadSeats` handles controllers dropping out. None of it needs to be re-derived.

## It can check its own work

An assistant cannot see a phone screen, but it can read numbers and look at images:

- **Screenshots without a window.** `OffscreenRenderer` renders any frame to a PNG on macOS, and
  `./gradlew :sample:captureDemo` renders every Lake Lab feature. While building Joyframe, looking
  at these renders caught a jagged whirlpool edge and snowflakes filling the view, before any
  person ran the app.
- **Smoothness as numbers.** `FramePacing` turns "it feels laggy" into fps, late-frame percentage,
  p95/p99 and worst frame, which an assistant can compare before and after a change.
- **Tests on the JVM.** The camera, physics, tilt, split layout, controller seats and audio queue
  are plain Kotlin with unit tests that run in seconds on any machine.

## It tells the assistant the rules

[`AGENTS.md`](../AGENTS.md) at the repository root lists the conventions and pitfalls in the form
coding assistants read: axis and yaw conventions, what must be stable across frames, what never
to call per frame, and how to verify a change. Point your assistant at it before it writes a line.

## A workflow that works

1. Ask for a small scene with `sceneAssets` and `frame`, and run it on desktop.
2. Have the assistant render it offscreen and look at the PNG.
3. Add the game loop with `GameSession` and `FixedTimestep`; test the rules on the JVM.
4. Run on a phone, read `FramePacing` in a release build, and fix what the numbers say.

None of this is specific to boats or water: the same defaults and checks carry any 3D game.
Joyframe is an alpha, and it does not make an assistant a game designer. It makes the parts that
are easy to get subtly wrong - timing, audio, input, platform quirks - already right.
