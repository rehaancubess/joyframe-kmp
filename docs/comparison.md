# How Joyframe compares

Joyframe is small and young. It is the right choice for some games and the wrong one for others.
This page tries to be fair about both. Facts about other projects are as of September 2026; check
each project for its current status.

| | Joyframe | KorGE | Kool | libGDX | Unity / Godot |
| --- | --- | --- | --- | --- | --- |
| Language | Kotlin Multiplatform | Kotlin Multiplatform | Kotlin Multiplatform | Java, Kotlin on JVM | C# (Unity); GDScript or C# (Godot) |
| Android | Yes (GLES3) | Yes | Yes (GLES3) | Yes | Yes |
| iOS | Yes (Metal) | Yes | No | Yes (RoboVM/MobiVM) | Yes |
| Desktop | Yes (OpenGL; macOS offscreen) | Yes | Yes (Vulkan, OpenGL) | Yes (LWJGL3) | Yes |
| Browser | Yes (Kotlin/Wasm, WebGL2) | Yes (JS, Wasm) | Yes (WebGPU, WebGL2) | GWT is Java-only; Kotlin via community TeaVM backend | Yes (engine web export) |
| 3D | Yes, stylized: water, shadows, static GLB | 2D-first; 3D is an experimental preview | Yes, advanced: PBR, SSR, AO, skinning | Yes | Yes, full |
| UI | Your app's Compose Multiplatform UI, beside the 3D view | Own UI | Own Compose-inspired UI | Scene2D | Engine UI |
| Physics | Game-feel helpers only | Box2D (2D) | PhysX 3D, Box2D 2D | Box2D, Bullet extensions | Built in |
| Editor | None: everything is code | None required | None | None required | Full editor |
| Maturity | Alpha, one shipped game | Mature | Active, pre-1.0 | Very mature | Industry standard |

## Where Joyframe is different

- **It lives inside a Compose Multiplatform app.** Your menus, HUD, settings, store and account
  screens stay ordinary Compose; the game is a `GameView` composable next to them. The others bring
  their own UI toolkit and own the whole window.
- **One `commonMain` reaches iOS Metal, Android, desktop and the browser.** Among the Kotlin options
  above, that full set with 3D is the gap Joyframe fills: Kool has no iOS, KorGE's 3D is a preview,
  and libGDX is not Kotlin Multiplatform.
- **It ships the fixes, not just the renderer.** Asynchronous audio, a decoupled Android render
  thread, phone resolution caps, controller seats, split screen, tilt, and frame-pacing numbers
  came out of a real game's device measurements ([why.md](why.md)).

## When to pick something else

- You need a physics engine, skeletal animation, PBR materials or post-processing: **Kool** (if you
  do not need iOS), or **Unity / Godot**.
- You are making a 2D game: **KorGE** or **libGDX** have years of 2D tooling Joyframe does not.
- You want a visual editor, an asset store and a large community: **Unity** or **Godot**.
- You need production maturity today: Joyframe is an alpha with limited hardware coverage
  ([verification.md](verification.md)).

Projects: [KorGE](https://github.com/korlibs/korge) ·
[Kool](https://github.com/fabmax/kool) · [libGDX](https://libgdx.com) ·
[libGDX with Kotlin](https://libgdx.com/wiki/jvm-langs/using-libgdx-with-kotlin) ·
[gdx-teavm](https://github.com/xpenatan/gdx-teavm) ·
[KorGE 3D](https://korlibs.soywiz.com/korge/reference/3d/)
