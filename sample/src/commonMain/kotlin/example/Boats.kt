// SPDX-License-Identifier: Apache-2.0
package example

import io.github.rehaancubess.joyframe.input.Movement
import io.github.rehaancubess.joyframe.render.ChaseCamera
import io.github.rehaancubess.joyframe.render.gpu.Transform3D
import io.github.rehaancubess.joyframe.render.math.Vec3
import io.github.rehaancubess.joyframe.render.water.Buoyancy
import io.github.rehaancubess.joyframe.render.water.WaterConfig
import io.github.rehaancubess.joyframe.render.water.Whirlpool
import kotlin.math.*

/** Map: the stick points where the boat goes (overview camera). Drive: throttle and steer, as in the game. */
enum class Steering { Map, Drive }

/**
 * Sample-only boat handling, not a game rule built into the toolkit. Advance it with fixed steps
 * (see FixedTimestep) and draw it with [transform], which interpolates between the last two steps.
 */
class BoatMotion(private val startX: Float = 0f, private val startZ: Float = 0f, private val startYaw: Float = 0f) {
    var x = startX; private set
    var z = startZ; private set
    var yaw = startYaw; private set
    private var vx = 0f
    private var vz = 0f
    private var previousX = x
    private var previousZ = z
    private var previousYaw = yaw

    /** Forward speed as a fraction of top speed, for the camera's speed framing. */
    val speed: Float get() = ((vx*cos(yaw) - vz*sin(yaw)) / MAX_SPEED).coerceIn(0f,1f)

    /** One fixed step. Returns true when a whirlpool swallowed the boat and it respawned. */
    fun step(input: Movement, steering: Steering, dt: Float, pools: List<Whirlpool> = emptyList()): Boolean {
        require(dt.isFinite() && dt >= 0)
        previousX = x; previousZ = z; previousYaw = yaw
        when(steering) {
            Steering.Map -> {
                val targetX = input.x * MAP_SPEED
                val targetZ = -input.y * MAP_SPEED
                val blend = 1f - exp(-dt*4f)
                vx += (targetX-vx)*blend; vz += (targetZ-vz)*blend
                if(abs(input.x)+abs(input.y) > .01f) {
                    val delta = wrap(atan2(input.y,input.x) - yaw)
                    yaw += delta * (1f-exp(-dt*9f))
                }
            }
            Steering.Drive -> {
                // Turning bites harder with speed, but a stopped boat can still pivot slowly.
                val turn = TURN_RATE * (.4f + .6f*speed)
                yaw -= input.x.coerceIn(-1f,1f) * turn * dt
                val forwardX = cos(yaw); val forwardZ = -sin(yaw)
                val throttle = input.y.coerceIn(-.5f,1f)
                vx += forwardX * throttle * ACCELERATION * dt
                vz += forwardZ * throttle * ACCELERATION * dt
                // Water resists sideways slip far more than forward motion.
                val along = vx*forwardX + vz*forwardZ
                val sideX = vx - forwardX*along; val sideZ = vz - forwardZ*along
                val keptAlong = along * exp(-dt*DRAG)
                val keptSide = exp(-dt*SIDE_DRAG)
                vx = forwardX*keptAlong + sideX*keptSide
                vz = forwardZ*keptAlong + sideZ*keptSide
                val magnitude = sqrt(vx*vx+vz*vz)
                if(magnitude > MAX_SPEED) { vx *= MAX_SPEED/magnitude; vz *= MAX_SPEED/magnitude }
            }
        }
        pools.forEach { pool ->
            val (dx,dz) = pool.velocityChange(x,z,dt)
            vx += dx; vz += dz
            yaw += (if(pool.clockwise) 1f else -1f) * pool.influenceAt(x,z) * 1.6f * dt
        }
        x += vx*dt; z += vz*dt
        if(abs(x) > LIMIT) { x = x.coerceIn(-LIMIT,LIMIT); vx = -vx*.3f }
        if(abs(z) > LIMIT) { z = z.coerceIn(-LIMIT,LIMIT); vz = -vz*.3f }
        if(pools.any { it.swallows(x,z) }) { reset(); return true }
        return false
    }

    /** Position and heading between the previous and latest fixed step. */
    fun pose(alpha: Float = 1f): Triple<Float,Float,Float> {
        val t = alpha.coerceIn(0f,1f)
        return Triple(previousX+(x-previousX)*t, previousZ+(z-previousZ)*t, previousYaw+wrap(yaw-previousYaw)*t)
    }

    fun position(alpha: Float = 1f) = pose(alpha).let { (px,pz,_) -> Vec3(px,0f,pz) }
    fun forward(alpha: Float = 1f) = ChaseCamera.forwardFromYaw(pose(alpha).third)

    fun reset() {
        x = startX; z = startZ; yaw = startYaw; vx = 0f; vz = 0f
        previousX = x; previousZ = z; previousYaw = yaw
    }

    /** Keeps two hulls from sailing through each other. */
    fun separateFrom(other: BoatMotion, distance: Float = 230f) {
        val dx = other.x - x; val dz = other.z - z
        val gap = sqrt(dx*dx+dz*dz)
        if(gap >= distance || gap < 1e-3f) return
        val push = (distance-gap)*.5f/gap
        x -= dx*push; z -= dz*push; other.x += dx*push; other.z += dz*push
    }

    fun transform(water: WaterConfig, seconds: Float, alpha: Float = 1f, imported: Boolean = false): Transform3D {
        val (px,pz,heading) = pose(alpha)
        val size = if(imported) 1f else 1.6f
        // The hull rides the same swell the water shader draws.
        return Buoyancy.pose(water,px,pz,heading,seconds,lift=if(imported) 0f else 24f,scale=size,energy=::lakeEnergyAt)
    }

    private companion object {
        const val MAP_SPEED = 420f
        const val MAX_SPEED = 560f
        const val ACCELERATION = 900f
        const val TURN_RATE = 2.1f
        const val DRAG = 1.1f
        const val SIDE_DRAG = 5.5f
        const val LIMIT = 900f
        fun wrap(angle: Float) = atan2(sin(angle),cos(angle))
    }
}
