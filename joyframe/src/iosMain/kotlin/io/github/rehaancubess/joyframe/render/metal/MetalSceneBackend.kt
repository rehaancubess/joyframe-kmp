// SPDX-License-Identifier: Apache-2.0
@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package io.github.rehaancubess.joyframe.render.metal

import io.github.rehaancubess.joyframe.render.PaneRect
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
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.cValue
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.Foundation.NSError
import platform.Metal.MTLBlendFactorOne
import platform.Metal.MTLClearColorMake
import platform.Metal.MTLScissorRect
import platform.Metal.MTLViewport
import platform.Metal.MTLBlendFactorOneMinusSourceAlpha
import platform.Metal.MTLBlendFactorSourceAlpha
import platform.Metal.MTLBlendOperationAdd
import platform.Metal.MTLBufferProtocol
import platform.Metal.MTLCommandBufferProtocol
import platform.Metal.MTLCommandQueueProtocol
import platform.Metal.MTLCompareFunctionAlways
import platform.Metal.MTLCompareFunctionLess
import platform.Metal.MTLCullModeBack
import platform.Metal.MTLCullModeFront
import platform.Metal.MTLCullModeNone
import platform.Metal.MTLDepthStencilDescriptor
import platform.Metal.MTLDepthStencilStateProtocol
import platform.Metal.MTLDeviceProtocol
import platform.Metal.MTLDrawableProtocol
import platform.Metal.MTLFunctionProtocol
import platform.Metal.MTLIndexTypeUInt32
import platform.Metal.MTLLoadActionClear
import platform.Metal.MTLPixelFormatDepth32Float
import platform.Metal.MTLPixelFormatInvalid
import platform.Metal.MTLPixelFormatRGBA8Unorm
import platform.Metal.MTLPrimitiveTypeTriangle
import platform.Metal.MTLRegion
import platform.Metal.MTLRenderCommandEncoderProtocol
import platform.Metal.MTLRenderPassDescriptor
import platform.Metal.MTLRenderPipelineDescriptor
import platform.Metal.MTLRenderPipelineStateProtocol
import platform.Metal.MTLResourceStorageModeShared
import platform.Metal.MTLStorageModePrivate
import platform.Metal.MTLStoreActionStore
import platform.Metal.MTLTextureDescriptor
import platform.Metal.MTLTextureProtocol
import platform.Metal.MTLTextureUsageRenderTarget
import platform.Metal.MTLTextureUsageShaderRead
import platform.Metal.MTLWindingCounterClockwise
import kotlin.math.abs
import kotlin.math.floor

private const val BUFFER_VERTICES = 0uL
private const val BUFFER_FRAME = 1uL
private const val BUFFER_DRAW = 2uL
private const val BUFFER_SKY = 0uL

private const val TEXTURE_SHADOW = 0uL
private const val TEXTURE_ALBEDO = 1uL

private const val FRAME_UNIFORM_FLOATS = 68 +
    WaterConfig.SWELL_TRAINS * WaterConfig.FLOATS_PER_TRAIN + 7 * 4 + 6 * 4 + 4

private const val DRAW_UNIFORM_FLOATS = 44 + 4
private const val SKY_UNIFORM_FLOATS = 24
private const val FLOATS_PER_VERTEX = 8

private const val SHADOW_MAP_SIZE = 2048uL

private const val SHADOW_HALF_EXTENT = 1_250f
private const val SHADOW_LIGHT_DISTANCE = 2_400f

internal class MetalMesh(
    val vertices: MTLBufferProtocol,
    val indices: MTLBufferProtocol,
    val primitives: List<MetalPrimitive>,
    val bounds: io.github.rehaancubess.joyframe.render.gpu.Bounds3,
)

internal class MetalPrimitive(
    val firstIndex: Int,
    val indexCount: Int,
    val materialId: String,
)

internal class MetalAssets(
    val cacheKey: String,
    val meshes: Map<String, MetalMesh>,
    val textures: Map<String, MTLTextureProtocol>,
)

internal class MetalSceneBackend private constructor(
    private val device: MTLDeviceProtocol,
    private val commandQueue: MTLCommandQueueProtocol,
    private val opaquePipeline: MTLRenderPipelineStateProtocol,
    private val alphaPipeline: MTLRenderPipelineStateProtocol,
    private val additivePipeline: MTLRenderPipelineStateProtocol,
    private val skyPipeline: MTLRenderPipelineStateProtocol,
    private val shadowPipeline: MTLRenderPipelineStateProtocol,
    private val writingDepth: MTLDepthStencilStateProtocol,
    private val readOnlyDepth: MTLDepthStencilStateProtocol,
    private val ignoringDepth: MTLDepthStencilStateProtocol,
    private val shadowMap: MTLTextureProtocol,
    private val whiteTexture: MTLTextureProtocol,
) {
    private val frameUniforms = FloatArray(FRAME_UNIFORM_FLOATS)
    private val drawUniforms = FloatArray(DRAW_UNIFORM_FLOATS)
    private val skyUniforms = FloatArray(SKY_UNIFORM_FLOATS)

    private val viewMatrix = FloatArray(Mat.SIZE)
    private val projectionMatrix = FloatArray(Mat.SIZE)
    private val lightViewMatrix = FloatArray(Mat.SIZE)
    private val lightProjectionMatrix = FloatArray(Mat.SIZE)
    private val instanceModel = FloatArray(Mat.SIZE)
    private val instanceNormal = FloatArray(Mat.SIZE)
    private val partModel = FloatArray(Mat.SIZE)
    private val partNormal = FloatArray(Mat.SIZE)
    private val localModel = FloatArray(Mat.SIZE)
    private val localNormal = FloatArray(Mat.SIZE)
    private val matrixScratch = FloatArray(Mat.SIZE)
    private val sceneFrustum = io.github.rehaancubess.joyframe.render.math.BoundsFrustum()
    private val drawPool = ArrayList<DrawItem>()
    private var pooled = 0
    private val opaqueDraws = ArrayList<DrawItem>()
    private val blendedDraws = ArrayList<DrawItem>()
    private val farToNear = Comparator<DrawItem> { a, b -> b.sortDepth.compareTo(a.sortDepth) }

    private var assets: MetalAssets? = null


    fun assetsFor(source: GpuSceneAssets): MetalAssets {
        assets?.let { if (it.cacheKey == source.cacheKey) return it }
        val uploaded = MetalAssets(
            cacheKey = source.cacheKey,
            meshes = source.meshes.mapValues { (_, mesh) -> upload(mesh) },
            textures = source.textures.mapValues { (_, texture) -> upload(texture) },
        )
        generateMipmaps(uploaded.textures.values)
        assets = uploaded
        return uploaded
    }

    private fun upload(mesh: IndexedMesh): MetalMesh {
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

        val vertexBuffer = vertexData.usePinned {
            device.newBufferWithBytes(
                it.addressOf(0),
                (vertexData.size * 4).toULong(),
                MTLResourceStorageModeShared,
            )
        } ?: error("Metal: could not allocate a vertex buffer for ${mesh.id}")
        val indexBuffer = indexData.usePinned {
            device.newBufferWithBytes(
                it.addressOf(0),
                (indexData.size * 4).toULong(),
                MTLResourceStorageModeShared,
            )
        } ?: error("Metal: could not allocate an index buffer for ${mesh.id}")

        return MetalMesh(
            vertices = vertexBuffer,
            indices = indexBuffer,
            primitives = mesh.primitives.map { MetalPrimitive(it.firstIndex, it.indexCount, it.materialId) },
            bounds = mesh.bounds,
        )
    }

    private fun upload(texture: TextureData): MTLTextureProtocol {
        val descriptor = MTLTextureDescriptor.texture2DDescriptorWithPixelFormat(
            pixelFormat = MTLPixelFormatRGBA8Unorm,
            width = texture.width.toULong(),
            height = texture.height.toULong(),
            mipmapped = true,
        )
        descriptor.usage = MTLTextureUsageShaderRead
        val created = device.newTextureWithDescriptor(descriptor)
            ?: error("Metal: could not allocate a texture for ${texture.id}")
        val region = cValue<MTLRegion> {
            origin.x = 0uL; origin.y = 0uL; origin.z = 0uL
            size.width = texture.width.toULong()
            size.height = texture.height.toULong()
            size.depth = 1uL
        }
        texture.rgba.usePinned {
            created.replaceRegion(
                region = region,
                mipmapLevel = 0uL,
                withBytes = it.addressOf(0),
                bytesPerRow = (texture.width * 4).toULong(),
            )
        }
        return created
    }


    private fun generateMipmaps(textures: Collection<MTLTextureProtocol>) {
        if (textures.isEmpty()) return
        val commandBuffer = commandQueue.commandBuffer() ?: return
        val blit = commandBuffer.blitCommandEncoder() ?: return
        textures.forEach(blit::generateMipmapsForTexture)
        blit.endEncoding()
        commandBuffer.commit()
    }


    fun render(
        frame: GpuSceneFrame,
        aspect: Float,
        passDescriptor: MTLRenderPassDescriptor,
        drawable: MTLDrawableProtocol?,
        transparentBackground: Boolean = false,
    ) {
        val uploaded = assetsFor(frame.assets)
        buildFrameUniforms(frame, aspect)
        collectDraws(frame, uploaded)

        val commandBuffer = commandQueue.commandBuffer() ?: return
        encodeShadowPass(commandBuffer, shadowMap)
        encodeScenePass(passDescriptor, commandBuffer, transparentBackground)
        if (drawable != null) commandBuffer.presentDrawable(drawable)
        commandBuffer.commit()
    }

    /**
     * Split screen: every pane's shadow pass first, each into its own shadow map, then one scene pass
     * that moves the viewport and scissor from pane to pane. One pass rather than one per pane because
     * a Metal clear load action clears the whole attachment, not a viewport of it. Panes never overlap,
     * so the single depth clear serves them all. [panes] use Metal's top-left origin.
     */
    fun renderSplit(
        frames: List<GpuSceneFrame>,
        panes: List<PaneRect>,
        gutter: PackedColor,
        passDescriptor: MTLRenderPassDescriptor,
        drawable: MTLDrawableProtocol?,
    ) {
        val count = minOf(frames.size, panes.size)
        if (count == 0) return
        val commandBuffer = commandQueue.commandBuffer() ?: return
        for (index in 0 until count) {
            buildFrameUniforms(frames[index], panes[index].aspect)
            collectDraws(frames[index], assetsFor(frames[index].assets))
            encodeShadowPass(commandBuffer, shadowMapFor(index))
        }
        passDescriptor.colorAttachments.objectAtIndexedSubscript(0uL)?.clearColor =
            MTLClearColorMake(gutter.red.toDouble(), gutter.green.toDouble(), gutter.blue.toDouble(), 1.0)
        val encoder = commandBuffer.renderCommandEncoderWithDescriptor(passDescriptor) ?: return
        encoder.setFrontFacingWinding(MTLWindingCounterClockwise)
        for (index in 0 until count) {
            val pane = panes[index]
            buildFrameUniforms(frames[index], pane.aspect)
            collectDraws(frames[index], assetsFor(frames[index].assets))
            encoder.setViewport(cValue<MTLViewport> {
                originX = pane.x.toDouble(); originY = pane.y.toDouble()
                width = pane.width.toDouble(); height = pane.height.toDouble()
                znear = 0.0; zfar = 1.0
            })
            encoder.setScissorRect(cValue<MTLScissorRect> {
                x = pane.x.toULong(); y = pane.y.toULong()
                width = pane.width.toULong(); height = pane.height.toULong()
            })
            encodeSceneContents(encoder, transparentBackground = false, shadow = shadowMapFor(index))
        }
        encoder.endEncoding()
        if (drawable != null) commandBuffer.presentDrawable(drawable)
        commandBuffer.commit()
    }

    /** Pane zero shares the single-view shadow map; the rest are made the first time they are needed. */
    private val extraShadowMaps = ArrayList<MTLTextureProtocol>()

    private fun shadowMapFor(pane: Int): MTLTextureProtocol {
        if (pane == 0) return shadowMap
        while (extraShadowMaps.size < pane) {
            extraShadowMaps += device.newTextureWithDescriptor(shadowTextureDescriptor()) ?: return shadowMap
        }
        return extraShadowMaps[pane - 1]
    }

    private fun buildFrameUniforms(frame: GpuSceneFrame, aspect: Float) {
        val camera = frame.camera
        val near = camera.nearPlane.coerceAtLeast(1f)
        val far = camera.farPlane.coerceAtLeast(near + 1f)
        Mat.lookAt(camera.eye, camera.target, camera.up, viewMatrix)
        Mat.perspective(camera.verticalFovDegrees, aspect, near, far, DepthRange.ZeroToOne, projectionMatrix)

        val key = frame.lights.firstOrNull()
        val fill = frame.lights.getOrNull(1)
        val shadowCaster = frame.lights.firstOrNull { it.castsShadow }
        buildLightMatrices(shadowCaster?.direction ?: Vec3(0f, -1f, 0f), frame.camera.target)

        var cursor = 0
        Mat.multiply(projectionMatrix, viewMatrix, matrixScratch)
        sceneFrustum.update(matrixScratch, DepthRange.ZeroToOne)
        matrixScratch.copyInto(frameUniforms, cursor); cursor += Mat.SIZE
        Mat.multiply(lightProjectionMatrix, lightViewMatrix, matrixScratch)
        matrixScratch.copyInto(frameUniforms, cursor); cursor += Mat.SIZE

        cursor = frameUniforms.putVec4(cursor, camera.eye.x, camera.eye.y, camera.eye.z, 0f)
        val keyDirection = key?.direction ?: Vec3(0f, -1f, 0f)
        cursor = frameUniforms.putVec4(cursor, keyDirection.x, keyDirection.y, keyDirection.z, 0f)
        cursor = frameUniforms.putColor(cursor, key?.color ?: PackedColor.White, key?.intensity ?: 1f)
        val fillDirection = fill?.direction ?: Vec3(0f, 1f, 0f)
        cursor = frameUniforms.putVec4(cursor, fillDirection.x, fillDirection.y, fillDirection.z, 0f)
        cursor = frameUniforms.putColor(cursor, fill?.color ?: PackedColor.Black, fill?.intensity ?: 0f)

        val environment = frame.environment
        cursor = frameUniforms.putColor(cursor, environment.ambientColor, environment.ambientIntensity)
        cursor = frameUniforms.putColor(cursor, environment.fogColor, 1f)
        cursor = frameUniforms.putVec4(cursor, environment.fogStart, environment.fogEnd, FOG_MAX_DENSITY, 0f)
        cursor = frameUniforms.putVec4(
            cursor,
            1f / SHADOW_MAP_SIZE.toFloat(),
            SHADOW_DEPTH_BIAS,
            if (shadowCaster != null) SHADOW_STRENGTH else 0f,
            EXPOSURE,
        )

        writeWaterUniforms(frame.water, frame.vortex, frame.vortexSurge, frame.renderTimeSeconds, cursor)
        buildSkyUniforms(frame, aspect)
    }


    private fun writeWaterUniforms(
        water: WaterConfig,
        vortex: VortexConfig,
        surge: VortexSurge,
        timeSeconds: Float,
        start: Int,
    ) {
        var cursor = start
        water.packSwell(frameUniforms, timeSeconds, cursor)
        cursor += WaterConfig.SWELL_TRAINS * WaterConfig.FLOATS_PER_TRAIN

        val shallow = water.shallowColor
        val deep = water.deepColor
        cursor = frameUniforms.putVec4(
            cursor, shallow.red, shallow.green, shallow.blue, water.inverseMaximumAmplitude,
        )
        cursor = frameUniforms.putVec4(cursor, deep.red, deep.green, deep.blue, water.depthTint)
        cursor = frameUniforms.putVec4(
            cursor,
            water.fresnelStrength, water.fresnelHardness, water.specularStrength, water.specularSharpness,
        )
        cursor = frameUniforms.putVec4(
            cursor,
            water.rippleStrength, water.rippleScale,
            water.rippleSpeed * water.animationSpeed, water.quality.shaderLevel,
        )
        cursor = frameUniforms.putVec4(
            cursor,
            water.resolvedDetailFadeStart, water.resolvedDetailFadeEnd,
            water.causticScale, water.causticSpeed * water.animationSpeed,
        )
        cursor = frameUniforms.putVec4(
            cursor,
            water.foamCrestStart, water.foamCrestEnd, water.foamIntensity, water.causticIntensity,
        )
        cursor = frameUniforms.putVec4(
            cursor,
            water.shoreFoamEdge, water.shoreFoamSurge, water.chopPatchScale, water.shallowCalm,
        )
        cursor = frameUniforms.putVec4(
            cursor, vortex.dishEnergy, vortex.dishDepth, vortex.boatInteraction, vortex.throatFraction,
        )
        cursor = frameUniforms.putVec4(
            cursor, vortex.rotationSpeed, vortex.inflowSpeed, vortex.armCount, vortex.spiralTightness,
        )
        cursor = frameUniforms.putVec4(
            cursor, vortex.distortionStrength, vortex.turbulence, vortex.foamAmount, vortex.foamSpeed,
        )
        cursor = frameUniforms.putVec4(
            cursor, vortex.foamBreakup, vortex.centerDarkness, vortex.rippleStrength, vortex.highlightIntensity,
        )
        cursor = frameUniforms.putVec4(
            cursor, surge.hullX, surge.hullZ, surge.hullSuction, 1f / vortex.boatReach.coerceAtLeast(1f),
        )
        cursor = frameUniforms.putVec4(
            cursor, surge.deathX, surge.deathZ, surge.deathEnvelope, vortex.deathIntensity,
        )

        frameUniforms.putVec4(cursor, timeSeconds, 0f, 0f, 0f)
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
            depthRange = DepthRange.ZeroToOne,
            out = lightProjectionMatrix,
        )
        lightProjectionMatrix[12] = -offsetX / SHADOW_HALF_EXTENT
        lightProjectionMatrix[13] = -offsetY / SHADOW_HALF_EXTENT
    }

    private fun buildSkyUniforms(frame: GpuSceneFrame, aspect: Float) {
        val camera = frame.camera
        val forward = (camera.target - camera.eye).normalized()
        var right = forward.cross(camera.up)
        right = if (right.length() > 1e-4f) right.normalized() else Vec3(1f, 0f, 0f)
        val up = right.cross(forward).normalized()
        val tanHalfFov = kotlin.math.tan(camera.verticalFovDegrees * 0.5f * (3.1415927f / 180f))

        var cursor = 0
        cursor = skyUniforms.putVec4(cursor, forward.x, forward.y, forward.z, tanHalfFov)
        cursor = skyUniforms.putVec4(cursor, right.x, right.y, right.z, aspect)
        cursor = skyUniforms.putVec4(cursor, up.x, up.y, up.z, frame.renderTimeSeconds)
        cursor = skyUniforms.putColor(cursor, SKY_ZENITH, 1f)
        cursor = skyUniforms.putColor(cursor, SKY_HORIZON, 1f)
        skyUniforms.putColor(cursor, SKY_CLOUD, 1f)
    }

    private fun obtain(): DrawItem {
        if (pooled == drawPool.size) drawPool.add(DrawItem())
        return drawPool[pooled++]
    }

    private fun collectDraws(frame: GpuSceneFrame, uploaded: MetalAssets) {
        pooled = 0
        opaqueDraws.clear()
        blendedDraws.clear()

        val eye = frame.camera.eye
        frame.instances.forEach { instance ->
            if (!instance.visible) return@forEach
            val model = frame.assets.models[instance.modelId] ?: return@forEach
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
                val identityLocal = part.localTransform == IDENTITY_TRANSFORM
                if (identityLocal) {
                    instanceModel.copyInto(partModel)
                    instanceNormal.copyInto(partNormal)
                } else {
                    Mat.compose(part.localTransform, localModel, localNormal)
                    Mat.multiply(instanceModel, localModel, partModel)
                    Mat.multiply(instanceNormal, localNormal, partNormal)
                }

                val inView = instance.kind == InstanceKind.Dynamic || sceneFrustum.intersects(mesh.bounds, partModel)

                mesh.primitives.forEach primitives@{ primitive ->
                    if (primitive.indexCount == 0) return@primitives
                    val materialId = part.materialSlot?.let(instance.materials::get) ?: primitive.materialId
                    val material = frame.assets.materials[materialId] ?: return@primitives
                    val item = obtain()
                    val albedo = material.albedo?.let(uploaded.textures::get)
                    item.fill(mesh, primitive, material, albedo, hitFlash, opacity, castsShadow, sortDepth)
                    item.inView = inView || material.waterShade > 0f || instance.kind == InstanceKind.Effect
                    item.foamAge = foamAge
                    partModel.copyInto(item.model)
                    partNormal.copyInto(item.normal)
                    if (item.blendMode == GpuBlendMode.Opaque) opaqueDraws.add(item) else blendedDraws.add(item)
                }
            }
        }
        if (blendedDraws.size > 1) blendedDraws.sortWith(farToNear)
    }

    private fun encodeShadowPass(commandBuffer: MTLCommandBufferProtocol, target: MTLTextureProtocol) {
        if (frameUniforms[FRAME_SHADOW_STRENGTH_INDEX] <= 0.001f) return
        val descriptor = MTLRenderPassDescriptor.renderPassDescriptor()
        descriptor.depthAttachment.apply {
            texture = target
            loadAction = MTLLoadActionClear
            storeAction = MTLStoreActionStore
            clearDepth = 1.0
        }
        val encoder = commandBuffer.renderCommandEncoderWithDescriptor(descriptor) ?: return
        encoder.setRenderPipelineState(shadowPipeline)
        encoder.setDepthStencilState(writingDepth)
        encoder.setFrontFacingWinding(MTLWindingCounterClockwise)
        encoder.setDepthBias(depthBias = 0f, slopeScale = SHADOW_SLOPE_BIAS, clamp = 0f)
        encoder.setVertexFloatArray(frameUniforms, BUFFER_FRAME)
        var cullMode = ULong.MAX_VALUE
        opaqueDraws.forEach { item ->
            if (!item.castsShadow) return@forEach
            val wanted = if (item.cullMode == MTLCullModeNone) MTLCullModeNone else MTLCullModeBack
            if (wanted != cullMode) {
                encoder.setCullMode(wanted)
                cullMode = wanted
            }
            encoder.setVertexBuffer(item.mesh!!.vertices, 0uL, BUFFER_VERTICES)
            writeDrawUniforms(item)
            encoder.setVertexFloatArray(drawUniforms, BUFFER_DRAW)
            encoder.drawIndexedPrimitives(
                MTLPrimitiveTypeTriangle,
                item.indexCount.toULong(),
                MTLIndexTypeUInt32,
                item.mesh!!.indices,
                (item.firstIndex * 4).toULong(),
            )
        }
        encoder.endEncoding()
    }

    private fun encodeScenePass(
        passDescriptor: MTLRenderPassDescriptor,
        commandBuffer: MTLCommandBufferProtocol,
        transparentBackground: Boolean,
    ) {
        val encoder = commandBuffer.renderCommandEncoderWithDescriptor(passDescriptor) ?: return
        encoder.setFrontFacingWinding(MTLWindingCounterClockwise)
        encodeSceneContents(encoder, transparentBackground, shadowMap)
        encoder.endEncoding()
    }

    private fun encodeSceneContents(
        encoder: MTLRenderCommandEncoderProtocol,
        transparentBackground: Boolean,
        shadow: MTLTextureProtocol,
    ) {
        if (!transparentBackground) {
        encoder.setRenderPipelineState(skyPipeline)
        encoder.setDepthStencilState(ignoringDepth)
        encoder.setCullMode(MTLCullModeNone)
        encoder.setFragmentFloatArray(skyUniforms, BUFFER_SKY)
        encoder.drawPrimitives(MTLPrimitiveTypeTriangle, 0uL, 3uL)
        }

        encoder.setVertexFloatArray(frameUniforms, BUFFER_FRAME)
        encoder.setFragmentFloatArray(frameUniforms, BUFFER_FRAME)
        encoder.setFragmentTexture(shadow, TEXTURE_SHADOW)

        encoder.setDepthStencilState(writingDepth)
        encoder.setRenderPipelineState(opaquePipeline)
        var currentBlend = GpuBlendMode.Opaque
        opaqueDraws.forEach { if (it.inView) encoder.encode(it) }

        encoder.setDepthStencilState(readOnlyDepth)
        blendedDraws.forEach { item ->
            if (!item.inView) return@forEach
            if (item.blendMode != currentBlend) {
                currentBlend = item.blendMode
                encoder.setRenderPipelineState(
                    if (currentBlend == GpuBlendMode.Additive) additivePipeline else alphaPipeline,
                )
            }
            encoder.encode(item)
        }
    }

    private fun MTLRenderCommandEncoderProtocol.encode(item: DrawItem) {
        val mesh = item.mesh ?: return
        setCullMode(item.cullMode)
        setFragmentTexture(item.albedo ?: whiteTexture, TEXTURE_ALBEDO)
        setVertexBuffer(mesh.vertices, 0uL, BUFFER_VERTICES)
        writeDrawUniforms(item)
        setVertexFloatArray(drawUniforms, BUFFER_DRAW)
        setFragmentFloatArray(drawUniforms, BUFFER_DRAW)
        drawIndexedPrimitives(
            MTLPrimitiveTypeTriangle,
            item.indexCount.toULong(),
            MTLIndexTypeUInt32,
            mesh.indices,
            (item.firstIndex * 4).toULong(),
        )
    }

    private fun writeDrawUniforms(item: DrawItem) {
        item.model.copyInto(drawUniforms, 0)
        item.normal.copyInto(drawUniforms, Mat.SIZE)
        var cursor = Mat.SIZE * 2
        cursor = drawUniforms.putVec4(cursor, item.red, item.green, item.blue, item.alpha)
        cursor = drawUniforms.putVec4(cursor, item.emissiveRed, item.emissiveGreen, item.emissiveBlue, if (item.isBubble) 1f else 0f)
        cursor = drawUniforms.putVec4(
            cursor,
            item.roughness, item.metallic, item.receivesShadow,
            if (item.albedo != null) 1f else 0f,
        )
        drawUniforms.putVec4(
            cursor, item.waterShade, if (item.isVortex) 1f else 0f,
            if (item.isFoam) 1f else 0f, item.foamAge,
        )
    }

    companion object {
        private const val FOG_MAX_DENSITY = 0.88f


        private const val EXPOSURE = 0.75f


        private const val SHADOW_STRENGTH = 0.72f


        private const val SHADOW_DEPTH_BIAS = 0.0010f


        private const val SHADOW_SLOPE_BIAS = 2.5f

        private const val FRAME_SHADOW_STRENGTH_INDEX = Mat.SIZE * 2 + 4 * 8 + 2

        private val SKY_ZENITH = PackedColor(0xff4bb8f8.toInt())
        private val SKY_HORIZON = PackedColor(0xffeaf8ff.toInt())
        private val SKY_CLOUD = PackedColor(0xfffdfeff.toInt())

        private val IDENTITY_TRANSFORM = Transform3D()

        private fun logMetalFailure(stage: String, error: NSError?) {
            println("Metal: $stage failed — ${error?.localizedDescription ?: "no error detail"}")
        }


        fun create(
            device: MTLDeviceProtocol,
            colorPixelFormat: ULong,
            depthPixelFormat: ULong,
            sampleCount: ULong,
        ): MetalSceneBackend? = memScoped {
            val queue = device.newCommandQueue() ?: return@memScoped null
            val errorSlot = alloc<ObjCObjectVar<NSError?>>()
            val library = device.newLibraryWithSource(SCENE_SHADER_SOURCE, null, errorSlot.ptr)
            if (library == null) {
                logMetalFailure("shader compilation", errorSlot.value)
                return@memScoped null
            }

            val sceneVertex = library.newFunctionWithName("scene_vertex") ?: return@memScoped null
            val sceneFragment = library.newFunctionWithName("scene_fragment") ?: return@memScoped null
            val skyVertex = library.newFunctionWithName("sky_vertex") ?: return@memScoped null
            val skyFragment = library.newFunctionWithName("sky_fragment") ?: return@memScoped null
            val shadowVertex = library.newFunctionWithName("shadow_vertex") ?: return@memScoped null

            fun pipeline(
                label: String,
                vertexFunction: MTLFunctionProtocol,
                fragmentFunction: MTLFunctionProtocol?,
                blendMode: GpuBlendMode?,
                samples: ULong,
                color: ULong,
                depth: ULong,
            ): MTLRenderPipelineStateProtocol? {
                val descriptor = MTLRenderPipelineDescriptor()
                descriptor.label = label
                descriptor.vertexFunction = vertexFunction
                descriptor.fragmentFunction = fragmentFunction
                descriptor.rasterSampleCount = samples
                descriptor.depthAttachmentPixelFormat = depth
                val attachment = descriptor.colorAttachments.objectAtIndexedSubscript(0uL)
                attachment.pixelFormat = color
                if (blendMode != null && blendMode != GpuBlendMode.Opaque) {
                    attachment.blendingEnabled = true
                    attachment.rgbBlendOperation = MTLBlendOperationAdd
                    attachment.alphaBlendOperation = MTLBlendOperationAdd
                    attachment.sourceRGBBlendFactor = MTLBlendFactorSourceAlpha
                    attachment.sourceAlphaBlendFactor = MTLBlendFactorSourceAlpha
                    val destination = if (blendMode == GpuBlendMode.Additive) {
                        MTLBlendFactorOne
                    } else {
                        MTLBlendFactorOneMinusSourceAlpha
                    }
                    attachment.destinationRGBBlendFactor = destination
                    attachment.destinationAlphaBlendFactor = destination
                }
                val slot = alloc<ObjCObjectVar<NSError?>>()
                val state = device.newRenderPipelineStateWithDescriptor(descriptor, slot.ptr)
                if (state == null) logMetalFailure("pipeline '$label'", slot.value)
                return state
            }

            val opaque = pipeline(
                "scene/opaque", sceneVertex, sceneFragment, GpuBlendMode.Opaque,
                sampleCount, colorPixelFormat, depthPixelFormat,
            ) ?: return@memScoped null
            val alpha = pipeline(
                "scene/alpha", sceneVertex, sceneFragment, GpuBlendMode.Alpha,
                sampleCount, colorPixelFormat, depthPixelFormat,
            ) ?: return@memScoped null
            val additive = pipeline(
                "scene/additive", sceneVertex, sceneFragment, GpuBlendMode.Additive,
                sampleCount, colorPixelFormat, depthPixelFormat,
            ) ?: return@memScoped null
            val sky = pipeline(
                "scene/sky", skyVertex, skyFragment, GpuBlendMode.Opaque,
                sampleCount, colorPixelFormat, depthPixelFormat,
            ) ?: return@memScoped null
            val shadow = pipeline(
                "scene/shadow", shadowVertex, null, null,
                1uL, MTLPixelFormatInvalid, MTLPixelFormatDepth32Float,
            ) ?: return@memScoped null

            fun depthState(compare: ULong, write: Boolean): MTLDepthStencilStateProtocol? {
                val descriptor = MTLDepthStencilDescriptor()
                descriptor.depthCompareFunction = compare
                descriptor.depthWriteEnabled = write
                return device.newDepthStencilStateWithDescriptor(descriptor)
            }

            val shadowTextureDescriptor = shadowTextureDescriptor()
            val whiteDescriptor = MTLTextureDescriptor.texture2DDescriptorWithPixelFormat(
                pixelFormat = MTLPixelFormatRGBA8Unorm,
                width = 1uL,
                height = 1uL,
                mipmapped = false,
            )
            whiteDescriptor.usage = MTLTextureUsageShaderRead
            val white = device.newTextureWithDescriptor(whiteDescriptor) ?: return@memScoped null
            ByteArray(4) { 0xFF.toByte() }.usePinned { pixel ->
                white.replaceRegion(
                    region = cValue<MTLRegion> {
                        origin.x = 0uL; origin.y = 0uL; origin.z = 0uL
                        size.width = 1uL; size.height = 1uL; size.depth = 1uL
                    },
                    mipmapLevel = 0uL,
                    withBytes = pixel.addressOf(0),
                    bytesPerRow = 4uL,
                )
            }

            MetalSceneBackend(
                device = device,
                commandQueue = queue,
                opaquePipeline = opaque,
                alphaPipeline = alpha,
                additivePipeline = additive,
                skyPipeline = sky,
                shadowPipeline = shadow,
                writingDepth = depthState(MTLCompareFunctionLess, true) ?: return@memScoped null,
                readOnlyDepth = depthState(MTLCompareFunctionLess, false) ?: return@memScoped null,
                ignoringDepth = depthState(MTLCompareFunctionAlways, false) ?: return@memScoped null,
                shadowMap = device.newTextureWithDescriptor(shadowTextureDescriptor) ?: return@memScoped null,
                whiteTexture = white,
            )
        }
    }
}

private class DrawItem {
    val model = FloatArray(Mat.SIZE)
    val normal = FloatArray(Mat.SIZE)
    var mesh: MetalMesh? = null
    var albedo: MTLTextureProtocol? = null
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
    var cullMode = MTLCullModeNone
    var blendMode = GpuBlendMode.Opaque
    var castsShadow = true
    var inView = true
    var sortDepth = 0f
    var waterShade = 0f
    var isVortex = false
    var isFoam = false
    var isBubble = false
    var foamAge = 0f

    fun fill(
        mesh: MetalMesh,
        primitive: MetalPrimitive,
        material: GpuMaterial,
        albedo: MTLTextureProtocol?,
        hitFlash: Float,
        opacity: Float,
        instanceCastsShadow: Boolean,
        sortDepth: Float,
    ) {
        this.mesh = mesh
        this.albedo = albedo
        firstIndex = primitive.firstIndex
        indexCount = primitive.indexCount

        val source = material.baseColor
        val baseRed = ((source.argb ushr 16) and 0xff) / 255f
        val baseGreen = ((source.argb ushr 8) and 0xff) / 255f
        val baseBlue = (source.argb and 0xff) / 255f
        red = baseRed + (1f - baseRed) * hitFlash
        green = baseGreen + (1f - baseGreen) * hitFlash
        blue = baseBlue + (1f - baseBlue) * hitFlash
        alpha = (source.alpha * opacity).coerceIn(0f, 1f)

        val emissive = material.emissive
        val strength = material.emissiveStrength
        val flashGlow = if (albedo != null) hitFlash * TEXTURED_FLASH_GLOW else 0f
        emissiveRed = ((emissive.argb ushr 16) and 0xff) / 255f * strength + flashGlow
        emissiveGreen = ((emissive.argb ushr 8) and 0xff) / 255f * strength + flashGlow
        emissiveBlue = (emissive.argb and 0xff) / 255f * strength + flashGlow

        roughness = material.roughness
        metallic = material.metallic
        receivesShadow = if (strength > 0.001f) 0f else 1f
        cullMode = when (material.cullMode) {
            GpuCullMode.Back -> MTLCullModeBack
            GpuCullMode.Front -> MTLCullModeFront
            GpuCullMode.None -> MTLCullModeNone
        }
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

private fun FloatArray.putVec4(offset: Int, x: Float, y: Float, z: Float, w: Float): Int {
    this[offset] = x
    this[offset + 1] = y
    this[offset + 2] = z
    this[offset + 3] = w
    return offset + 4
}

private fun FloatArray.putColor(offset: Int, color: PackedColor, intensity: Float): Int = putVec4(
    offset,
    ((color.argb ushr 16) and 0xff) / 255f * intensity,
    ((color.argb ushr 8) and 0xff) / 255f * intensity,
    (color.argb and 0xff) / 255f * intensity,
    color.alpha,
)

private fun MTLRenderCommandEncoderProtocol.setVertexFloatArray(data: FloatArray, index: ULong) {
    data.usePinned { setVertexBytes(it.addressOf(0), (data.size * 4).toULong(), index) }
}

private fun MTLRenderCommandEncoderProtocol.setFragmentFloatArray(data: FloatArray, index: ULong) {
    data.usePinned { setFragmentBytes(it.addressOf(0), (data.size * 4).toULong(), index) }
}

private const val TEXTURED_FLASH_GLOW = 0.9f

private fun shadowTextureDescriptor(): MTLTextureDescriptor = MTLTextureDescriptor().apply {
    pixelFormat = MTLPixelFormatDepth32Float
    width = SHADOW_MAP_SIZE
    height = SHADOW_MAP_SIZE
    usage = MTLTextureUsageRenderTarget or MTLTextureUsageShaderRead
    storageMode = MTLStorageModePrivate
}
