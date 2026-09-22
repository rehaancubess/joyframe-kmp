// SPDX-License-Identifier: Apache-2.0
package io.github.rehaancubess.joyframe.render.gltf

import io.github.rehaancubess.joyframe.render.gpu.Bounds3
import io.github.rehaancubess.joyframe.render.gpu.GpuBlendMode
import io.github.rehaancubess.joyframe.render.gpu.GpuCullMode
import io.github.rehaancubess.joyframe.render.gpu.GpuMaterial
import io.github.rehaancubess.joyframe.render.gpu.GpuSceneAssets
import io.github.rehaancubess.joyframe.render.gpu.IndexedMesh
import io.github.rehaancubess.joyframe.render.gpu.MeshPrimitive
import io.github.rehaancubess.joyframe.render.gpu.ModelDefinition
import io.github.rehaancubess.joyframe.render.gpu.ModelPart
import io.github.rehaancubess.joyframe.render.gpu.PackedColor
import io.github.rehaancubess.joyframe.render.gpu.TextureData
import io.github.rehaancubess.joyframe.render.gpu.Vec2f
import io.github.rehaancubess.joyframe.render.math.Mat
import io.github.rehaancubess.joyframe.render.math.Vec3

class GltfException(message: String) : Exception(message)

class GltfModel(
    val meshes: Map<String, IndexedMesh>,
    val materials: Map<String, GpuMaterial>,
    val textures: Map<String, TextureData>,
    val model: ModelDefinition,
) {

    fun mergeInto(assets: GpuSceneAssets, cacheKey: String): GpuSceneAssets = assets.copy(
        cacheKey = cacheKey,
        meshes = assets.meshes + meshes,
        materials = assets.materials + materials,
        textures = assets.textures + textures,
        models = assets.models + (model.id to model),
    )
}

class EncodedImage(val bytes: ByteArray, val mimeType: String?)

class GltfDocument(
    val meshes: Map<String, IndexedMesh>,
    val materials: Map<String, GpuMaterial>,

    val images: Map<String, EncodedImage>,
    val model: ModelDefinition,
) {

    fun resolve(textures: Map<String, TextureData>): GltfModel {
        val resolved = materials.mapValues { (_, material) ->
            val albedo = material.albedo
            if (albedo != null && albedo !in textures) material.copy(albedo = null) else material
        }
        return GltfModel(meshes = meshes, materials = resolved, textures = textures, model = model)
    }


    fun resolveWith(decode: (bytes: ByteArray, mimeType: String?, label: String) -> TextureData?): GltfModel {
        val textures = HashMap<String, TextureData>()
        images.forEach { (id, image) ->
            val decoded = decode(image.bytes, image.mimeType, id)
            if (decoded != null) textures[id] = decoded else println("glTF ${model.id}: could not decode image $id (${image.mimeType})")
        }
        return resolve(textures)
    }
}

object GltfLoader {

    /** Loads binary glTF and decodes embedded images, including the browser's asynchronous decoder.
     * Supports static triangle meshes, node transforms and base-colour textures; no skinning,
     * animation, morph targets, external buffers or extension-compressed meshes.
     */
    suspend fun loadGlbAsync(bytes: ByteArray, namespace: String, maxTextureEdge: Int = 2048): GltfModel {
        require(namespace.isNotBlank())
        require(maxTextureEdge in 1..8192)
        val document = readGlb(bytes, namespace)
        val textures = document.images.mapNotNull { (id, image) ->
            decodeImageAsync(image.bytes, image.mimeType, id, maxTextureEdge)?.let { id to it }
        }.toMap()
        return document.resolve(textures)
    }

    private const val MAGIC = 0x46546C67 // "glTF"
    private const val CHUNK_JSON = 0x4E4F534A
    private const val CHUNK_BIN = 0x004E4942

    private const val MODE_TRIANGLES = 4

    private const val COMPONENT_BYTE = 5120
    private const val COMPONENT_UNSIGNED_BYTE = 5121
    private const val COMPONENT_SHORT = 5122
    private const val COMPONENT_UNSIGNED_SHORT = 5123
    private const val COMPONENT_UNSIGNED_INT = 5125
    private const val COMPONENT_FLOAT = 5126


    fun loadGlb(bytes: ByteArray, namespace: String): GltfModel =
        readGlb(bytes, namespace).resolveWith { image, _, label -> decodeImage(image, label) }


    fun readGlb(bytes: ByteArray, namespace: String): GltfDocument {
        val (json, binary) = readContainer(bytes)
        val root = Json.parse(json) as? JsonObject
            ?: throw GltfException("$namespace: the glTF chunk is not a JSON object")
        return Gltf(root, binary, namespace).build()
    }


    private fun readContainer(bytes: ByteArray): Pair<String, ByteArray> {
        if (bytes.size < 12) throw GltfException("not a .glb: only ${bytes.size} bytes")
        if (bytes.readInt(0) != MAGIC) throw GltfException("not a .glb: bad magic")
        val version = bytes.readInt(4)
        if (version != 2) throw GltfException("unsupported glTF container version $version")
        val declared = bytes.readInt(8)
        if (declared > bytes.size) {
            throw GltfException("truncated .glb: header declares $declared bytes, file has ${bytes.size}")
        }

        var offset = 12
        var json: String? = null
        var binary = ByteArray(0)
        while (offset + 8 <= declared) {
            val length = bytes.readInt(offset)
            val type = bytes.readInt(offset + 4)
            val start = offset + 8
            if (start + length > declared) throw GltfException("truncated .glb: chunk runs past the end")
            when (type) {
                CHUNK_JSON -> json = bytes.decodeToString(start, start + length)
                CHUNK_BIN -> binary = bytes.copyOfRange(start, start + length)
                else -> Unit
            }
            offset = start + length + ((4 - length % 4) % 4)
        }
        return (json ?: throw GltfException("no JSON chunk in the .glb")) to binary
    }


    private class Gltf(
        private val root: JsonObject,
        private val binaryChunk: ByteArray,
        private val namespace: String,
    ) {
        private val buffers = root.array("buffers")?.objects().orEmpty()
        private val bufferViews = root.array("bufferViews")?.objects().orEmpty()
        private val accessors = root.array("accessors")?.objects().orEmpty()
        private val nodes = root.array("nodes")?.objects().orEmpty()
        private val gltfMeshes = root.array("meshes")?.objects().orEmpty()
        private val gltfMaterials = root.array("materials")?.objects().orEmpty()
        private val gltfTextures = root.array("textures")?.objects().orEmpty()
        private val gltfImages = root.array("images")?.objects().orEmpty()

        private val meshes = LinkedHashMap<String, IndexedMesh>()
        private val materials = LinkedHashMap<String, GpuMaterial>()
        private val images = LinkedHashMap<String, EncodedImage>()
        private val parts = ArrayList<ModelPart>()

        fun build(): GltfDocument {
            gltfMaterials.indices.forEach(::material)
            materials[defaultMaterialId] = GpuMaterial(defaultMaterialId, PackedColor.White)

            val identity = FloatArray(Mat.SIZE).also(Mat::identity)
            rootNodes().forEach { node -> visit(node, identity) }

            if (parts.isEmpty()) throw GltfException("$namespace: the .glb contains no triangle geometry")
            val used = meshes.values.flatMap { it.primitives }.map { it.materialId }.toSet()
            materials.keys.retainAll { it in used }

            return GltfDocument(
                meshes = meshes,
                materials = materials,
                images = images,
                model = ModelDefinition(id = namespace, parts = parts),
            )
        }

        private val defaultMaterialId get() = "$namespace/material/default"

        private fun rootNodes(): List<Int> {
            val sceneIndex = root.int("scene", 0)
            val scene = root.array("scenes")?.obj(sceneIndex)
            scene?.array("nodes")?.ints()?.let { if (it.isNotEmpty()) return it }
            return nodes.indices.toList()
        }


        private fun visit(index: Int, parent: FloatArray) {
            val node = nodes.getOrNull(index) ?: return
            val local = localMatrix(node)
            val world = FloatArray(Mat.SIZE)
            Mat.multiply(parent, local, world)

            node.int("mesh")?.let { meshIndex -> emitMesh(meshIndex, index, world) }
            node.array("children")?.ints()?.forEach { child -> visit(child, world) }
        }

        private fun localMatrix(node: JsonObject): FloatArray {
            node.floats("matrix", 16)?.let { return it }

            val out = FloatArray(Mat.SIZE)
            val t = node.floats("translation", 3) ?: floatArrayOf(0f, 0f, 0f)
            val r = node.floats("rotation", 4) ?: floatArrayOf(0f, 0f, 0f, 1f)
            val s = node.floats("scale", 3) ?: floatArrayOf(1f, 1f, 1f)
            val (x, y, z, w) = listOf(r[0], r[1], r[2], r[3])
            val m00 = 1f - 2f * (y * y + z * z)
            val m01 = 2f * (x * y - z * w)
            val m02 = 2f * (x * z + y * w)
            val m10 = 2f * (x * y + z * w)
            val m11 = 1f - 2f * (x * x + z * z)
            val m12 = 2f * (y * z - x * w)
            val m20 = 2f * (x * z - y * w)
            val m21 = 2f * (y * z + x * w)
            val m22 = 1f - 2f * (x * x + y * y)

            out[0] = m00 * s[0]; out[1] = m10 * s[0]; out[2] = m20 * s[0]; out[3] = 0f
            out[4] = m01 * s[1]; out[5] = m11 * s[1]; out[6] = m21 * s[1]; out[7] = 0f
            out[8] = m02 * s[2]; out[9] = m12 * s[2]; out[10] = m22 * s[2]; out[11] = 0f
            out[12] = t[0]; out[13] = t[1]; out[14] = t[2]; out[15] = 1f
            return out
        }

        private fun emitMesh(meshIndex: Int, nodeIndex: Int, world: FloatArray) {
            val mesh = gltfMeshes.getOrNull(meshIndex) ?: return
            val primitives = mesh.array("primitives")?.objects().orEmpty()
            if (primitives.isEmpty()) return

            val normalMatrix = inverseTranspose3x3(world)
            val positions = ArrayList<Vec3>()
            val normals = ArrayList<Vec3>()
            val uvs = ArrayList<Vec2f>()
            val indices = ArrayList<Int>()
            val slices = ArrayList<MeshPrimitive>()

            primitives.forEach { primitive ->
                val mode = primitive.int("mode", MODE_TRIANGLES)
                if (mode != MODE_TRIANGLES) {
                    println("glTF $namespace: skipping a primitive with mode $mode; only triangles are drawn")
                    return@forEach
                }
                val attributes = primitive.obj("attributes") ?: return@forEach
                val positionAccessor = attributes.int("POSITION") ?: return@forEach
                val rawPositions = readFloats(positionAccessor, 3)
                val vertexCount = rawPositions.size / 3
                if (vertexCount == 0) return@forEach

                val rawNormals = attributes.int("NORMAL")?.let { readFloats(it, 3) }
                val rawUvs = attributes.int("TEXCOORD_0")?.let { readFloats(it, 2) }

                val base = positions.size
                for (v in 0 until vertexCount) {
                    val px = rawPositions[v * 3]
                    val py = rawPositions[v * 3 + 1]
                    val pz = rawPositions[v * 3 + 2]
                    positions += Vec3(
                        world[0] * px + world[4] * py + world[8] * pz + world[12],
                        world[1] * px + world[5] * py + world[9] * pz + world[13],
                        world[2] * px + world[6] * py + world[10] * pz + world[14],
                    )

                    val nx = rawNormals?.getOrNull(v * 3) ?: 0f
                    val ny = rawNormals?.getOrNull(v * 3 + 1) ?: 1f
                    val nz = rawNormals?.getOrNull(v * 3 + 2) ?: 0f
                    normals += Vec3(
                        normalMatrix[0] * nx + normalMatrix[3] * ny + normalMatrix[6] * nz,
                        normalMatrix[1] * nx + normalMatrix[4] * ny + normalMatrix[7] * nz,
                        normalMatrix[2] * nx + normalMatrix[5] * ny + normalMatrix[8] * nz,
                    ).normalized()
                    uvs += Vec2f(rawUvs?.getOrNull(v * 2) ?: 0f, rawUvs?.getOrNull(v * 2 + 1) ?: 0f)
                }

                val first = indices.size
                val sourceIndices = primitive.int("indices")?.let { readIndices(it) }
                if (sourceIndices != null) {
                    sourceIndices.forEach { indices += base + it }
                } else {
                    for (v in 0 until vertexCount) indices += base + v
                }

                val materialId = primitive.int("material")
                    ?.let { materialId(it) }
                    ?: defaultMaterialId
                slices += MeshPrimitive(first, indices.size - first, materialId)
            }

            if (slices.isEmpty() || positions.isEmpty()) return

            val id = "$namespace/mesh/$nodeIndex-$meshIndex"
            meshes[id] = IndexedMesh(
                id = id,
                positions = positions,
                normals = normals,
                textureCoordinates = uvs,
                indices = indices,
                primitives = slices,
                bounds = boundsOf(positions),
            )
            parts += ModelPart(meshId = id)
        }

        private fun materialId(index: Int) = "$namespace/material/$index"

        private fun material(index: Int) {
            val source = gltfMaterials.getOrNull(index) ?: return
            val pbr = source.obj("pbrMetallicRoughness")
            val factor = pbr?.floats("baseColorFactor", 4) ?: floatArrayOf(1f, 1f, 1f, 1f)
            val emissiveFactor = source.floats("emissiveFactor", 3) ?: floatArrayOf(0f, 0f, 0f)
            val emissiveStrength = maxOf(emissiveFactor[0], emissiveFactor[1], emissiveFactor[2])

            val albedo = pbr?.obj("baseColorTexture")?.int("index")?.let(::texture)
            val blend = when (source.string("alphaMode")) {
                "BLEND", "MASK" -> GpuBlendMode.Alpha
                else -> if (factor[3] < 0.995f) GpuBlendMode.Alpha else GpuBlendMode.Opaque
            }

            materials[materialId(index)] = GpuMaterial(
                id = materialId(index),
                baseColor = packColor(factor[0], factor[1], factor[2], factor[3]),
                albedo = albedo,
                roughness = pbr?.float("roughnessFactor", 1f) ?: 1f,
                metallic = pbr?.float("metallicFactor", 1f) ?: 1f,
                emissive = if (emissiveStrength > 0f) {
                    packColor(
                        emissiveFactor[0] / emissiveStrength,
                        emissiveFactor[1] / emissiveStrength,
                        emissiveFactor[2] / emissiveStrength,
                        1f,
                    )
                } else {
                    PackedColor.Black
                },
                emissiveStrength = emissiveStrength,
                blendMode = blend,
                depthWrite = blend == GpuBlendMode.Opaque,
                cullMode = if (source.boolean("doubleSided", false)) GpuCullMode.None else GpuCullMode.Back,
            )
        }


        private fun texture(index: Int): String? {
            val id = "$namespace/texture/$index"
            if (id in images) return id
            val source = gltfTextures.getOrNull(index)?.int("source") ?: return null
            val image = gltfImages.getOrNull(source) ?: return null

            val encoded = image.int("bufferView")?.let(::viewBytes)
                ?: image.string("uri")?.let(::dataUriBytes)
                ?: run {
                    println("glTF $namespace: image $source is an external file, which is not loaded")
                    return null
                }

            images[id] = EncodedImage(encoded, image.string("mimeType"))
            return id
        }

        private fun bufferBytes(index: Int): ByteArray {
            val buffer = buffers.getOrNull(index) ?: throw GltfException("$namespace: no buffer $index")
            val uri = buffer.string("uri") ?: return binaryChunk
            return dataUriBytes(uri) ?: throw GltfException("$namespace: buffer $index is an external file")
        }

        private fun viewBytes(index: Int): ByteArray {
            val view = bufferViews.getOrNull(index) ?: throw GltfException("$namespace: no bufferView $index")
            val bytes = bufferBytes(view.int("buffer", 0))
            val offset = view.int("byteOffset", 0)
            val length = view.int("byteLength", 0)
            if (offset + length > bytes.size) throw GltfException("$namespace: bufferView $index runs past its buffer")
            return bytes.copyOfRange(offset, offset + length)
        }


        private fun readFloats(index: Int, components: Int): FloatArray {
            val accessor = accessors.getOrNull(index) ?: throw GltfException("$namespace: no accessor $index")
            val count = accessor.int("count", 0)
            val declared = componentsOf(accessor.string("type"))
            if (declared != components) {
                throw GltfException("$namespace: accessor $index is ${accessor.string("type")}, expected $components components")
            }
            val componentType = accessor.int("componentType", COMPONENT_FLOAT)
            val normalized = accessor.boolean("normalized", false)
            val viewIndex = accessor.int("bufferView")
                ?: return FloatArray(count * components) // sparse-only accessors read as zero
            val view = bufferViews.getOrNull(viewIndex) ?: throw GltfException("$namespace: no bufferView $viewIndex")
            val bytes = viewBytes(viewIndex)
            val size = componentSize(componentType)
            val stride = view.int("byteStride", 0).takeIf { it > 0 } ?: (size * components)
            val start = accessor.int("byteOffset", 0)

            val out = FloatArray(count * components)
            for (element in 0 until count) {
                val base = start + element * stride
                for (component in 0 until components) {
                    val at = base + component * size
                    if (at + size > bytes.size) throw GltfException("$namespace: accessor $index reads past its view")
                    out[element * components + component] = readComponent(bytes, at, componentType, normalized)
                }
            }
            return out
        }

        private fun readIndices(index: Int): IntArray {
            val accessor = accessors.getOrNull(index) ?: throw GltfException("$namespace: no accessor $index")
            val count = accessor.int("count", 0)
            val componentType = accessor.int("componentType", COMPONENT_UNSIGNED_SHORT)
            val viewIndex = accessor.int("bufferView") ?: return IntArray(count)
            val bytes = viewBytes(viewIndex)
            val size = componentSize(componentType)
            val start = accessor.int("byteOffset", 0)

            return IntArray(count) { element ->
                val at = start + element * size
                if (at + size > bytes.size) throw GltfException("$namespace: index accessor $index reads past its view")
                when (componentType) {
                    COMPONENT_UNSIGNED_BYTE -> bytes[at].toInt() and 0xFF
                    COMPONENT_UNSIGNED_SHORT -> bytes.readShort(at)
                    COMPONENT_UNSIGNED_INT -> bytes.readInt(at)
                    else -> throw GltfException("$namespace: indices cannot be component type $componentType")
                }
            }
        }

        private fun readComponent(bytes: ByteArray, at: Int, componentType: Int, normalized: Boolean): Float =
            when (componentType) {
                COMPONENT_FLOAT -> Float.fromBits(bytes.readInt(at))
                COMPONENT_UNSIGNED_BYTE -> (bytes[at].toInt() and 0xFF).let { if (normalized) it / 255f else it.toFloat() }
                COMPONENT_BYTE -> bytes[at].toInt().let { if (normalized) maxOf(it / 127f, -1f) else it.toFloat() }
                COMPONENT_UNSIGNED_SHORT -> bytes.readShort(at).let { if (normalized) it / 65535f else it.toFloat() }
                COMPONENT_SHORT -> {
                    val signed = bytes.readShort(at).toShort().toInt()
                    if (normalized) maxOf(signed / 32767f, -1f) else signed.toFloat()
                }
                COMPONENT_UNSIGNED_INT -> bytes.readInt(at).toFloat()
                else -> throw GltfException("$namespace: unsupported component type $componentType")
            }

        private fun componentSize(componentType: Int): Int = when (componentType) {
            COMPONENT_BYTE, COMPONENT_UNSIGNED_BYTE -> 1
            COMPONENT_SHORT, COMPONENT_UNSIGNED_SHORT -> 2
            COMPONENT_UNSIGNED_INT, COMPONENT_FLOAT -> 4
            else -> throw GltfException("$namespace: unsupported component type $componentType")
        }

        private fun componentsOf(type: String?): Int = when (type) {
            "SCALAR" -> 1
            "VEC2" -> 2
            "VEC3" -> 3
            "VEC4" -> 4
            "MAT4" -> 16
            else -> throw GltfException("$namespace: unsupported accessor type '$type'")
        }

        private fun dataUriBytes(uri: String): ByteArray? {
            if (!uri.startsWith("data:")) return null
            val comma = uri.indexOf(',')
            if (comma < 0) return null
            if (!uri.substring(0, comma).endsWith(";base64")) return null
            return Base64.decode(uri.substring(comma + 1))
        }
    }
}

private fun ByteArray.readInt(at: Int): Int =
    (this[at].toInt() and 0xFF) or
        ((this[at + 1].toInt() and 0xFF) shl 8) or
        ((this[at + 2].toInt() and 0xFF) shl 16) or
        ((this[at + 3].toInt() and 0xFF) shl 24)

private fun ByteArray.readShort(at: Int): Int =
    (this[at].toInt() and 0xFF) or ((this[at + 1].toInt() and 0xFF) shl 8)

private fun packColor(red: Float, green: Float, blue: Float, alpha: Float): PackedColor {
    fun channel(value: Float) = (value.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
    return PackedColor(
        (channel(alpha) shl 24) or (channel(red) shl 16) or (channel(green) shl 8) or channel(blue),
    )
}

private fun boundsOf(positions: List<Vec3>): Bounds3 {
    var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var minZ = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
    positions.forEach {
        if (it.x < minX) minX = it.x; if (it.x > maxX) maxX = it.x
        if (it.y < minY) minY = it.y; if (it.y > maxY) maxY = it.y
        if (it.z < minZ) minZ = it.z; if (it.z > maxZ) maxZ = it.z
    }
    return Bounds3(Vec3(minX, minY, minZ), Vec3(maxX, maxY, maxZ))
}

private fun inverseTranspose3x3(m: FloatArray): FloatArray {
    val a = m[0]; val b = m[4]; val c = m[8]
    val d = m[1]; val e = m[5]; val f = m[9]
    val g = m[2]; val h = m[6]; val i = m[10]

    val cofactor0 = e * i - f * h
    val cofactor1 = f * g - d * i
    val cofactor2 = d * h - e * g
    val determinant = a * cofactor0 + b * cofactor1 + c * cofactor2
    if (determinant > -1e-12f && determinant < 1e-12f) {
        return floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
    }
    val inverse = 1f / determinant
    return floatArrayOf(
        cofactor0 * inverse, cofactor1 * inverse, cofactor2 * inverse,
        (c * h - b * i) * inverse, (a * i - c * g) * inverse, (b * g - a * h) * inverse,
        (b * f - c * e) * inverse, (c * d - a * f) * inverse, (a * e - b * d) * inverse,
    )
}

private object Base64 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private val reverse = IntArray(128) { -1 }.also { table ->
        ALPHABET.forEachIndexed { index, character -> table[character.code] = index }
    }

    fun decode(text: String): ByteArray {
        val out = ArrayList<Byte>(text.length * 3 / 4)
        var accumulator = 0
        var bits = 0
        text.forEach { character ->
            if (character == '=') return@forEach
            val value = if (character.code < 128) reverse[character.code] else -1
            if (value < 0) return@forEach // whitespace and newlines are legal inside a data URI
            accumulator = (accumulator shl 6) or value
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out += ((accumulator shr bits) and 0xFF).toByte()
            }
        }
        return out.toByteArray()
    }
}

private operator fun <T> List<T>.component4(): T = this[3]
