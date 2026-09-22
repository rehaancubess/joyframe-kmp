package io.github.rehaancubess.joyframe.render.gltf

import io.github.rehaancubess.joyframe.render.gpu.GpuBlendMode
import io.github.rehaancubess.joyframe.render.gpu.GpuCullMode
import io.github.rehaancubess.joyframe.render.gpu.GpuSceneAssets
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * End-to-end cover for the glTF reader, built on a `.glb` assembled here rather than a fixture
 * file.
 *
 * Generating the container in the test is what makes the failures legible: when an assertion trips,
 * the exact bytes that caused it are a few lines up rather than in an opaque binary, and a case can
 * be added by editing a JSON string instead of opening Blender.
 */
class GltfLoaderTest {
    private fun encodePng(pixels: IntArray): ByteArray {
        val image = BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(0, 0, 2, 2, pixels, 0, 2)
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }

    // A quad on the XY plane, wound counter-clockwise, with a full 0..1 UV square.
    private val positions = floatArrayOf(
        0f, 0f, 0f,
        1f, 0f, 0f,
        1f, 1f, 0f,
        0f, 1f, 0f,
    )
    private val normals = floatArrayOf(
        0f, 0f, 1f,
        0f, 0f, 1f,
        0f, 0f, 1f,
        0f, 0f, 1f,
    )
    private val uvs = floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)
    private val indices = intArrayOf(0, 1, 2, 0, 2, 3)

    /** 2x2 texture with four distinct, fully opaque colours, so a channel swap cannot hide. */
    private val texturePixels = intArrayOf(
        0xFFFF0000.toInt(), 0xFF00FF00.toInt(),
        0xFF0000FF.toInt(), 0xFFFFFFFF.toInt(),
    )

    @Test
    fun `reads geometry, material and texture out of a glb`() {
        val model = GltfLoader.loadGlb(buildGlb(), namespace = "test")

        assertEquals(1, model.meshes.size, "one node with one mesh should produce one mesh")
        val mesh = model.meshes.values.single()
        assertEquals(4, mesh.positions.size)
        assertEquals(6, mesh.indices.size)
        assertContentEquals(indices.toList(), mesh.indices, "index buffer should survive the load")

        // UVs must come through intact: they are what makes the texture land the right way up.
        assertEquals(4, mesh.textureCoordinates.size)
        assertEquals(0f, mesh.textureCoordinates[0].x)
        assertEquals(1f, mesh.textureCoordinates[2].x)
        assertEquals(1f, mesh.textureCoordinates[2].y)

        val material = model.materials.values.single()
        assertNotNull(material.albedo, "the base colour texture should be resolved")
        assertEquals(GpuCullMode.None, material.cullMode, "doubleSided should disable culling")
        assertEquals(GpuBlendMode.Opaque, material.blendMode)
        assertEquals(0.25f, material.metallic)
        assertEquals(0.75f, material.roughness)

        val texture = model.textures.getValue(material.albedo!!)
        assertEquals(2, texture.width)
        assertEquals(2, texture.height)
        assertEquals(16, texture.rgba.size)
        // Top-left texel is opaque red. Checking the byte order directly is the point: an ARGB/RGBA
        // mix-up here would show up in the game as every imported model being blue instead of red.
        assertEquals(0xFF.toByte(), texture.rgba[0], "red channel")
        assertEquals(0x00.toByte(), texture.rgba[1], "green channel")
        assertEquals(0x00.toByte(), texture.rgba[2], "blue channel")
        assertEquals(0xFF.toByte(), texture.rgba[3], "alpha channel")
    }

    @Test
    fun `bakes the node transform into the vertices`() {
        val model = GltfLoader.loadGlb(buildGlb(), namespace = "test")
        val mesh = model.meshes.values.single()

        // The node translates by (10, 20, 30) and scales by 2, so the unit quad lands here.
        assertEquals(10f, mesh.positions[0].x); assertEquals(20f, mesh.positions[0].y)
        assertEquals(12f, mesh.positions[1].x); assertEquals(20f, mesh.positions[1].y)
        assertEquals(12f, mesh.positions[2].x); assertEquals(22f, mesh.positions[2].y)
        assertEquals(30f, mesh.positions[3].z)

        assertEquals(10f, mesh.bounds.minimum.x)
        assertEquals(22f, mesh.bounds.maximum.y)

        // A uniform scale must leave the normals unit length, not scaled with the geometry.
        mesh.normals.forEach { assertEquals(1f, it.length(), 1e-5f, "normals stay normalised") }
        assertEquals(1f, mesh.normals[0].z, 1e-5f)
    }

    @Test
    fun `merges into a scene without disturbing what is already there`() {
        val model = GltfLoader.loadGlb(buildGlb(), namespace = "test")
        val empty = GpuSceneAssets(
            cacheKey = "before",
            meshes = emptyMap(),
            materials = emptyMap(),
            models = emptyMap(),
        )

        val merged = model.mergeInto(empty, cacheKey = "after")

        assertEquals("after", merged.cacheKey)
        assertEquals(1, merged.meshes.size)
        assertEquals(1, merged.textures.size)
        assertTrue("test" in merged.models, "the model should be addressable by its namespace")
        // GpuSceneAssets validates its own references on construction, so reaching here at all
        // proves the loaded material, mesh and texture all point at each other correctly.
    }

    @Test
    fun `rejects a container that is not a glb`() {
        val notAGlb = ByteArray(64) { 0x7F }
        val failure = assertFailsWith<GltfException> { GltfLoader.loadGlb(notAGlb, "test") }
        assertTrue("magic" in failure.message.orEmpty(), "should name the actual problem")
    }

    @Test
    fun `rejects a truncated glb`() {
        val truncated = buildGlb().copyOfRange(0, 40)
        assertFailsWith<GltfException> { GltfLoader.loadGlb(truncated, "test") }
    }

    @Test
    fun `keeps the mesh when an image cannot be decoded`() {
        // A model whose texture is nonsense should still yield its geometry: losing a map is a
        // cosmetic problem, losing the mesh is not.
        val model = GltfLoader.loadGlb(buildGlb(corruptImage = true), namespace = "test")
        assertEquals(1, model.meshes.size)
        assertNull(model.materials.values.single().albedo, "the undecodable texture is dropped")
        assertTrue(model.textures.isEmpty())
    }

    @Test
    fun `json reader rejects malformed input`() {
        assertFailsWith<JsonException> { Json.parse("{\"a\": }") }
        assertFailsWith<JsonException> { Json.parse("[1, 2") }
        assertFailsWith<JsonException> { Json.parse("{} trailing") }
        // Escapes and exponents are both used by real exporters.
        val parsed = Json.parse("""{"n": -1.5e2, "s": "a\"bA"}""") as JsonObject
        assertEquals(-150.0, parsed.double("n"))
        assertEquals("a\"bA", parsed.string("s"))
    }

    // ---------------------------------------------------------------- container assembly

    private fun buildGlb(corruptImage: Boolean = false): ByteArray {
        val png = if (corruptImage) ByteArray(32) { 0x11 } else encodePng(texturePixels)

        val bin = Bytes()
        val positionsAt = bin.mark { positions.forEach(bin::float) }
        val normalsAt = bin.mark { normals.forEach(bin::float) }
        val uvsAt = bin.mark { uvs.forEach(bin::float) }
        val indicesAt = bin.mark { indices.forEach { bin.short(it) } }
        bin.pad(4)
        val imageAt = bin.mark { bin.raw(png) }
        bin.pad(4)

        val json = """
        {
          "asset": {"version": "2.0"},
          "scene": 0,
          "scenes": [{"nodes": [0]}],
          "nodes": [
            {"mesh": 0, "translation": [10, 20, 30], "scale": [2, 2, 2]}
          ],
          "meshes": [
            {"primitives": [
              {"attributes": {"POSITION": 0, "NORMAL": 1, "TEXCOORD_0": 2}, "indices": 3, "material": 0}
            ]}
          ],
          "materials": [
            {
              "doubleSided": true,
              "pbrMetallicRoughness": {
                "baseColorFactor": [1, 1, 1, 1],
                "baseColorTexture": {"index": 0},
                "metallicFactor": 0.25,
                "roughnessFactor": 0.75
              }
            }
          ],
          "textures": [{"source": 0}],
          "images": [{"bufferView": 4, "mimeType": "image/png"}],
          "accessors": [
            {"bufferView": 0, "componentType": 5126, "count": 4, "type": "VEC3"},
            {"bufferView": 1, "componentType": 5126, "count": 4, "type": "VEC3"},
            {"bufferView": 2, "componentType": 5126, "count": 4, "type": "VEC2"},
            {"bufferView": 3, "componentType": 5123, "count": 6, "type": "SCALAR"}
          ],
          "bufferViews": [
            {"buffer": 0, "byteOffset": ${positionsAt.offset}, "byteLength": ${positionsAt.length}},
            {"buffer": 0, "byteOffset": ${normalsAt.offset}, "byteLength": ${normalsAt.length}},
            {"buffer": 0, "byteOffset": ${uvsAt.offset}, "byteLength": ${uvsAt.length}},
            {"buffer": 0, "byteOffset": ${indicesAt.offset}, "byteLength": ${indicesAt.length}},
            {"buffer": 0, "byteOffset": ${imageAt.offset}, "byteLength": ${imageAt.length}}
          ],
          "buffers": [{"byteLength": ${bin.size}}]
        }
        """.trimIndent()

        val jsonBytes = json.encodeToByteArray()
        val out = Bytes()
        out.int(0x46546C67) // "glTF"
        out.int(2)
        val lengthAt = out.size
        out.int(0) // total length, patched below

        out.int(jsonBytes.size + jsonBytes.padding(4))
        out.int(0x4E4F534A) // "JSON"
        out.raw(jsonBytes)
        // The JSON chunk pads with spaces, the binary chunk with zeroes; both are spec-required.
        repeat(jsonBytes.padding(4)) { out.byte(0x20) }

        out.int(bin.size)
        out.int(0x004E4942) // "BIN"
        out.raw(bin.toByteArray())

        val bytes = out.toByteArray()
        writeInt(bytes, lengthAt, bytes.size)
        return bytes
    }

    private fun Int.padding(alignment: Int) = (alignment - this % alignment) % alignment
    private fun ByteArray.padding(alignment: Int) = size.padding(alignment)

    private fun writeInt(target: ByteArray, at: Int, value: Int) {
        target[at] = (value and 0xFF).toByte()
        target[at + 1] = ((value ushr 8) and 0xFF).toByte()
        target[at + 2] = ((value ushr 16) and 0xFF).toByte()
        target[at + 3] = ((value ushr 24) and 0xFF).toByte()
    }

    private class Span(val offset: Int, val length: Int)

    /** Little-endian byte builder, matching glTF's byte order. */
    private class Bytes {
        private val buffer = ArrayList<Byte>()
        val size: Int get() = buffer.size

        fun byte(value: Int) { buffer += (value and 0xFF).toByte() }
        fun short(value: Int) { byte(value); byte(value ushr 8) }
        fun int(value: Int) { short(value); short(value ushr 16) }
        fun float(value: Float) = int(value.toRawBits())
        fun raw(bytes: ByteArray) { bytes.forEach { buffer += it } }
        fun pad(alignment: Int) { while (buffer.size % alignment != 0) byte(0) }

        /** Records where a block landed, so the JSON can reference it without hand-counting. */
        fun mark(write: () -> Unit): Span {
            val start = buffer.size
            write()
            return Span(start, buffer.size - start)
        }

        fun toByteArray() = buffer.toByteArray()
    }
}
