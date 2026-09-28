# Device testing for 0.1.0-alpha04

Nothing below has been run on a physical phone, tablet or controller yet. Record
the device, OS version, controller model and the result of each step in
`verification.md`. "Not tested" is a fine answer; a guess is not.

## Build and install

- Android: `./gradlew :sample-android:assembleDebug`, then
  `adb install -r sample-android/build/outputs/apk/debug/sample-android-debug.apk`.
- iOS: `./gradlew :sample:linkDebugFrameworkIosArm64` and host `MainKt.MainViewController()`
  in a signed app (see `showcase.md`).
- Browser: open the GitHub Pages demo on the phone as well as on desktop.

## Lake

1. The lake, trees, buoy and both whirlpools draw; no black or frozen viewport.
2. Drag the touch stick: the boat moves where the stick points (overview camera).
3. Turn on **Chase camera**: the camera sits behind the boat; up is throttle, left and
   right steer. Turning the camera back off returns to the overview without a jump.
4. Sail into a whirlpool: it pulls and spins the boat; in the centre the boat respawns,
   the camera cuts (no sweep), the view shakes, a splash plays and a controller rumbles
   (Android and browser only).
5. Each weather choice (snow, rain, sand) draws particles and tints the fog; frame
   time in Diagnostics stays reasonable. Note the number.
6. With headphones, the bell buoy sounds from the side it is on and fades with distance.
7. **Music** fades in and out; backgrounding the app silences it; returning resumes it.

## Split screen

8. Two panes appear (side by side in landscape, stacked in portrait), each following
   its own boat, with a thin gutter between them.
9. With two controllers: each drives its own boat. Disconnect controller one and
   reconnect it: it returns to seat one and controller two keeps seat two.
10. On a keyboard (desktop/browser): WASD + Space drives player one, arrows + Enter
    drives player two, at the same time.

## Hangar

11. The boat spins slowly; paint and turn change it; leaving and re-entering works.

## Smoothness and tilt

13. Diagnostics shows fps, late % and worst frame. Note them for Lake (clear), Lake with snow,
    and Split screen, on a release-like build where possible.
14. On a phone, turn on **Tilt to steer**: rolling right turns right, the boat drives forward
    on its own, and Recenter takes a new neutral. In a browser on iPhone, a permission prompt appears.

## Every mode

12. Pause and resume, rotate the device, background and return: no stuck input, no
    duplicated sound, no crash. Close and reopen the app several times.
