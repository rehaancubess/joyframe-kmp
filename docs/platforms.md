# Platform setup and current limits

| Platform | Rendering | Controller | Audio |
| --- | --- | --- | --- |
| Desktop JVM | OpenGL 3.3 AWT; macOS 4.1 Core offscreen | GLFW on Windows/Linux; GameController via JNA on macOS | Java Sound; macOS system OpenAL |
| Android API 26+ | GLES3 surface | Forward Activity key/motion events | SoundPool and an optional MediaPlayer loop |
| iOS | Metal, MTKView | First extended GameController | AVAudioPlayer voices |
| Browser Wasm | WebGL2 DOM canvas | Browser Gamepad API | Web Audio; user gesture required |

This table describes implementations, not a hardware-certification matrix.
See `verification.md` for checks actually performed on this extraction.

## Android

Call `JoyframeAndroid.initialize(applicationContext)` before `Audio.prepare`.
Forward inputs from your Activity:

```kotlin
override fun dispatchKeyEvent(event: KeyEvent): Boolean =
    PlatformGamepad.handleKeyEvent(event) || super.dispatchKeyEvent(event)

override fun onGenericMotionEvent(event: MotionEvent): Boolean =
    PlatformGamepad.handleMotionEvent(event) || super.onGenericMotionEvent(event)
```

Pass Activity lifecycle state into `GameView(active = ...)`, pause your own
simulation, and call `Audio.setForeground(false/true)` when backgrounding/resuming.
The toolkit does not install an Activity lifecycle observer automatically.

## Controls

This alpha exposes an arcade preset: left X/D-pad for horizontal, RT minus LT /
D-pad for vertical (iOS additionally falls back to left Y), RB or west face for
action, south/east for confirm/cancel, Start for pause. Raw left-stick Y retains
the platform sign convention. Do not treat these as a complete uniform raw input
API yet. Only the first controller is used. Android callers must forward events.

Rumble is best effort on Android/browser. Desktop and iOS controller rumble are
currently no-ops. Device haptics are not part of this alpha. The Windows/Linux
controller service owns GLFW initialization and runs for the process lifetime;
do not combine it with a separately managed GLFW application without integration
work. No background key capture is performed by the toolkit.

## Audio

Configure one process-wide bank. Provide `PcmSound(ShortArray(...))` containing
44.1 kHz mono PCM, or use `PcmSound.tone`. Arbitrary WAV/MP3 decoding is not a
public API yet. Native effects are preloaded asynchronously; calls before ready
are discarded. Up to four effects overlap, with two native voices per sound.
Old native cues expire after 100 ms; stop invalidates pending cues.

Call `Audio.stop()` on leaving the game. It stops playback but does not unload
the process-wide bank. Full unload/reinitialization and explicit readiness/error
reporting are planned. Browser playback requires a click/key/touch after setup.

## Rendering

`GameView` displays frames; your coroutine or simulation owns updates. iOS uses
Metal's display loop; desktop and browser use Compose frame timing; Android
requests a draw on frame publication. Pause simulation separately.

macOS uses a dedicated offscreen CGL context and pixel readback into Compose.
This fixes embedded surface positioning and permits Compose layout/clipping,
but costs a GPU-to-CPU copy per frame. The long edge is capped at 1200 pixels.
This is not a claim of zero-copy rendering or the original app's performance.

The Windows/Linux AWT surface and browser DOM canvas are native surfaces: clipping,
transforms and Compose HUD overlays are not generally supported. Put controls
beside the viewport as the sample does. Remove the view to show a covering menu.
WebGL context-loss recovery and automatic background view suspension are not
implemented in this alpha. No software rendering fallback is included.

Use `GltfLoader.loadGlbAsync(bytes, namespace)` for embedded images on every
platform. The loader supports static triangle GLBs and base-colour textures.
It does not support animation, skinning, morph targets, Draco or external buffers.
Treat input GLBs as trusted assets until parser hardening/fuzzing is completed.
