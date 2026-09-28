# Platform setup and current limits

| Platform | Rendering | Controller | Audio |
| --- | --- | --- | --- |
| Desktop JVM | OpenGL 3.3 AWT; macOS 4.1 Core offscreen | GLFW on Windows/Linux; GameController via JNA on macOS | Java Sound; macOS system OpenAL |
| Android API 26+ | GLES3 surface | Forward Activity key/motion events, per device | SoundPool and an optional MediaPlayer loop |
| iOS | Metal, MTKView | Every extended GameController | AVAudioPlayer voices |
| Browser Wasm | WebGL2 DOM canvas | Browser Gamepad API | Web Audio; user gesture required |

| Feature | Desktop | Android | iOS | Browser |
| --- | --- | --- | --- | --- |
| Split screen | One surface, scissored viewports | Same | One Metal pass, a shadow map per pane | Same as desktop |
| Several controllers | Yes (GLFW / GameController) | Yes, by device id | Yes | Yes |
| Stereo pan | OpenAL position (macOS); Java Sound PAN/BALANCE if the line offers it | SoundPool left/right volume | `AVAudioPlayer.pan` | `StereoPannerNode` where available |
| Offscreen rendering | macOS only | No | No | No |

This table describes implementations, not a hardware-certification matrix.
See `verification.md` for checks actually performed on this extraction.

## Android

Call `JoyframeAndroid.initialize(applicationContext)` before constructing `AudioPlayer` (or `Audio.prepare`).
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

The arcade preset remains: left X/D-pad for horizontal, RT minus LT / D-pad for
vertical (iOS additionally falls back to left Y), RB or west face for action,
south/east for confirm/cancel, Start for pause. Raw stick Y is positive up on every
backend. `GamepadState` also reports both sticks, both triggers and every face,
shoulder, stick, D-pad, Start and Select button by position (`GamepadButton.North`
is Y / Triangle). Home/guide buttons and touchpads are not exposed.

`PlatformGamepad.poll()` reads the first controller; `pollAll()` reads every one with
an id that is stable while it stays connected. `GamepadSeats` turns that list into
player seats. `ActionInput` combines one player's keys (`KeyBindings`), touch stick
and controller into `movement`, `drive` and action edges. Android callers must forward
events; controllers are separated by `InputDevice` id. Rumble targets the first
controller only.

Rumble is best effort on Android/browser. Desktop and iOS controller rumble are
currently no-ops. Device haptics are not part of this alpha. The Windows/Linux
controller service owns GLFW initialization and runs for the process lifetime;
do not combine it with a separately managed GLFW application without integration
work. No background key capture is performed by the toolkit.

## Audio

Create a scene-owned `AudioPlayer`. Provide `PcmSound(ShortArray(...))` containing
44.1 kHz mono PCM, `PcmSound.tone`, or `PcmSound.fromWav(bytes)` for 44.1 kHz
PCM16 mono/stereo RIFF/WAVE. Other WAV formats/MP3 are not supported.
Native effects are preloaded asynchronously; calls before ready
are discarded. Up to four effects overlap, with two native voices per sound.
Old native cues expire after 100 ms; stop invalidates pending cues.

`play(id, volume, pan)` pans from -1 (left) to 1 (right); `play(id, SpatialMix)` takes
the result of `SpatialMix.of(...)` or `camera.hear(position, range)`. Browsers without
`StereoPannerNode` play centred. `setLoopVolume` changes the loop while it plays.
`MusicPlayer` is a separate channel for a looped track with fades; it is decoded
into memory, so keep it short. No streaming or compressed formats yet.

Observe `AudioPlayer.status`: Loading, AwaitingGesture (web), Ready, Failed or Closed.
Call `close()` on leaving the scene. It invalidates pending cues and releases
native clips/buffers/players, browser contexts and registered listeners. Native
cleanup is asynchronous on the audio worker. `stop()` only stops playback.
The legacy `Audio` facade can replace its bank and has `close()` too.
Browser playback requires a click/key/touch after setup; readiness is not a
guarantee that the hardware output is audible or unmuted.

## Rendering

For browsers, mount `ComposeViewport` in a dedicated full-window `div` with
`z-index: 0`, **not `document.body`**. Compose attaches a shadow root; owning the
body hides the sibling WebGL canvas and produces a blank scene. Lake Lab's HTML
and Wasm entry point demonstrate the required arrangement.

`SplitGameView(frames)` draws up to four frames into one surface. Every frame should
share one `GpuSceneAssets` (cache key); different assets per pane re-upload geometry
each pane. The two-pane split follows the long edge (side by side in landscape).

`GameView` displays frames; your coroutine or simulation owns updates. iOS uses
Metal's display loop; desktop uses Compose frame timing and browser uses native requestAnimationFrame; Android
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
