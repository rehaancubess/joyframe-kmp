package io.github.rehaancubess.joyframe

import androidx.compose.ui.input.key.Key
import io.github.rehaancubess.joyframe.audio.SpatialMix
import io.github.rehaancubess.joyframe.input.*
import io.github.rehaancubess.joyframe.render.*
import io.github.rehaancubess.joyframe.render.gpu.*
import io.github.rehaancubess.joyframe.render.math.Vec3
import io.github.rehaancubess.joyframe.render.water.*
import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.*

class Alpha03FeatureTest {
    private val flatWater = WaterConfig(swell = WaterConfig().swell.map { it.copy(amplitude = 0f) })

    @Test fun chaseCameraSitsBehindAndAboveTheHeadingAndLooksAhead() {
        val camera = ChaseCamera().desired(Vec3(100f, 0f, 50f), Vec3(1f, 0f, 0f))
        assertEquals(100f - 168f, camera.eye.x, .001f)
        assertEquals(134f, camera.eye.y, .001f)
        assertEquals(100f + 148f, camera.target.x, .001f)
        assertEquals(50f, camera.eye.z, .001f)
        val fast = ChaseCamera().desired(Vec3.ZERO, Vec3(0f, 0f, -1f), speed = 1f)
        assertEquals(56f, fast.verticalFovDegrees, .001f)
        assertTrue(fast.eye.z > 168f, "speed pulls the boom back")
    }

    @Test fun chaseCameraDampsCutsAndShakesOnlyBriefly() {
        val chase = ChaseCamera()
        val start = chase.update(Vec3.ZERO, Vec3(1f, 0f, 0f), 0f, .016f)
        val moved = chase.update(Vec3(500f, 0f, 0f), Vec3(1f, 0f, 0f), 0f, .016f)
        assertTrue(moved.eye.x > start.eye.x && moved.eye.x < 500f - 168f, "a small step eases, it does not snap")
        chase.cut()
        assertEquals(500f - 168f, chase.update(Vec3(500f, 0f, 0f), Vec3(1f, 0f, 0f), 0f, .016f).eye.x, .001f)
        assertEquals(1000f - 168f, chase.update(Vec3(1000f, 0f, 0f), Vec3(1f, 0f, 0f), 0f, 1f).eye.x, .001f,
            "a long gap (resume) cuts instead of sweeping")
        chase.shake(20f)
        val shaken = chase.update(Vec3(1000f, 0f, 0f), Vec3(1f, 0f, 0f), 0f, .01f)
        assertTrue(abs(shaken.eye.x - (1000f - 168f)) > .5f || abs(shaken.eye.y - 134f) > .5f)
        val settled = chase.update(Vec3(1000f, 0f, 0f), Vec3(1f, 0f, 0f), 0f, .19f)
        assertEquals(134f, chase.update(Vec3(1000f, 0f, 0f), Vec3(1f, 0f, 0f), 0f, .05f).eye.y, .01f)
        assertTrue(settled.eye.x.isFinite())
        assertEquals(ChaseCameraStyle().distance * 2f, ChaseCameraStyle().scaled(2f).distance)
        val forward = ChaseCamera.forwardFromYaw((PI / 2).toFloat())
        assertEquals(-1f, forward.z, .0001f)
    }

    @Test fun fixedTimestepStepsEvenlyReportsAlphaAndCapsStalls() {
        val fixed = FixedTimestep(.01f, maxStepsPerFrame = 3)
        assertEquals(0, fixed.advance(.005f)); assertEquals(.5f, fixed.alpha, .001f)
        assertEquals(1, fixed.advance(.006f)); assertEquals(.1f, fixed.alpha, .01f)
        assertEquals(3, fixed.advance(10f), "a stall is capped")
        assertEquals(0f, fixed.alpha, .001f)
        assertEquals(4L, fixed.steps)
        fixed.reset(); assertEquals(0L, fixed.steps)
        assertFailsWith<IllegalArgumentException> { fixed.advance(-1f) }
    }

    @Test fun buoyancyFollowsTheSwellAndKeepsYaw() {
        val calm = Buoyancy.pose(flatWater, 10f, 20f, 1.2f, 3f, lift = 5f)
        assertEquals(5f, calm.translation.y, .0001f)
        assertEquals(0f, calm.rotation.x, .0001f); assertEquals(0f, calm.rotation.z, .0001f)
        assertEquals(1.2f, calm.rotation.y)
        val water = WaterConfig()
        val point = Buoyancy.pose(water, 300f, -120f, .4f, 2f)
        assertEquals(water.heightAt(300f, -120f, 2f, 1f), point.translation.y, .0001f)
        val hull = Buoyancy.pose(water, 300f, -120f, .4f, 2f, length = 40f, beam = 20f)
        // A short hull on long swell leans the same way as the point slope.
        assertTrue(point.rotation.z * hull.rotation.z >= 0f)
        assertTrue(abs(point.rotation.z - hull.rotation.z) < .05f)
    }

    @Test fun whirlpoolPullsInwardAndSwallowsTheCore() {
        val pool = Whirlpool(0f, 0f, radius = 400f, coreRadius = 80f)
        assertEquals(0f, pool.influenceAt(401f, 0f))
        assertEquals(1f, pool.influenceAt(50f, 0f))
        assertTrue(pool.influenceAt(200f, 0f) in .1f..1f)
        val (dx, dz) = pool.velocityChange(200f, 0f, .1f)
        assertTrue(dx < 0f, "pulls toward the centre")
        assertTrue(dz != 0f, "and swirls around it")
        val (cx, cz) = Whirlpool(0f, 0f, 400f, 80f, clockwise = false).velocityChange(200f, 0f, .1f)
        assertEquals(dx, cx, .0001f); assertEquals(-dz, cz, .0001f)
        assertTrue(pool.swallows(79f, 0f)); assertFalse(pool.swallows(81f, 0f))
        assertEquals(pool.influenceAt(150f, 0f), pool.surge(150f, 0f).hullSuction)
    }

    @Test fun waterHolesAndWhirlpoolDishesAreValidGeometry() {
        val hole = WaterHole.forWhirlpool(0f, 0f, 300f)
        assertTrue(hole.radius < 300f)
        val lake = Primitives.water("lake", 2000f, "w", 40, holes = listOf(hole))
        val full = Primitives.water("full", 2000f, "w", 40)
        assertTrue(lake.indices.size < full.indices.size)
        assertTrue(lake.indices.chunked(3).none { tri ->
            val cx = tri.sumOf { lake.positions[it].x.toDouble() } / 3
            val cz = tri.sumOf { lake.positions[it].z.toDouble() } / 3
            cx * cx + cz * cz < hole.radius * hole.radius
        })
        val dish = Primitives.whirlpool("pool", 300f, "v")
        assertTrue(dish.indices.all { it in dish.positions.indices })
        assertEquals(dish.positions.size, dish.textureCoordinates.size)
        assertTrue(dish.positions.minOf { it.y } < -4f, "the funnel sinks")
        assertEquals(1.2f, dish.positions.maxOf { it.y }, .001f)
        // Over the whole overlap band (hole edge minus a grid cell, out to the rim) the dish stays above y = 0.
        assertTrue(dish.positions.filter { kotlin.math.hypot(it.x, it.z) >= hole.radius - 300f * .06f }.all { it.y > 0f })
        assertFailsWith<IllegalArgumentException> { Primitives.water("gone", 10f, "w", 2, holes = listOf(WaterHole(0f, 0f, 100f))) }
        val assets = sceneAssets("pool") { whirlpoolMaterial("v", 0xff1188aa.toInt()); mesh(dish); model("pool", ModelPart("pool")) }
        assertTrue(assets.materials.getValue("v").isVortex)
        val frame = assets.frame(GpuCamera(Vec3(0f, 10f, 10f), Vec3.ZERO, verticalFovDegrees = 50f, nearPlane = 1f, farPlane = 100f),
            1f, vortexSurge = VortexSurge(hullSuction = .5f)) { instance("pool", "pool") }
        assertEquals(.5f, frame.vortexSurge.hullSuction)
    }

    @Test fun splitLayoutCoversTheSurfaceWithoutOverlap() {
        assertEquals(listOf(PaneRect(0, 0, 800, 600)), SplitLayout.panes(1, 800, 600))
        val landscape = SplitLayout.panes(2, 800, 600, gap = 4)
        assertEquals(0, landscape[0].y); assertEquals(600, landscape[1].height)
        assertEquals(800, landscape[0].width + landscape[1].width + 4)
        val portrait = SplitLayout.panes(2, 600, 800, gap = 4)
        assertEquals(600, portrait[0].width); assertEquals(800, portrait[0].height + portrait[1].height + 4)
        val quads = SplitLayout.panes(4, 801, 601, gap = 3)
        quads.forEachIndexed { i, a -> quads.drop(i + 1).forEach { b ->
            assertTrue(a.x + a.width <= b.x || b.x + b.width <= a.x || a.y + a.height <= b.y || b.y + b.height <= a.y)
        } }
        assertEquals(801 * 601 - quads.sumOf { it.width * it.height },
            3 * 601 + 3 * 801 - 9, "only the gutter is uncovered")
        assertEquals(3, SplitLayout.panes(3, 800, 600).size)
        assertEquals(PaneRect(0, 500, 10, 100), PaneRect(0, 0, 10, 100).flippedY(600))
        assertFailsWith<IllegalArgumentException> { SplitLayout.panes(5, 10, 10) }
    }

    @Test fun weatherFillsAroundTheEyeWithUniqueEffectInstances() {
        val assets = sceneAssets("weather") { weatherParticles() }
        val eye = Vec3(5000f, 400f, -3000f)
        val camera = GpuCamera(eye, Vec3.ZERO, verticalFovDegrees = 50f, nearPlane = 1f, farPlane = 10f)
        Weather.entries.forEach { kind ->
            val frame = assets.frame(camera, 12.5f) { weather(kind, camera, 12.5f) }
            if (kind == Weather.Clear) assertTrue(frame.instances.isEmpty()) else {
                assertTrue(frame.instances.size > 50)
                assertEquals(frame.instances.size, frame.instances.map { it.id }.toSet().size)
                assertTrue(frame.instances.all { it.kind == InstanceKind.Effect && it.opacity < 1f })
                // Layers sit ahead of the eye: the farthest spans up to two half-extents away.
                assertTrue(frame.instances.all { (it.transform.translation - eye).length() <= 2f * 1180f * 1.8f })
                assertTrue(frame.instances.none { (it.transform.translation - eye).length() < 5f && it.opacity > .05f })
            }
        }
        assertEquals(SceneEnvironment(), Weather.Clear.environment(SceneEnvironment()))
        assertTrue(Weather.Rain.environment(SceneEnvironment()).fogEnd < SceneEnvironment().fogEnd)
    }

    @Test fun modelBoundsIncludePartTransformsAndTurntableFramesThem() {
        val assets = sceneAssets("bounds") {
            material("m", -1); box("cube", Vec3(2f, 2f, 2f), "m")
            model("pair", ModelPart("cube"), ModelPart("cube", Transform3D(Vec3(10f, 0f, 0f), scale = Vec3(2f, 2f, 2f))))
        }
        val bounds = assets.modelBounds("pair")
        assertEquals(-1f, bounds.minimum.x, .001f); assertEquals(12f, bounds.maximum.x, .001f)
        assertEquals(2f, bounds.maximum.y, .001f)
        val camera = turntableCamera(assets, "pair")
        assertTrue((camera.eye - camera.target).length() > 12f)
        assertTrue(camera.eye.y > camera.target.y)
    }

    @Test fun seatsStayStableAcrossDisconnects() {
        val seats = GamepadSeats(4)
        fun pad(id: String) = ConnectedGamepad(id, id, GamepadState())
        seats.update(listOf(pad("a"), pad("b"), pad("c")))
        assertEquals("b", seats.pad(1)?.id)
        seats.update(listOf(pad("a"), pad("c")))
        assertNull(seats.pad(1)); assertEquals("c", seats.pad(2)?.id)
        seats.update(listOf(pad("d"), pad("a"), pad("c")))
        assertEquals("d", seats.pad(1)?.id, "a newcomer fills the lowest free seat")
        assertEquals("a", seats.pad(0)?.id); assertEquals(3, seats.occupied)
    }

    @Test fun sharedKeyboardBindingsKeepPlayersApartAndDriveReadsTriggers() {
        val one = ActionInput(KeyBindings.Wasd)
        val two = ActionInput(KeyBindings.Arrows)
        assertTrue(one.key(Key.W, true)); assertFalse(one.key(Key.DirectionUp, true))
        assertTrue(two.key(Key.DirectionUp, true)); assertFalse(two.key(Key.W, true))
        assertTrue(two.key(Key.Enter, true))
        assertEquals(1f, one.poll().movement.y); assertEquals(setOf(GameAction.Interact), two.poll().pressed)
        val pad = ActionInput().poll(GamepadState(rightTrigger = .8f, leftTrigger = .2f,
            buttons = setOf(GamepadButton.DpadLeft)))
        assertEquals(.6f, pad.drive.y, .001f); assertEquals(-1f, pad.drive.x)
        assertEquals(-1f, pad.movement.x, .001f, "the D-pad moves like the stick")
        assertTrue(GamepadState(buttons = setOf(GamepadButton.North)).isPressed(GamepadButton.North))
    }

    @Test fun spatialMixPansToTheRightSideAndFadesWithDistance() {
        // Facing +X, the listener's right is +Z.
        val right = SpatialMix.of(0f, 0f, 1f, 0f, 0f, 500f, range = 1000f)
        assertTrue(right.pan > .5f)
        val left = SpatialMix.of(0f, 0f, 1f, 0f, 0f, -500f, range = 1000f)
        assertEquals(-right.pan, left.pan, .0001f)
        assertEquals(0f, SpatialMix.of(0f, 0f, 1f, 0f, 2000f, 0f, range = 1000f).volume)
        assertEquals(1f, SpatialMix.of(0f, 0f, 1f, 0f, 10f, 0f, range = 1000f).volume, .0001f)
        assertEquals(0f, SpatialMix.of(0f, 0f, 1f, 0f, 500f, 0f, range = 1000f).pan, .0001f)
        val camera = GpuCamera(Vec3(0f, 100f, 0f), Vec3(100f, 0f, 0f), verticalFovDegrees = 50f, nearPlane = 1f, farPlane = 10f)
        assertTrue(camera.hear(Vec3(0f, 0f, 400f), 1000f).pan > 0f)
    }
}
