# Why Joyframe exists

We built an arcade boat-combat game in Kotlin Multiplatform: one codebase for iPhone, Android,
Mac, Windows and the browser, with online matches and couch play. Sharing the code was the easy
part. Making it run **smoothly** on every one of those platforms was not, and most of the time went
into problems that had nothing to do with our game's rules.

Joyframe is those fixes, pulled out so the next game does not have to find them again. Every number
below was measured on real devices in our game's release builds; the write-ups are dated and kept.

## Sound was making the game stutter

On an iPhone 15 Plus the game felt rough, but only sometimes. We ran the same scripted 100-second
match three times: sound on, muted, sound on again.

| | Sound on | Muted | Sound on again |
|---|---:|---:|---:|
| Frames per second | 53.5 | 59.5 | 52.9 |
| Frames later than 25 ms | 8.8% | 1.0% | 9.6% |
| Game update, 95th percentile | 21.5 ms | 4.1 ms | 22.5 ms |
| Worst frame | 100 ms | 50 ms | 133 ms |

Muting fixed it without touching resolution, boat count or graphics. The audio code was updating
native players (volume, rate, playback state) from the game loop, every frame, on the main thread.

The fix was structural: **one background consumer owns every native audio call.** The game only
drops small requests into a bounded queue and never waits. Sounds older than 100 ms are dropped
instead of played late in a burst. Loops start and stop on state changes, not per frame, and mute or
pause invalidates anything still queued. After the change, sound on and sound off measured the same
(58.8 vs 59.1 fps, game update p95 3.44 vs 3.46 ms).

In Joyframe that design *is* `AudioPlayer` on Android, iOS and desktop; browsers use Web Audio,
which does not block. Calls return immediately even when the mixer is blocked (there is a test for
exactly that), and fades step at 20 Hz with inaudible changes skipped, so a volume ramp cannot turn
back into a per-frame native call.

## Android said 60 fps while the game ran at 39

On a Snapdragon 730G phone (Adreno 618, 1080x2400) the game ran at 39-60 fps with single frames as
long as **350 ms**, and the system reported nothing wrong: the GL thread kept re-presenting the last
frame at a perfect 60 while the game state was stuck. Four changes, in the order they mattered:

1. **Unchain the render thread.** Building frames on the UI side and drawing them were one serial
   path. Each fitted in about half a frame on its own; together they missed vsync. Letting the GL
   thread run on its own and draw the newest published frame let both fit.
2. **Render at 0.8 scale.** The scene shaded 2.6 million pixels a frame and kept the GPU ~90% busy.
   At 0.8, with the compositor upscaling, it dropped to ~70% - and on a 420 dpi panel in motion the
   difference is close to invisible.
3. **Four shadow taps instead of nine.** The cost scaled with screen pixels, not shadow-map size.
4. **Sustained performance mode.** The governor boosted past what the phone could hold and every
   fallback landed a late frame.

Result: 47 of 50 two-second windows at 60.05 fps, worst frame 17 ms. We later also pinned 120 Hz
panels to their 60 Hz mode: running everything twice as often made phones heat, throttle and
present on uneven 8/17/25 ms intervals.

Joyframe's Android `GameView` does all of this by default (`GameViewOptions`). `FramePacing` reports
the numbers above - fps, late frames, p95/p99, worst - from the loop that advances your game, because
the display alone can hide the problem.

## The iPhone was shading pixels nobody could see

An iPhone 15 Plus renders 2796x1290 natively. Capping only the 3D drawable at a 1920-pixel long
edge (1920x885) removed about 53% of the shaded pixels. The HUD and touch coordinates stay at native
resolution, so text stays sharp. Joyframe applies the same cap on phones and in the browser.

## Couch play, everywhere

The game is at its best with friends on one screen, so it grew split screen, controllers, phones
as controllers and tilt steering. Each of those behaves differently on every platform.

- **One surface, several viewports.** Separate views per player meant separate GPU contexts and
  uploads. Joyframe's `SplitGameView` draws up to four panes into one surface: scissored viewports on
  OpenGL/WebGL, and on Metal a single pass with a shadow map per pane (a Metal clear wipes the
  whole attachment, so per-pane passes do not work). Two players split along the long edge so each
  keeps a near-square view.
- **Controllers on four input stacks.** GLFW on Windows/Linux (on its own thread), Apple's
  GameController (through JNA on macOS, because GLFW needs the main thread AppKit already owns),
  Android input events forwarded from the Activity and kept per device, and the browser Gamepad
  API, where a pad is invisible until a button is pressed. Each names buttons and signs axes its
  own way. Joyframe reads them all into one `GamepadState` by button position, and `GamepadSeats`
  keeps each player in their seat when someone else's controller drops out.
- **Phones as controllers.** In the game, anyone can scan a QR code on the big screen and use their
  phone as a pad, in the browser with nothing to install or in the app's controller mode. Input goes
  over a direct WebRTC data channel on the local network, falling back to a relay. This part is
  not in Joyframe yet: it needs a relay server someone has to host.
- **Tilt steering.** Rolling the phone like a wheel reads gravity rather than the gyroscope, because
  the player holds an angle, and gravity gives it directly with no drift. It is zeroed wherever the
  phone is held, since nobody holds a phone level, and it survives any pitch, from flat on a table
  to upright in bed. Joyframe's `DeviceTilt` is that code, for Android, iOS and phone browsers.

## Small things that each cost a day

- In the browser, mounting Compose on `document.body` hides the WebGL canvas behind its shadow root.
  Joyframe documents the dedicated-`div` setup and its sample uses it.
- On macOS an embedded GL view drifted out of position inside Compose layouts. Joyframe renders
  into an offscreen context and composites the image, trading a readback for correct layout.
- A fixed-step simulation makes the camera's target jump; a camera snapped to it jitters.
  `ChaseCamera` damps toward it in render time and cuts (never sweeps) after a respawn.
- The whirlpool the player sees and the one that pulls the boat were once two definitions that
  disagreed. In Joyframe the drawn funnel and `Whirlpool`'s pull share one `clockwise` flag.

## It was never really about boats

Look back at the list: audio blocking the game loop, an Android render path that hides dropped
frames, too many pixels on phones, a camera fighting a fixed-step simulation, split screen, four
controller APIs, tilt, browser and Mac view quirks. Only the whirlpool had anything to do with
water. Those are the problems of shipping *any* real-time 3D game on every platform from one
codebase, and they are the ones that took us longest, because each shows up on only one platform
and only on a real device. Joyframe exists so a racer, a brawler or a marble game starts where
our boat game ended up.

## What Joyframe is not

It is not an engine or an editor. You own the game loop and the rules; Joyframe gives you a
renderer, water, cameras, input and audio that behave the same on every platform, plus the
measurements to prove it on your own devices.
