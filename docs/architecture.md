# Architecture

One `joyframe` artifact contains independently organized subsystems. Audio/input
do not import rendering. Renderer backends consume `GpuSceneFrame`, not app state.
There is deliberately no dependency on the private game repository.

`commonMain` contains scene values, math, static GLB parsing, water, public input
and audio APIs. `glMain` contains the GL backend shared by desktop, Android and
Wasm. iOS has a Metal backend. `GameView` is a small platform-specific Compose
host; callers own simulation updates and lifecycle state.

Keep `GpuSceneAssets.cacheKey` stable across frames. Change it whenever meshes,
materials or textures change. The backend caches GPU uploads by this key.
Treat lists, maps and pixel buffers as read-only after constructing a frame:
Kotlin's read-only collection interfaces are not deep immutability guarantees.

Scene units are arbitrary, but the inherited lighting/shadow/water defaults use
an arcade-scale scene (objects roughly 100 units wide). This alpha has not yet
generalized every artistic default into a setting. `InstanceKind.Dynamic` skips
ordinary frustum culling, `Effect` skips shadow casting, and `Static` uses culling.

Materials include stylized water/foam/bubble effects. These are optional flags,
not gameplay rules. No arena builder, combat, boats, networking or player state
is part of the public toolkit.

## Scope of this first extraction

The original backends are retained, but the standalone view hosts are new and
smaller. The original app's HUD, native overlay workarounds, diagnostics UI and
simulation-clock integration are not copied wholesale. They require separate
generic APIs and hardware validation before claiming equivalent integration.

Next priorities: hardware smoke tests on every target; input lifecycle/device
disconnect hardening; full configurable raw controller state; audio readiness,
failure and resource-release APIs; a safer cross-platform Compose overlay path;
and an approachable scene builder with portable defaults. The macOS viewport
uses extracted offscreen CGL rendering to avoid heavyweight view origin problems;
its readback cost is documented. API stability is not
promised before 1.0.
