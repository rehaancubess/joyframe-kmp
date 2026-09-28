// SPDX-License-Identifier: Apache-2.0
package io.github.rehaancubess.joyframe.render

import io.github.rehaancubess.joyframe.render.gpu.*
import io.github.rehaancubess.joyframe.render.math.Vec3
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
    fun water(id: String, size: Float, material: String, subdivisions: Int = 64, energy: Float = 1f, depth: Float = 1f) =
        mesh(Primitives.water(id, size, material, subdivisions, energy, depth))
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

    /** UV.x stores wave energy and normal.x stores opacity for Joyframe's water shaders. */
    fun water(id: String, size: Float, material: String, subdivisions: Int = 64, energy: Float = 1f, depth: Float = 1f): IndexedMesh {
        require(size.isFinite() && size > 0 && subdivisions in 1..256)
        require(energy in 0f..1f && depth in 0f..1f)
        val positions = (0..subdivisions).flatMap { z -> (0..subdivisions).map { x ->
            Vec3(size * (x.toFloat()/subdivisions-.5f), 0f, size * (z.toFloat()/subdivisions-.5f))
        } }
        val indices = mutableListOf<Int>()
        repeat(subdivisions) { z -> repeat(subdivisions) { x ->
            val a = z * (subdivisions+1) + x; val b = a+subdivisions+1
            indices.addAll(listOf(a,b,a+1,a+1,b,b+1))
        } }
        return IndexedMesh(id, positions, List(positions.size) { Vec3(1f,1f,0f) },
            List(positions.size) { Vec2f(energy,depth) }, indices,
            listOf(MeshPrimitive(0,indices.size,material)),
            Bounds3(Vec3(-size/2,0f,-size/2),Vec3(size/2,0f,size/2)))
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
    fun instance(id: String, model: String, transform: Transform3D = Transform3D(),
                 kind: InstanceKind = InstanceKind.Static, previous: Transform3D = transform,
                 alpha: Float = 1f, materials: Map<String,String> = emptyMap()) {
        require(instances.none { it.id == id }) { "Duplicate instance: $id" }
        instances += SceneInstance(id,kind,model,previous,transform,alpha,materials)
    }
}

fun GpuSceneAssets.frame(camera: GpuCamera, seconds: Float, tick: Long = 0,
                        water: WaterConfig = WaterConfig(), environment: SceneEnvironment = SceneEnvironment(),
                        lights: List<DirectionalLight> = listOf(DirectionalLight(Vec3(-1f,-1f,-1f))),
                        build: SceneFrameBuilder.() -> Unit): GpuSceneFrame =
    GpuSceneFrame(this,camera,environment,lights,SceneFrameBuilder().apply(build).instances.toList(),tick,seconds,water)
