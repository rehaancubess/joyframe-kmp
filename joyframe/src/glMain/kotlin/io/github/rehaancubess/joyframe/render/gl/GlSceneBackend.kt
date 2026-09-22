// SPDX-License-Identifier: Apache-2.0
package io.github.rehaancubess.joyframe.render.gl

import io.github.rehaancubess.joyframe.render.gpu.GpuBlendMode
import io.github.rehaancubess.joyframe.render.gpu.GpuCullMode
import io.github.rehaancubess.joyframe.render.gpu.GpuMaterial
import io.github.rehaancubess.joyframe.render.gpu.GpuSceneAssets
import io.github.rehaancubess.joyframe.render.gpu.GpuSceneFrame
import io.github.rehaancubess.joyframe.render.gpu.IndexedMesh
import io.github.rehaancubess.joyframe.render.gpu.InstanceKind
import io.github.rehaancubess.joyframe.render.gpu.PackedColor
import io.github.rehaancubess.joyframe.render.gpu.TextureData
import io.github.rehaancubess.joyframe.render.gpu.Transform3D
import io.github.rehaancubess.joyframe.render.math.DepthRange
import io.github.rehaancubess.joyframe.render.math.Mat
import io.github.rehaancubess.joyframe.render.math.Vec3
import io.github.rehaancubess.joyframe.render.water.VortexConfig
import io.github.rehaancubess.joyframe.render.water.VortexSurge
import io.github.rehaancubess.joyframe.render.water.WaterConfig
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sqrt
import kotlin.math.tan

private const val FLOATS_PER_VERTEX = 8
private const val VERTEX_STRIDE_BYTES = FLOATS_PER_VERTEX * 4

private const val SHADOW_MAP_SIZE = 2048

private const val FRUSTUM_PLANES = 6

private const val UNIT_SHADOW = 0
private const val UNIT_ALBEDO = 1

private const val SHADOW_HALF_EXTENT = 1_250f
private const val SHADOW_LIGHT_DISTANCE = 2_400f

internal class GlMesh(
    val vertexArray: Int,
    val vertexBuffer: Int,
    val indexBuffer: Int,
    val primitives: List<GlPrimitive>,

    val boundsCenter: Vec3,
    val boundsRadius: Float,
)

internal class GlPrimitive(
    val firstIndex: Int,
    val indexCount: Int,
    val materialId: String,
)

internal class GlAssets(
    val cacheKey: String,
    val meshes: Map<String, GlMesh>,
    val textures: Map<String, Int>,
)

internal class GlSceneBackend private constructor(
    private val gl: GlApi,
    private val scene: SceneProgram,
    private val shadow: ShadowProgram,
    private val sky: SkyProgram,
    private val shadowFramebuffer: Int,
    private val shadowTexture: Int,
    private val whiteTexture: Int,
) {
    private val viewMatrix = FloatArray(Mat.SIZE)
    private val projectionMatrix = FloatArray(Mat.SIZE)
    private val viewProjectionMatrix = FloatArray(Mat.SIZE)
    private val lightViewMatrix = FloatArray(Mat.SIZE)
    private val lightProjectionMatrix = FloatArray(Mat.SIZE)
    private val lightViewProjectionMatrix = FloatArray(Mat.SIZE)
    private val instanceModel = FloatArray(Mat.SIZE)
    private val instanceNormal = FloatArray(Mat.SIZE)
    private val partModel = FloatArray(Mat.SIZE)
    private val partNormal = FloatArray(Mat.SIZE)
    private val localModel = FloatArray(Mat.SIZE)
    private val localNormal = FloatArray(Mat.SIZE)

    private val frame = FrameState()


    private val swellScratch = FloatArray(WaterConfig.SWELL_TRAINS * WaterConfig.FLOATS_PER_TRAIN)
    private val drawPool = ArrayList<DrawItem>()
    private var pooled = 0
    private val opaqueDraws = ArrayList<DrawItem>()
    private val blendedDraws = ArrayList<DrawItem>()
    private val farToNear = Comparator<DrawItem> { a, b -> b.sortDepth.compareTo(a.sortDepth) }

    private var assets: GlAssets? = null
    private var boundVertexArray = -1
    private var currentCull = Int.MIN_VALUE
    private var currentBlend: GpuBlendMode? = null
    private var boundAlbedo = -1


    fun assetsFor(source: GpuSceneAssets): GlAssets {
        assets?.let { if (it.cacheKey == source.cacheKey) return it }
        assets?.let(::release)
        val uploaded = GlAssets(
            cacheKey = source.cacheKey,
            meshes = source.meshes.mapValues { (_, mesh) -> upload(mesh) },
            textures = source.textures.mapValues { (_, texture) -> upload(texture) },
        )
        assets = uploaded
        return uploaded
    }

    private fun upload(mesh: IndexedMesh): GlMesh {
        val vertexData = FloatArray(mesh.positions.size * FLOATS_PER_VERTEX)
        for (i in mesh.positions.indices) {
            val position = mesh.positions[i]
            val normal = mesh.normals[i]
            val uv = mesh.textureCoordinates.getOrNull(i)
            val base = i * FLOATS_PER_VERTEX
            vertexData[base] = position.x
            vertexData[base + 1] = position.y
            vertexData[base + 2] = position.z
            vertexData[base + 3] = normal.x
            vertexData[base + 4] = normal.y
            vertexData[base + 5] = normal.z
            vertexData[base + 6] = uv?.x ?: 0f
            vertexData[base + 7] = uv?.y ?: 0f
        }
        val indexData = IntArray(mesh.indices.size) { mesh.indices[it] }

        val vertexArray = gl.genVertexArray()
        gl.bindVertexArray(vertexArray)

        val vertexBuffer = gl.genBuffer()
        gl.bindBuffer(GlConst.ARRAY_BUFFER, vertexBuffer)
        gl.bufferData(GlConst.ARRAY_BUFFER, vertexData, GlConst.STATIC_DRAW)

        val indexBuffer = gl.genBuffer()
        gl.bindBuffer(GlConst.ELEMENT_ARRAY_BUFFER, indexBuffer)
        gl.bufferData(GlConst.ELEMENT_ARRAY_BUFFER, indexData, GlConst.STATIC_DRAW)

        gl.enableVertexAttribArray(GlShaders.ATTRIB_POSITION)
        gl.vertexAttribPointer(GlShaders.ATTRIB_POSITION, 3, GlConst.FLOAT, false, VERTEX_STRIDE_BYTES, 0)
        gl.enableVertexAttribArray(GlShaders.ATTRIB_NORMAL)
        gl.vertexAttribPointer(GlShaders.ATTRIB_NORMAL, 3, GlConst.FLOAT, false, VERTEX_STRIDE_BYTES, 12)
        gl.enableVertexAttribArray(GlShaders.ATTRIB_TEXCOORD)
        gl.vertexAttribPointer(GlShaders.ATTRIB_TEXCOORD, 2, GlConst.FLOAT, false, VERTEX_STRIDE_BYTES, 24)

        gl.bindVertexArray(0)
        boundVertexArray = 0

        return GlMesh(
            vertexArray = vertexArray,
            vertexBuffer = vertexBuffer,
            indexBuffer = indexBuffer,
            primitives = mesh.primitives.map { GlPrimitive(it.firstIndex, it.indexCount, it.materialId) },
            boundsCenter = (mesh.bounds.minimum + mesh.bounds.maximum) * 0.5f,
            boundsRadius = (mesh.bounds.maximum - mesh.bounds.minimum).length() * 0.5f,
        )
    }

    private fun upload(texture: TextureData): Int {
        val id = gl.genTexture()
        gl.bindTexture(GlConst.TEXTURE_2D, id)
        gl.texImage2DRgba(texture.width, texture.height, texture.rgba)
        gl.texParameteri(GlConst.TEXTURE_2D, GlConst.TEXTURE_MIN_FILTER, GlConst.LINEAR_MIPMAP_LINEAR)
        gl.texParameteri(GlConst.TEXTURE_2D, GlConst.TEXTURE_MAG_FILTER, GlConst.LINEAR)
        gl.texParameteri(GlConst.TEXTURE_2D, GlConst.TEXTURE_WRAP_S, GlConst.REPEAT)
        gl.texParameteri(GlConst.TEXTURE_2D, GlConst.TEXTURE_WRAP_T, GlConst.REPEAT)
        gl.generateMipmap(GlConst.TEXTURE_2D)
        boundAlbedo = -1
        return id
    }

    private fun release(stale: GlAssets) {
        stale.meshes.values.forEach { mesh ->
            gl.deleteVertexArray(mesh.vertexArray)
            gl.deleteBuffer(mesh.vertexBuffer)
            gl.deleteBuffer(mesh.indexBuffer)
        }
        stale.textures.values.forEach(gl::deleteTexture)
        boundAlbedo = -1
    }


    fun render(source: GpuSceneFrame, widthPixels: Int, heightPixels: Int, targetFramebuffer: Int = 0, background: (() -> Unit)? = null) {
        val width = widthPixels.coerceAtLeast(1)
        val height = heightPixels.coerceAtLeast(1)
        val uploaded = assetsFor(source.assets)
        buildFrameState(source, width.toFloat() / height.toFloat())
        collectDraws(source, uploaded)
        boundVertexArray = -1
        currentCull = Int.MIN_VALUE
        currentBlend = null
        boundAlbedo = -1
        gl.frontFace(GlConst.CCW)

        renderShadowPass()
        renderScenePass(source, width, height, targetFramebuffer, background)
    }

    private fun buildFrameState(source: GpuSceneFrame, aspect: Float) {
        val camera = source.camera
        val near = camera.nearPlane.coerceAtLeast(1f)
        val far = camera.farPlane.coerceAtLeast(near + 1f)
        Mat.lookAt(camera.eye, camera.target, camera.up, viewMatrix)
        Mat.perspective(
            camera.verticalFovDegrees,
            aspect,
            near,
            far,
            DepthRange.NegativeOneToOne,
            projectionMatrix,
        )
        Mat.multiply(projectionMatrix, viewMatrix, viewProjectionMatrix)

        val key = source.lights.firstOrNull()
        val fill = source.lights.getOrNull(1)
        val shadowCaster = source.lights.firstOrNull { it.castsShadow }
        buildLightMatrices(shadowCaster?.direction ?: Vec3(0f, -1f, 0f), camera.target)
        Mat.multiply(lightProjectionMatrix, lightViewMatrix, lightViewProjectionMatrix)

        frame.cameraPosition = camera.eye
        frame.keyDirection = key?.direction ?: Vec3(0f, -1f, 0f)
        frame.setKeyColor(key?.color ?: PackedColor.White, key?.intensity ?: 1f)
        frame.fillDirection = fill?.direction ?: Vec3(0f, 1f, 0f)
        frame.setFillColor(fill?.color ?: PackedColor.Black, fill?.intensity ?: 0f)

        val environment = source.environment
        frame.setAmbient(environment.ambientColor, environment.ambientIntensity)
        frame.setFog(environment.fogColor, environment.fogStart, environment.fogEnd)
        frame.shadowStrength = if (shadowCaster != null) SHADOW_STRENGTH else 0f

        val forward = (camera.target - camera.eye).normalized()
        var right = forward.cross(camera.up)
        right = if (right.length() > 1e-4f) right.normalized() else Vec3(1f, 0f, 0f)
        frame.skyForward = forward
        frame.skyRight = right
        frame.skyUp = right.cross(forward).normalized()
        frame.tanHalfFov = tan(camera.verticalFovDegrees * 0.5f * (PI_F / 180f))
        frame.aspect = aspect
        frame.timeSeconds = source.renderTimeSeconds
    }


    private fun buildLightMatrices(direction: Vec3, focus: Vec3) {
        val forward = direction.normalized()
        val up = if (abs(forward.y) > 0.985f) Vec3(0f, 0f, 1f) else Vec3.UP
        val eye = focus - forward * SHADOW_LIGHT_DISTANCE
        Mat.lookAt(eye, focus, up, lightViewMatrix)

        val texelWorldSize = 2f * SHADOW_HALF_EXTENT / SHADOW_MAP_SIZE.toFloat()
        val focusX = lightViewMatrix[0] * focus.x + lightViewMatrix[4] * focus.y +
            lightViewMatrix[8] * focus.z + lightViewMatrix[12]
        val focusY = lightViewMatrix[1] * focus.x + lightViewMatrix[5] * focus.y +
            lightViewMatrix[9] * focus.z + lightViewMatrix[13]
        val offsetX = focusX - floor(focusX / texelWorldSize + 0.5f) * texelWorldSize
        val offsetY = focusY - floor(focusY / texelWorldSize + 0.5f) * texelWorldSize

        Mat.orthographic(
            halfWidth = SHADOW_HALF_EXTENT,
            halfHeight = SHADOW_HALF_EXTENT,
            near = 1f,
            far = SHADOW_LIGHT_DISTANCE * 2f,
            depthRange = DepthRange.NegativeOneToOne,
            out = lightProjectionMatrix,
        )
        lightProjectionMatrix[12] = -offsetX / SHADOW_HALF_EXTENT
        lightProjectionMatrix[13] = -offsetY / SHADOW_HALF_EXTENT
    }


    private val frustumPlanes = FloatArray(FRUSTUM_PLANES * 4)

    private fun extractFrustum(m: FloatArray) {
        fun row(index: Int, out: Int, sign: Float) {
            frustumPlanes[out] = m[3] + sign * m[index]
            frustumPlanes[out + 1] = m[7] + sign * m[4 + index]
            frustumPlanes[out + 2] = m[11] + sign * m[8 + index]
            frustumPlanes[out + 3] = m[15] + sign * m[12 + index]
        }
        row(0, 0, 1f)    // left
        row(0, 4, -1f)   // right
        row(1, 8, 1f)    // bottom
        row(1, 12, -1f)  // top
        row(2, 16, 1f)   // near, for the -1..1 depth range this backend projects into
        row(2, 20, -1f)  // far
        for (plane in 0 until FRUSTUM_PLANES) {
            val base = plane * 4
            val a = frustumPlanes[base]
            val b = frustumPlanes[base + 1]
            val c = frustumPlanes[base + 2]
            val inverse = 1f / sqrt(a * a + b * b + c * c).coerceAtLeast(1e-6f)
            frustumPlanes[base] = a * inverse
            frustumPlanes[base + 1] = b * inverse
            frustumPlanes[base + 2] = c * inverse
            frustumPlanes[base + 3] *= inverse
        }
    }


    private fun insideFrustum(mesh: GlMesh, model: FloatArray): Boolean {
        val c = mesh.boundsCenter
        val x = model[0] * c.x + model[4] * c.y + model[8] * c.z + model[12]
        val y = model[1] * c.x + model[5] * c.y + model[9] * c.z + model[13]
        val z = model[2] * c.x + model[6] * c.y + model[10] * c.z + model[14]
        val sx = model[0] * model[0] + model[1] * model[1] + model[2] * model[2]
        val sy = model[4] * model[4] + model[5] * model[5] + model[6] * model[6]
        val sz = model[8] * model[8] + model[9] * model[9] + model[10] * model[10]
        val radius = mesh.boundsRadius * sqrt(maxOf(sx, sy, sz))
        for (plane in 0 until FRUSTUM_PLANES) {
            val base = plane * 4
            val distance = frustumPlanes[base] * x + frustumPlanes[base + 1] * y +
                frustumPlanes[base + 2] * z + frustumPlanes[base + 3]
            if (distance < -radius) return false
        }
        return true
    }

    private fun obtain(): DrawItem {
        if (pooled == drawPool.size) drawPool.add(DrawItem())
        return drawPool[pooled++]
    }

    private fun collectDraws(source: GpuSceneFrame, uploaded: GlAssets) {
        extractFrustum(viewProjectionMatrix)
        pooled = 0
        opaqueDraws.clear()
        blendedDraws.clear()

        val eye = source.camera.eye
        source.instances.forEach { instance ->
            if (!instance.visible) return@forEach
            val model = source.assets.models[instance.modelId] ?: return@forEach
            val hitFlash = instance.hitFlash.coerceIn(0f, 1f)
            val opacity = instance.opacity.coerceIn(0f, 1f)
            val foamAge = instance.foamAge.coerceIn(0f, 1f)
            val worldTransform = instance.interpolatedTransform()
            Mat.compose(worldTransform, instanceModel, instanceNormal)

            val toEye = worldTransform.translation - eye
            val sortDepth = toEye.dot(toEye)
            val castsShadow = instance.kind != InstanceKind.Effect

            model.parts.forEach parts@{ part ->
                val mesh = uploaded.meshes[part.meshId] ?: return@parts
                if (part.localTransform == IDENTITY_TRANSFORM) {
                    instanceModel.copyInto(partModel)
                    instanceNormal.copyInto(partNormal)
                } else {
                    Mat.compose(part.localTransform, localModel, localNormal)
                    Mat.multiply(instanceModel, localModel, partModel)
                    Mat.multiply(instanceNormal, localNormal, partNormal)
                }
                val inView = instance.kind == InstanceKind.Dynamic || insideFrustum(mesh, partModel)

                mesh.primitives.forEach primitives@{ primitive ->
                    if (primitive.indexCount == 0) return@primitives
                    val materialId = part.materialSlot?.let(instance.materials::get) ?: primitive.materialId
                    val material = source.assets.materials[materialId] ?: return@primitives
                    val item = obtain()
                    val albedo = material.albedo?.let(uploaded.textures::get) ?: 0
                    item.fill(mesh, primitive, material, albedo, hitFlash, opacity, castsShadow, sortDepth)
                    item.foamAge = foamAge
                    item.inView = inView
                    partModel.copyInto(item.model)
                    partNormal.copyInto(item.normal)
                    if (item.blendMode == GpuBlendMode.Opaque) opaqueDraws.add(item) else blendedDraws.add(item)
                }
            }
        }
        if (blendedDraws.size > 1) blendedDraws.sortWith(farToNear)
    }

    private fun renderShadowPass() {
        if (frame.shadowStrength <= 0.001f) return

        gl.bindFramebuffer(shadowFramebuffer)
        gl.viewport(0, 0, SHADOW_MAP_SIZE, SHADOW_MAP_SIZE)
        gl.enable(GlConst.DEPTH_TEST)
        gl.depthFunc(GlConst.LESS)
        gl.depthMask(true)
        gl.disable(GlConst.BLEND)
        gl.clear(GlConst.DEPTH_BUFFER_BIT)
        gl.enable(GlConst.POLYGON_OFFSET_FILL)
        gl.polygonOffset(SHADOW_SLOPE_BIAS, 0f)

        gl.useProgram(shadow.program)
        gl.uniformMatrix4(shadow.lightViewProjection, lightViewProjectionMatrix)
        opaqueDraws.forEach { item ->
            if (!item.castsShadow) return@forEach
            applyCull(if (item.cullMode == GpuCullMode.None) GpuCullMode.None else GpuCullMode.Back)
            bindMesh(item.mesh ?: return@forEach)
            gl.uniformMatrix4(shadow.model, item.model)
            gl.drawElements(GlConst.TRIANGLES, item.indexCount, GlConst.UNSIGNED_INT, item.firstIndex * 4)
        }

        gl.disable(GlConst.POLYGON_OFFSET_FILL)
    }

    private fun renderScenePass(source: GpuSceneFrame, width: Int, height: Int, targetFramebuffer: Int, background: (() -> Unit)?) {
        gl.bindFramebuffer(targetFramebuffer)
        gl.viewport(0, 0, width, height)

        val clear = source.environment.clearColor
        gl.clearColor(clear.red, clear.green, clear.blue, 1f)
        gl.depthMask(true)
        gl.clear(GlConst.COLOR_BUFFER_BIT or GlConst.DEPTH_BUFFER_BIT)

        if (background != null) {
            background()
            boundVertexArray = -1
            currentCull = Int.MIN_VALUE
            currentBlend = null
            boundAlbedo = -1
        } else {
        gl.disable(GlConst.DEPTH_TEST)
        gl.depthMask(false)
        gl.disable(GlConst.BLEND)
        applyCull(GpuCullMode.None)
        gl.useProgram(sky.program)
        gl.uniform4(sky.forward, frame.skyForward.x, frame.skyForward.y, frame.skyForward.z, frame.tanHalfFov)
        gl.uniform4(sky.right, frame.skyRight.x, frame.skyRight.y, frame.skyRight.z, frame.aspect)
        gl.uniform4(sky.up, frame.skyUp.x, frame.skyUp.y, frame.skyUp.z, frame.timeSeconds)
        gl.uniform4(sky.zenithColor, SKY_ZENITH.red, SKY_ZENITH.green, SKY_ZENITH.blue, 1f)
        gl.uniform4(sky.horizonColor, SKY_HORIZON.red, SKY_HORIZON.green, SKY_HORIZON.blue, 1f)
        gl.uniform4(sky.cloudColor, SKY_CLOUD.red, SKY_CLOUD.green, SKY_CLOUD.blue, 1f)
        bindVertexArray(sky.emptyVertexArray)
        gl.drawArrays(GlConst.TRIANGLES, 0, 3)
        }

        gl.useProgram(scene.program)
        gl.uniform1f(scene.time, frame.timeSeconds)
        uploadWaterUniforms(source.water, frame.timeSeconds)
        uploadVortexUniforms(source.vortex, source.vortexSurge)
        gl.uniformMatrix4(scene.viewProjection, viewProjectionMatrix)
        gl.uniformMatrix4(scene.lightViewProjection, lightViewProjectionMatrix)
        gl.uniform4(scene.cameraPosition, frame.cameraPosition.x, frame.cameraPosition.y, frame.cameraPosition.z, 0f)
        gl.uniform4(scene.lightDirection, frame.keyDirection.x, frame.keyDirection.y, frame.keyDirection.z, 0f)
        gl.uniform4(scene.lightColor, frame.keyRed, frame.keyGreen, frame.keyBlue, 1f)
        gl.uniform4(scene.fillDirection, frame.fillDirection.x, frame.fillDirection.y, frame.fillDirection.z, 0f)
        gl.uniform4(scene.fillColor, frame.fillRed, frame.fillGreen, frame.fillBlue, 1f)
        gl.uniform4(scene.ambientColor, frame.ambientRed, frame.ambientGreen, frame.ambientBlue, 1f)
        gl.uniform4(scene.fogColor, frame.fogRed, frame.fogGreen, frame.fogBlue, 1f)
        gl.uniform4(scene.fogParams, frame.fogStart, frame.fogEnd, FOG_MAX_DENSITY, 0f)
        gl.uniform4(
            scene.shadowParams,
            1f / SHADOW_MAP_SIZE.toFloat(),
            SHADOW_DEPTH_BIAS,
            frame.shadowStrength,
            EXPOSURE,
        )
        gl.activeTexture(GlConst.TEXTURE0 + UNIT_SHADOW)
        gl.bindTexture(GlConst.TEXTURE_2D, shadowTexture)
        gl.uniform1i(scene.shadowMap, UNIT_SHADOW)
        gl.uniform1i(scene.albedo, UNIT_ALBEDO)
        gl.activeTexture(GlConst.TEXTURE0 + UNIT_ALBEDO)

        gl.enable(GlConst.DEPTH_TEST)
        gl.depthFunc(GlConst.LESS)
        gl.depthMask(true)
        applyBlend(GpuBlendMode.Opaque)
        opaqueDraws.forEach { if (it.inView) encode(it) }
        gl.depthMask(false)
        blendedDraws.forEach { item ->
            if (!item.inView) return@forEach
            applyBlend(item.blendMode)
            encode(item)
        }

        gl.depthMask(true)
        bindVertexArray(0)
    }


    private fun uploadWaterUniforms(water: WaterConfig, timeSeconds: Float) {
        water.packSwell(swellScratch, timeSeconds)
        for (index in scene.waterWaves.indices) {
            val base = index * WaterConfig.FLOATS_PER_TRAIN
            gl.uniform4(
                scene.waterWaves[index],
                swellScratch[base], swellScratch[base + 1], swellScratch[base + 2], swellScratch[base + 3],
            )
        }
        val shallow = water.shallowColor
        val deep = water.deepColor
        gl.uniform4(scene.waterShallow, shallow.red, shallow.green, shallow.blue, water.inverseMaximumAmplitude)
        gl.uniform4(scene.waterDeep, deep.red, deep.green, deep.blue, water.depthTint)
        gl.uniform4(
            scene.waterOptics,
            water.fresnelStrength, water.fresnelHardness, water.specularStrength, water.specularSharpness,
        )
        gl.uniform4(
            scene.waterDetail,
            water.rippleStrength, water.rippleScale,
            water.rippleSpeed * water.animationSpeed, water.quality.shaderLevel,
        )
        gl.uniform4(
            scene.waterLod,
            water.resolvedDetailFadeStart, water.resolvedDetailFadeEnd,
            water.causticScale, water.causticSpeed * water.animationSpeed,
        )
        gl.uniform4(
            scene.waterFoam,
            water.foamCrestStart, water.foamCrestEnd, water.foamIntensity, water.causticIntensity,
        )
        gl.uniform4(
            scene.waterSurf,
            water.shoreFoamEdge, water.shoreFoamSurge, water.chopPatchScale, water.shallowCalm,
        )
    }


    private fun uploadVortexUniforms(vortex: VortexConfig, surge: VortexSurge) {
        gl.uniform4(
            scene.vortexMask,
            vortex.dishEnergy, vortex.dishDepth, vortex.boatInteraction, vortex.throatFraction,
        )
        gl.uniform4(
            scene.vortexSpin,
            vortex.rotationSpeed, vortex.inflowSpeed, vortex.armCount, vortex.spiralTightness,
        )
        gl.uniform4(
            scene.vortexChurn,
            vortex.distortionStrength, vortex.turbulence, vortex.foamAmount, vortex.foamSpeed,
        )
        gl.uniform4(
            scene.vortexLook,
            vortex.foamBreakup, vortex.centerDarkness, vortex.rippleStrength, vortex.highlightIntensity,
        )
        gl.uniform4(
            scene.vortexHull,
            surge.hullX, surge.hullZ, surge.hullSuction, 1f / vortex.boatReach.coerceAtLeast(1f),
        )
        gl.uniform4(
            scene.vortexDeath,
            surge.deathX, surge.deathZ, surge.deathEnvelope, vortex.deathIntensity,
        )
    }

    private fun encode(item: DrawItem) {
        val mesh = item.mesh ?: return
        applyCull(item.cullMode)
        bindAlbedo(item.albedoTexture)
        bindMesh(mesh)
        gl.uniformMatrix4(scene.model, item.model)
        gl.uniformMatrix4(scene.normalMatrix, item.normal)
        gl.uniform4(scene.baseColor, item.red, item.green, item.blue, item.alpha)
        gl.uniform4(scene.emissive, item.emissiveRed, item.emissiveGreen, item.emissiveBlue, if (item.isBubble) 1f else 0f)
        gl.uniform4(
            scene.materialParams,
            item.roughness, item.metallic, item.receivesShadow,
            if (item.albedoTexture != 0) 1f else 0f,
        )
        gl.uniform1f(scene.waterShade, item.waterShade)
        gl.uniform1f(scene.vortex, if (item.isVortex) 1f else 0f)
        gl.uniform2(scene.foam, if (item.isFoam) 1f else 0f, item.foamAge)
        gl.drawElements(GlConst.TRIANGLES, item.indexCount, GlConst.UNSIGNED_INT, item.firstIndex * 4)
    }

    private fun bindMesh(mesh: GlMesh) = bindVertexArray(mesh.vertexArray)


    private fun bindAlbedo(texture: Int) {
        val wanted = if (texture != 0) texture else whiteTexture
        if (wanted == boundAlbedo) return
        gl.bindTexture(GlConst.TEXTURE_2D, wanted)
        boundAlbedo = wanted
    }

    private fun bindVertexArray(array: Int) {
        if (boundVertexArray == array) return
        gl.bindVertexArray(array)
        boundVertexArray = array
    }

    private fun applyCull(mode: GpuCullMode) {
        val wanted = when (mode) {
            GpuCullMode.None -> CULL_NONE
            GpuCullMode.Back -> GlConst.BACK
            GpuCullMode.Front -> GlConst.FRONT
        }
        if (wanted == currentCull) return
        if (wanted == CULL_NONE) {
            gl.disable(GlConst.CULL_FACE)
        } else {
            if (currentCull == CULL_NONE || currentCull == Int.MIN_VALUE) gl.enable(GlConst.CULL_FACE)
            gl.cullFace(wanted)
        }
        currentCull = wanted
    }

    private fun applyBlend(mode: GpuBlendMode) {
        if (mode == currentBlend) return
        when (mode) {
            GpuBlendMode.Opaque -> gl.disable(GlConst.BLEND)
            GpuBlendMode.Alpha -> {
                if (currentBlend == null || currentBlend == GpuBlendMode.Opaque) gl.enable(GlConst.BLEND)
                gl.blendFuncSeparate(
                    GlConst.SRC_ALPHA, GlConst.ONE_MINUS_SRC_ALPHA,
                    GlConst.SRC_ALPHA, GlConst.ONE_MINUS_SRC_ALPHA,
                )
            }
            GpuBlendMode.Additive -> {
                if (currentBlend == null || currentBlend == GpuBlendMode.Opaque) gl.enable(GlConst.BLEND)
                gl.blendFuncSeparate(GlConst.SRC_ALPHA, GlConst.ONE, GlConst.SRC_ALPHA, GlConst.ONE)
            }
        }
        currentBlend = mode
    }

    fun dispose() {
        assets?.let(::release)
        assets = null
        gl.deleteProgram(scene.program)
        gl.deleteProgram(shadow.program)
        gl.deleteProgram(sky.program)
        gl.deleteVertexArray(sky.emptyVertexArray)
        gl.deleteFramebuffer(shadowFramebuffer)
        gl.deleteTexture(shadowTexture)
        gl.deleteTexture(whiteTexture)
    }

    internal class SceneProgram(val program: Int, gl: GlApi) {
        val viewProjection = gl.uniformLocation(program, "uViewProjection")
        val lightViewProjection = gl.uniformLocation(program, "uLightViewProjection")
        val model = gl.uniformLocation(program, "uModel")
        val normalMatrix = gl.uniformLocation(program, "uNormalMatrix")
        val cameraPosition = gl.uniformLocation(program, "uCameraPosition")
        val lightDirection = gl.uniformLocation(program, "uLightDirection")
        val lightColor = gl.uniformLocation(program, "uLightColor")
        val fillDirection = gl.uniformLocation(program, "uFillDirection")
        val fillColor = gl.uniformLocation(program, "uFillColor")
        val ambientColor = gl.uniformLocation(program, "uAmbientColor")
        val fogColor = gl.uniformLocation(program, "uFogColor")
        val fogParams = gl.uniformLocation(program, "uFogParams")
        val shadowParams = gl.uniformLocation(program, "uShadowParams")
        val baseColor = gl.uniformLocation(program, "uBaseColor")
        val emissive = gl.uniformLocation(program, "uEmissive")
        val materialParams = gl.uniformLocation(program, "uMaterialParams")
        val shadowMap = gl.uniformLocation(program, "uShadowMap")
        val albedo = gl.uniformLocation(program, "uAlbedo")

        val time = gl.uniformLocation(program, "uTime")
        val waterShade = gl.uniformLocation(program, "uWaterShade")
        val waterWaves = IntArray(WaterConfig.SWELL_TRAINS) { gl.uniformLocation(program, "uWaterWaves[$it]") }
        val waterShallow = gl.uniformLocation(program, "uWaterShallow")
        val waterDeep = gl.uniformLocation(program, "uWaterDeep")
        val waterOptics = gl.uniformLocation(program, "uWaterOptics")
        val waterDetail = gl.uniformLocation(program, "uWaterDetail")
        val waterLod = gl.uniformLocation(program, "uWaterLod")
        val waterFoam = gl.uniformLocation(program, "uWaterFoam")
        val waterSurf = gl.uniformLocation(program, "uWaterSurf")

        val vortex = gl.uniformLocation(program, "uVortex")

        val foam = gl.uniformLocation(program, "uFoam")
        val vortexMask = gl.uniformLocation(program, "uVortexMask")
        val vortexSpin = gl.uniformLocation(program, "uVortexSpin")
        val vortexChurn = gl.uniformLocation(program, "uVortexChurn")
        val vortexLook = gl.uniformLocation(program, "uVortexLook")
        val vortexHull = gl.uniformLocation(program, "uVortexHull")
        val vortexDeath = gl.uniformLocation(program, "uVortexDeath")
    }

    internal class ShadowProgram(val program: Int, gl: GlApi) {
        val lightViewProjection = gl.uniformLocation(program, "uLightViewProjection")
        val model = gl.uniformLocation(program, "uModel")
    }

    internal class SkyProgram(val program: Int, val emptyVertexArray: Int, gl: GlApi) {
        val forward = gl.uniformLocation(program, "uSkyForward")
        val right = gl.uniformLocation(program, "uSkyRight")
        val up = gl.uniformLocation(program, "uSkyUp")
        val zenithColor = gl.uniformLocation(program, "uZenithColor")
        val horizonColor = gl.uniformLocation(program, "uHorizonColor")
        val cloudColor = gl.uniformLocation(program, "uCloudColor")
    }

    companion object {
        private const val FOG_MAX_DENSITY = 0.88f
        private const val PI_F = 3.1415927f
        private const val CULL_NONE = 0


        private const val EXPOSURE = 0.75f


        private const val SHADOW_STRENGTH = 0.72f


        private const val SHADOW_DEPTH_BIAS = 0.0010f


        private const val SHADOW_SLOPE_BIAS = 2.5f

        private val SKY_ZENITH = PackedColor(0xff4bb8f8.toInt())
        private val SKY_HORIZON = PackedColor(0xffeaf8ff.toInt())
        private val SKY_CLOUD = PackedColor(0xfffdfeff.toInt())

        private val IDENTITY_TRANSFORM = Transform3D()


        fun create(gl: GlApi, dialect: GlslDialect): GlSceneBackend? {
            val sceneProgram = GlProgramBuilder.link(
                gl, "scene",
                GlShaders.sceneVertex(dialect),
                GlShaders.sceneFragment(dialect),
            ) ?: return null
            val shadowProgram = GlProgramBuilder.link(
                gl, "shadow",
                GlShaders.shadowVertex(dialect),
                GlShaders.shadowFragment(dialect),
            ) ?: run {
                gl.deleteProgram(sceneProgram)
                return null
            }
            val skyProgram = GlProgramBuilder.link(
                gl, "sky",
                GlShaders.skyVertex(dialect),
                GlShaders.skyFragment(dialect),
            ) ?: run {
                gl.deleteProgram(sceneProgram)
                gl.deleteProgram(shadowProgram)
                return null
            }

            val shadowTexture = gl.genTexture()
            gl.bindTexture(GlConst.TEXTURE_2D, shadowTexture)
            gl.texImage2DDepth(SHADOW_MAP_SIZE, SHADOW_MAP_SIZE)
            gl.texParameteri(GlConst.TEXTURE_2D, GlConst.TEXTURE_MIN_FILTER, GlConst.LINEAR)
            gl.texParameteri(GlConst.TEXTURE_2D, GlConst.TEXTURE_MAG_FILTER, GlConst.LINEAR)
            gl.texParameteri(GlConst.TEXTURE_2D, GlConst.TEXTURE_WRAP_S, GlConst.CLAMP_TO_EDGE)
            gl.texParameteri(GlConst.TEXTURE_2D, GlConst.TEXTURE_WRAP_T, GlConst.CLAMP_TO_EDGE)
            gl.texParameteri(GlConst.TEXTURE_2D, GlConst.TEXTURE_COMPARE_MODE, GlConst.COMPARE_REF_TO_TEXTURE)
            gl.texParameteri(GlConst.TEXTURE_2D, GlConst.TEXTURE_COMPARE_FUNC, GlConst.LESS)

            val framebuffer = gl.genFramebuffer()
            gl.bindFramebuffer(framebuffer)
            gl.framebufferDepthTexture(shadowTexture)
            gl.disableColorBuffers()
            val complete = gl.framebufferComplete()
            gl.bindFramebuffer(0)
            if (!complete) {
                println("GL: shadow framebuffer incomplete; falling back to the software renderer")
                gl.deleteProgram(sceneProgram)
                gl.deleteProgram(shadowProgram)
                gl.deleteProgram(skyProgram)
                gl.deleteFramebuffer(framebuffer)
                gl.deleteTexture(shadowTexture)
                return null
            }
            val white = gl.genTexture()
            gl.bindTexture(GlConst.TEXTURE_2D, white)
            gl.texImage2DRgba(1, 1, ByteArray(4) { 0xFF.toByte() })
            gl.texParameteri(GlConst.TEXTURE_2D, GlConst.TEXTURE_MIN_FILTER, GlConst.LINEAR)
            gl.texParameteri(GlConst.TEXTURE_2D, GlConst.TEXTURE_MAG_FILTER, GlConst.LINEAR)
            gl.texParameteri(GlConst.TEXTURE_2D, GlConst.TEXTURE_WRAP_S, GlConst.CLAMP_TO_EDGE)
            gl.texParameteri(GlConst.TEXTURE_2D, GlConst.TEXTURE_WRAP_T, GlConst.CLAMP_TO_EDGE)

            return GlSceneBackend(
                gl = gl,
                scene = SceneProgram(sceneProgram, gl),
                shadow = ShadowProgram(shadowProgram, gl),
                sky = SkyProgram(skyProgram, gl.genVertexArray(), gl),
                shadowFramebuffer = framebuffer,
                shadowTexture = shadowTexture,
                whiteTexture = white,
            )
        }
    }
}

private class FrameState {
    var cameraPosition = Vec3.ZERO
    var keyDirection = Vec3(0f, -1f, 0f)
    var fillDirection = Vec3(0f, 1f, 0f)
    var keyRed = 1f; var keyGreen = 1f; var keyBlue = 1f
    var fillRed = 0f; var fillGreen = 0f; var fillBlue = 0f
    var ambientRed = 0f; var ambientGreen = 0f; var ambientBlue = 0f
    var fogRed = 0f; var fogGreen = 0f; var fogBlue = 0f
    var fogStart = 0f
    var fogEnd = 1f
    var shadowStrength = 0f
    var skyForward = Vec3(0f, 0f, -1f)
    var skyRight = Vec3(1f, 0f, 0f)
    var skyUp = Vec3.UP
    var tanHalfFov = 1f
    var aspect = 1f
    var timeSeconds = 0f

    fun setKeyColor(color: PackedColor, intensity: Float) {
        keyRed = color.red * intensity; keyGreen = color.green * intensity; keyBlue = color.blue * intensity
    }

    fun setFillColor(color: PackedColor, intensity: Float) {
        fillRed = color.red * intensity; fillGreen = color.green * intensity; fillBlue = color.blue * intensity
    }

    fun setAmbient(color: PackedColor, intensity: Float) {
        ambientRed = color.red * intensity
        ambientGreen = color.green * intensity
        ambientBlue = color.blue * intensity
    }

    fun setFog(color: PackedColor, start: Float, end: Float) {
        fogRed = color.red; fogGreen = color.green; fogBlue = color.blue
        fogStart = start
        fogEnd = end
    }
}

private class DrawItem {
    val model = FloatArray(Mat.SIZE)
    val normal = FloatArray(Mat.SIZE)
    var mesh: GlMesh? = null
    var albedoTexture = 0
    var firstIndex = 0
    var indexCount = 0
    var red = 1f
    var green = 1f
    var blue = 1f
    var alpha = 1f
    var emissiveRed = 0f
    var emissiveGreen = 0f
    var emissiveBlue = 0f
    var roughness = 0.78f
    var metallic = 0f
    var receivesShadow = 1f
    var cullMode = GpuCullMode.None
    var blendMode = GpuBlendMode.Opaque
    var castsShadow = true
    var waterShade = 0f
    var isVortex = false
    var isFoam = false
    var isBubble = false
    var foamAge = 0f

    var inView = true
    var sortDepth = 0f

    fun fill(
        mesh: GlMesh,
        primitive: GlPrimitive,
        material: GpuMaterial,
        albedoTexture: Int,
        hitFlash: Float,
        opacity: Float,
        instanceCastsShadow: Boolean,
        sortDepth: Float,
    ) {
        this.mesh = mesh
        this.albedoTexture = albedoTexture
        firstIndex = primitive.firstIndex
        indexCount = primitive.indexCount

        val source = material.baseColor
        red = source.red + (1f - source.red) * hitFlash
        green = source.green + (1f - source.green) * hitFlash
        blue = source.blue + (1f - source.blue) * hitFlash
        alpha = (source.alpha * opacity).coerceIn(0f, 1f)

        val emissive = material.emissive
        val strength = material.emissiveStrength
        val flashGlow = if (albedoTexture != 0) hitFlash * TEXTURED_FLASH_GLOW else 0f
        emissiveRed = emissive.red * strength + flashGlow
        emissiveGreen = emissive.green * strength + flashGlow
        emissiveBlue = emissive.blue * strength + flashGlow

        roughness = material.roughness
        metallic = material.metallic
        receivesShadow = if (strength > 0.001f) 0f else 1f
        cullMode = material.cullMode
        blendMode = if (alpha < 0.995f) {
            if (material.blendMode == GpuBlendMode.Additive) GpuBlendMode.Additive else GpuBlendMode.Alpha
        } else {
            material.blendMode
        }
        castsShadow = instanceCastsShadow && blendMode == GpuBlendMode.Opaque && material.waterShade <= 0f
        this.sortDepth = sortDepth
        waterShade = material.waterShade
        isVortex = material.isVortex
        isFoam = material.isFoam
        isBubble = material.isBubble
    }
}

private const val TEXTURED_FLASH_GLOW = 0.9f
