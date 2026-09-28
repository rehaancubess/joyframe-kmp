// SPDX-License-Identifier: Apache-2.0
package io.github.rehaancubess.joyframe.render

import io.github.rehaancubess.joyframe.render.gpu.*
import io.github.rehaancubess.joyframe.render.math.Mat
import io.github.rehaancubess.joyframe.render.math.Vec3
import io.github.rehaancubess.joyframe.render.water.VortexConfig
import io.github.rehaancubess.joyframe.render.water.VortexSurge
import io.github.rehaancubess.joyframe.render.water.WaterConfig
import kotlin.math.*

/** Build once, retain, and use a new cache key whenever geometry/materials change. */
fun sceneAssets(cacheKey: String, build: SceneBuilder.() -> Unit): GpuSceneAssets =
    SceneBuilder().apply(build).build(cacheKey)

class SceneBuilder {
    private val meshes = linkedMapOf<String, IndexedMesh>()
    private val materials = linkedMapOf<String, GpuMaterial>()
    private val models = linkedMapOf<String, ModelDefinition>()
    private val textures = linkedMapOf<String, TextureData>()
    fun material(id: String, color: Int, water: Boolean = false) = material(
        GpuMaterial(id, PackedColor(color), waterShade = if (water) 1f else 0f))
    /** Water that shades as a swirling whirlpool funnel. Use it only on [Primitives.whirlpool] meshes. */
    fun whirlpoolMaterial(id: String, color: Int) =
        material(GpuMaterial(id, PackedColor(color), waterShade = 1f, isVortex = true))
    fun material(value: GpuMaterial) { require(value.id !in materials); materials[value.id] = value }
    fun texture(value: TextureData) { require(value.id !in textures); textures[value.id] = value }
    fun mesh(value: IndexedMesh) { require(value.id !in meshes); meshes[value.id] = value }
    fun model(id: String, vararg parts: ModelPart) {
        require(id !in models && parts.isNotEmpty())
        models[id] = ModelDefinition(id, parts.toList())
    }
    fun box(id: String, size: Vec3, material: String) = mesh(Primitives.box(id, size, material))
    fun cone(id: String, radius: Float, height: Float, material: String, sides: Int = 8) =
        mesh(Primitives.cone(id, radius, height, material, sides))
    fun water(id: String, size: Float, material: String, subdivisions: Int = 64, energy: Float = 1f, depth: Float = 1f,
              holes: List<WaterHole> = emptyList()) =
        mesh(Primitives.water(id, size, material, subdivisions, energy, depth, holes))
    fun whirlpool(id: String, radius: Float, material: String, config: VortexConfig = VortexConfig(), clockwise: Boolean = true) =
        mesh(Primitives.whirlpool(id, radius, material, config, clockwise))
    internal fun build(key: String): GpuSceneAssets {
        require(key.isNotBlank())
        return GpuSceneAssets(key, meshes.toMap(), materials.toMap(), models.toMap(), textures.toMap())
    }
}

/** Centered boxes, bottom-origin cones and an XZ water grid; dimensions are world units. */
object Primitives {
    fun box(id: String, size: Vec3, material: String): IndexedMesh {
        require(listOf(size.x, size.y, size.z).all { it.isFinite() && it > 0f })
        val x = size.x / 2; val y = size.y / 2; val z = size.z / 2
        val p = listOf(Vec3(-x,-y,-z),Vec3(x,-y,-z),Vec3(x,y,-z),Vec3(-x,y,-z),
            Vec3(-x,-y,z),Vec3(x,-y,z),Vec3(x,y,z),Vec3(-x,y,z))
        val triangles = listOf(0,2,1,0,3,2,4,5,6,4,6,7,0,4,7,0,7,3,
            1,2,6,1,6,5,3,7,6,3,6,2,0,1,5,0,5,4)
        return flatMesh(id, triangles.map { p[it] }, material)
    }

    fun cone(id: String, radius: Float, height: Float, material: String, sides: Int = 8): IndexedMesh {
        require(radius.isFinite() && radius > 0 && height.isFinite() && height > 0 && sides in 3..128)
        val p = mutableListOf<Vec3>()
        fun ring(i: Int) = Vec3(cos(i * 2 * PI / sides).toFloat() * radius, 0f,
            sin(i * 2 * PI / sides).toFloat() * radius)
        repeat(sides) { i ->
            p.addAll(listOf(ring(i),Vec3(0f,height,0f),ring(i+1),ring(i),ring(i+1),Vec3.ZERO))
        }
        return flatMesh(id, p, material)
    }

    /** UV.x stores wave energy and normal.x stores opacity for Joyframe's water shaders.
     * [holes] (mesh-local XZ) leave room for whirlpool dishes; their edge follows the grid, so make
     * each dish a little larger than its hole, as [WaterHole.forWhirlpool] does.
     */
    fun water(id: String, size: Float, material: String, subdivisions: Int = 64, energy: Float = 1f, depth: Float = 1f,
              holes: List<WaterHole> = emptyList()): IndexedMesh {
        require(size.isFinite() && size > 0 && subdivisions in 1..256)
        require(energy in 0f..1f && depth in 0f..1f)
        val positions = (0..subdivisions).flatMap { z -> (0..subdivisions).map { x ->
            Vec3(size * (x.toFloat()/subdivisions-.5f), 0f, size * (z.toFloat()/subdivisions-.5f))
        } }
        val indices = mutableListOf<Int>()
        fun keep(a: Int, b: Int, c: Int): Boolean {
            if (holes.isEmpty()) return true
            val cx = (positions[a].x + positions[b].x + positions[c].x) / 3f
            val cz = (positions[a].z + positions[b].z + positions[c].z) / 3f
            return holes.none { (cx - it.x) * (cx - it.x) + (cz - it.z) * (cz - it.z) < it.radius * it.radius }
        }
        repeat(subdivisions) { z -> repeat(subdivisions) { x ->
            val a = z * (subdivisions+1) + x; val b = a+subdivisions+1
            if (keep(a,b,a+1)) indices.addAll(listOf(a,b,a+1))
            if (keep(a+1,b,b+1)) indices.addAll(listOf(a+1,b,b+1))
        } }
        require(indices.isNotEmpty()) { "$id: holes removed the whole water surface" }
        return IndexedMesh(id, positions, List(positions.size) { Vec3(1f,1f,0f) },
            List(positions.size) { Vec2f(energy,depth) }, indices,
            listOf(MeshPrimitive(0,indices.size,material)),
            Bounds3(Vec3(-size/2,0f,-size/2),Vec3(size/2,0f,size/2)))
    }

    /**
     * A whirlpool funnel: a polar dish centred on the origin whose surface sinks toward the eye, for a
     * [SceneBuilder.whirlpoolMaterial]. Rings bunch toward the centre, where the funnel curves hardest.
     * UVs carry (normalised radius, angle in turns); the shader spins, foams and darkens from them.
     * The rim sits [rimLift] above y = 0, high enough that the dish stays above a y = 0 lake across
     * the jagged edge of its [WaterHole] (for lake cells up to about a tenth of the dish radius).
     * Match [VortexConfig.dishDepth] to the lake's depth there so the rim blends in.
     */
    fun whirlpool(id: String, radius: Float, material: String, config: VortexConfig = VortexConfig(),
                  clockwise: Boolean = true, rimLift: Float = 1.2f): IndexedMesh {
        require(radius.isFinite() && radius > 0f && rimLift.isFinite())
        fun ringRadius(ring: Int) = radius * (ring.toFloat() / config.rings).pow(config.ringBias)
        fun point(ring: Int, spoke: Int): Vec3 {
            val r = ringRadius(ring)
            val angle = spoke.toFloat() / config.spokes * 2f * PI.toFloat()
            return Vec3(cos(angle) * r, rimLift + config.surfaceOffsetAt(r / radius), sin(angle) * r)
        }
        // Mirroring the angle turns the whole spiral the other way; each quad owns its vertices so the
        // seam keeps a consistent 0.975..1 range, and the shader only uses the angle periodically.
        fun uv(ring: Int, spoke: Int): Vec2f {
            val turns = spoke.toFloat() / config.spokes
            return Vec2f(ringRadius(ring) / radius, if (clockwise) turns else 1f - turns)
        }
        val positions = mutableListOf<Vec3>()
        val uvs = mutableListOf<Vec2f>()
        val indices = mutableListOf<Int>()
        repeat(config.spokes) { spoke ->
            val next = spoke + 1
            for (ring in 0 until config.rings) {
                val base = positions.size
                listOf(ring to next, ring + 1 to next, ring + 1 to spoke, ring to spoke).forEach { (r, s) ->
                    positions += point(r, s); uvs += uv(r, s)
                }
                indices.addAll(listOf(base, base + 1, base + 2, base, base + 2, base + 3))
            }
        }
        return IndexedMesh(id, positions, List(positions.size) { Vec3.UP }, uvs, indices,
            listOf(MeshPrimitive(0, indices.size, material)),
            Bounds3(Vec3(-radius, rimLift - config.depth, -radius), Vec3(radius, rimLift, radius)))
    }

    /** A double pyramid: a cheap faceted sparkle for particles, gems and pickups. */
    fun octahedron(id: String, radius: Float, material: String): IndexedMesh {
        require(radius.isFinite() && radius > 0f)
        val top = Vec3(0f, radius, 0f); val bottom = Vec3(0f, -radius, 0f)
        val ring = listOf(Vec3(radius, 0f, 0f), Vec3(0f, 0f, -radius), Vec3(-radius, 0f, 0f), Vec3(0f, 0f, radius))
        val triangles = ring.indices.flatMap { i ->
            val a = ring[i]; val b = ring[(i + 1) % ring.size]
            listOf(a, b, top, b, a, bottom)
        }
        return flatMesh(id, triangles, material)
    }

    /** Vertices in counter-clockwise groups of three; creates flat-shaded normals. */
    fun flatMesh(id: String, triangles: List<Vec3>, material: String): IndexedMesh {
        require(triangles.isNotEmpty() && triangles.size % 3 == 0)
        require(triangles.all { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() })
        val normals = triangles.chunked(3).flatMap { (a,b,c) ->
            val n = (b-a).cross(c-a).normalized(); List(3) { n }
        }
        return IndexedMesh(id, triangles.toList(), normals, emptyList(), triangles.indices.toList(),
            listOf(MeshPrimitive(0,triangles.size,material)),
            Bounds3(Vec3(triangles.minOf{it.x},triangles.minOf{it.y},triangles.minOf{it.z}),
                Vec3(triangles.maxOf{it.x},triangles.maxOf{it.y},triangles.maxOf{it.z})))
    }
}

/** Frame assembly without repeating interpolation and material boilerplate. */
class SceneFrameBuilder {
    internal val instances = mutableListOf<SceneInstance>()
    private val ids = mutableSetOf<String>()
    fun instance(id: String, model: String, transform: Transform3D = Transform3D(),
                 kind: InstanceKind = InstanceKind.Static, previous: Transform3D = transform,
                 alpha: Float = 1f, materials: Map<String,String> = emptyMap(), opacity: Float = 1f) {
        require(ids.add(id)) { "Duplicate instance: $id" }
        instances += SceneInstance(id,kind,model,previous,transform,alpha,materials,opacity=opacity)
    }
}

/** A circular gap in a [Primitives.water] grid, in mesh-local XZ. */
data class WaterHole(val x: Float, val z: Float, val radius: Float) {
    init { require(x.isFinite() && z.isFinite() && radius.isFinite() && radius > 0f) }
    companion object {
        /** The hole a whirlpool dish of [dishRadius] needs: small enough that the dish overlaps its edge. */
        fun forWhirlpool(x: Float, z: Float, dishRadius: Float, config: VortexConfig = VortexConfig()) =
            WaterHole(x, z, dishRadius * config.lagoonHoleScale / config.meshRadiusScale)
    }
}

fun GpuSceneAssets.frame(camera: GpuCamera, seconds: Float, tick: Long = 0,
                        water: WaterConfig = WaterConfig(), environment: SceneEnvironment = SceneEnvironment(),
                        lights: List<DirectionalLight> = listOf(DirectionalLight(Vec3(-1f,-1f,-1f))),
                        vortex: VortexConfig = VortexConfig(), vortexSurge: VortexSurge = VortexSurge(),
                        build: SceneFrameBuilder.() -> Unit): GpuSceneFrame =
    GpuSceneFrame(this,camera,environment,lights,SceneFrameBuilder().apply(build).instances.toList(),tick,seconds,water,
        vortex,vortexSurge)

/**
 * Axis-aligned bounds of [modelId] in model space, including every part's local transform.
 * Useful for framing a camera, as [ModelTurntable] does.
 */
fun GpuSceneAssets.modelBounds(modelId: String): Bounds3 {
    val model = requireNotNull(models[modelId]) { "Unknown model: $modelId" }
    val matrix = FloatArray(Mat.SIZE)
    val normal = FloatArray(Mat.SIZE)
    var min = Vec3(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
    var max = Vec3(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
    model.parts.forEach { part ->
        val bounds = meshes.getValue(part.meshId).bounds
        Mat.compose(part.localTransform, matrix, normal)
        for (corner in 0 until 8) {
            val x = if (corner and 1 == 0) bounds.minimum.x else bounds.maximum.x
            val y = if (corner and 2 == 0) bounds.minimum.y else bounds.maximum.y
            val z = if (corner and 4 == 0) bounds.minimum.z else bounds.maximum.z
            val p = Vec3(matrix[0]*x + matrix[4]*y + matrix[8]*z + matrix[12],
                matrix[1]*x + matrix[5]*y + matrix[9]*z + matrix[13],
                matrix[2]*x + matrix[6]*y + matrix[10]*z + matrix[14])
            min = Vec3(minOf(min.x,p.x), minOf(min.y,p.y), minOf(min.z,p.z))
            max = Vec3(maxOf(max.x,p.x), maxOf(max.y,p.y), maxOf(max.z,p.z))
        }
    }
    return Bounds3(min, max)
}
