// SPDX-License-Identifier: Apache-2.0
package example

import io.github.rehaancubess.joyframe.render.*
import io.github.rehaancubess.joyframe.render.gpu.*
import io.github.rehaancubess.joyframe.render.math.Vec3
import io.github.rehaancubess.joyframe.render.water.*
import kotlin.math.*

// The lagoon's shoreline ramps: wave energy and depth rise with distance from land.
private val shore=WaterField(Array(44) { x -> IntArray(44) { z -> minOf(x,z,43-x,43-z) } },50f)
fun lakeEnergyAt(x: Float,z: Float)=shore.energyAt(x+1100f,z+1100f)
private fun lakeDepthAt(x: Float,z: Float)=shore.depthAt(x+1100f,z+1100f)
fun lagoonWater()=WaterConfig(rippleStrength=.44f,foamIntensity=1.30f,fresnelStrength=.54f)

/** Two whirlpools turning opposite ways. The same values drive the pull and the drawn dish. */
val lakePools = listOf(
    Whirlpool(-470f,-400f,radius=330f,coreRadius=70f,pull=900f,swirl=360f,clockwise=true),
    Whirlpool(500f,430f,radius=330f,coreRadius=70f,pull=900f,swirl=360f,clockwise=false),
)
val buoyPosition = Vec3(40f,0f,-760f)
private val dishScale = VortexConfig().meshRadiusScale

/** Procedural fallback geometry. An optional local GLB is loaded separately by PreviewBoat. */
fun lakeAssets(whirlpools: Boolean) = sceneAssets(if(whirlpools) "joyframe-lake-pools-v2" else "joyframe-lake-v2") {
    material("water",0xff14b4c8.toInt(),water=true)
    whirlpoolMaterial("pool",0xff0f8ca3.toInt())
    material("earth",0xffd4c193.toInt()); material("grass",0xff8fab70.toInt())
    material("trunk",0xff7d5847.toInt()); material("pine",0xff32766d.toInt())
    material("hull",0xffef7755.toInt()); material("hull-blue",0xff4f8fe8.toInt())
    material("cream",0xfffff0ca.toInt()); material("glass",0xff274653.toInt())
    material("buoy",0xffe0483e.toInt())
    material(GpuMaterial("lamp",PackedColor(0xfffff2b0.toInt()),emissive=PackedColor(0xffffe27a.toInt()),emissiveStrength=1.4f))
    // Holes leave room for the whirlpool dishes; each dish overlaps its hole's grid-cut edge.
    val holes = if(whirlpools) lakePools.map { WaterHole.forWhirlpool(it.x,it.z,it.radius*dishScale) } else emptyList()
    val surface=Primitives.water("lake",2200f,"water",80,holes=holes)
    mesh(surface.copy(textureCoordinates=surface.positions.map { Vec2f(lakeEnergyAt(it.x,it.z),lakeDepthAt(it.x,it.z)) }))
    if(whirlpools) {
        whirlpool("pool-cw",lakePools[0].radius*dishScale,"pool",clockwise=true)
        whirlpool("pool-ccw",lakePools[1].radius*dishScale,"pool",clockwise=false)
        model("pool-cw",ModelPart("pool-cw")); model("pool-ccw",ModelPart("pool-ccw"))
    }
    box("ground",Vec3(2600f,80f,2600f),"earth")
    box("bank",Vec3(2600f,32f,200f),"grass")
    box("trunk",Vec3(27f,115f,27f),"trunk")
    cone("crown",90f,200f,"pine")
    box("deck",Vec3(145f,17f,75f),"cream")
    box("cabin",Vec3(64f,46f,58f),"cream")
    box("window",Vec3(5f,27f,48f),"glass")
    cone("buoy-body",34f,70f,"buoy",sides=10)
    box("buoy-lamp",Vec3(16f,16f,16f),"lamp")
    // A pointed bow, flat stern and tapered lower hull, facing +X.
    val outline = listOf(Vec3(-85f,0f,-46f),Vec3(40f,0f,-46f),Vec3(105f,0f,0f),
        Vec3(40f,0f,46f),Vec3(-85f,0f,46f))
    val triangles = mutableListOf<Vec3>()
    outline.indices.forEach { i ->
        val a = outline[i]; val b = outline[(i+1)%outline.size]
        val lowA = Vec3(a.x*.85f,-30f,a.z*.65f); val lowB = Vec3(b.x*.85f,-30f,b.z*.65f)
        triangles.addAll(listOf(Vec3.ZERO,b,a,a,b,lowB,a,lowB,lowA,Vec3(0f,-30f,0f),lowA,lowB))
    }
    mesh(Primitives.flatMesh("hull",triangles,"hull"))
    model("water",ModelPart("lake")); model("ground",ModelPart("ground")); model("bank",ModelPart("bank"))
    model("tree",ModelPart("trunk",Transform3D(Vec3(0f,57f,0f))),
        ModelPart("crown",Transform3D(Vec3(0f,60f,0f))),
        ModelPart("crown",Transform3D(Vec3(0f,130f,0f),scale=Vec3(.7f,.7f,.7f))))
    // The hull is a material slot, so each player's boat can wear its own colour.
    model("boat",ModelPart("hull",materialSlot="hull"),ModelPart("deck",Transform3D(Vec3(-10f,3f,0f))),
        ModelPart("cabin",Transform3D(Vec3(-28f,30f,0f))),
        ModelPart("window",Transform3D(Vec3(6f,32f,0f))))
    model("buoy",ModelPart("buoy-body"),ModelPart("buoy-lamp",Transform3D(Vec3(0f,78f,0f))))
    weatherParticles()
}

/** What the scene looks like, independent of where the camera is. */
data class LakeLook(
    val sunlight: Float = 1.876f,
    val weather: Weather = Weather.Clear,
    val whirlpools: Boolean = true,
    val buoy: Boolean = true,
)

/** The original fixed view over the whole lake. */
fun overviewCamera(zoom: Float) = GpuCamera(Vec3(1650f*zoom,2300f*zoom,2400f*zoom),Vec3.ZERO,
    verticalFovDegrees=48f,nearPlane=10f,farPlane=12000f)

/** The game's chase framing, scaled up for Lake Lab's larger boats. */
fun lakeChaseStyle(zoom: Float) = ChaseCameraStyle().scaled(2.9f*zoom)

/** The dish shades as water this deep, matching the lake around the whirlpools so the rim blends. */
private val lakeVortex = VortexConfig(dishDepth=.62f)

private val playerHulls = listOf("hull","hull-blue")

/**
 * One view of the lake. [viewer] is whose hull makes the whirlpool water react in this view.
 * [alpha] interpolates boats between fixed simulation steps.
 */
fun lakeFrame(assets: GpuSceneAssets, camera: GpuCamera, boats: List<BoatMotion>, water: WaterConfig,
              seconds: Float, tick: Long, look: LakeLook, alpha: Float = 1f,
              importedModelId: String? = null, viewer: BoatMotion? = boats.firstOrNull()): GpuSceneFrame {
    val base = SceneEnvironment(clearColor=PackedColor(0xff4ec8ff.toInt()),ambientColor=PackedColor(0xfffff6e8.toInt()),
        ambientIntensity=.88f,fogColor=PackedColor(0xff9edcf0.toInt()),fogStart=2600f,fogEnd=8400f)
    val sun = look.sunlight*look.weather.sunlight
    val surge = if(look.whirlpools && viewer != null) {
        val (x,z,_) = viewer.pose(alpha)
        lakePools.maxBy { it.influenceAt(x,z) }.surge(x,z)
    } else VortexSurge()
    return assets.frame(camera=camera,seconds=seconds,tick=tick,water=water,
        environment=look.weather.environment(base),
        lights=listOf(DirectionalLight(Vec3(-.38f,-1f,-.22f).normalized(),PackedColor(0xfffff3c4.toInt()),sun,false),
            DirectionalLight(Vec3(.62f,-.55f,.48f).normalized(),PackedColor(0xffc4f0ff.toInt()),.38f*look.weather.sunlight,false)),
        vortex=lakeVortex,vortexSurge=surge) {
        instance("ground","ground",Transform3D(Vec3(0f,-65f,0f)))
        instance("water","water")
        for(side in 0..3) {
            val angle = side * PI.toFloat()/2
            instance("bank-$side","bank",Transform3D(Vec3(sin(angle)*1200f,16f,cos(angle)*1200f),Vec3(0f,angle,0f)))
            for(i in -4..4) {
                val along = i*235f
                val x = along*cos(angle)+1200f*sin(angle)
                val z = -along*sin(angle)+1200f*cos(angle)
                val scale = .8f+((i+4+side)%3)*.14f
                instance("tree-$side-$i","tree",Transform3D(Vec3(x,32f,z),scale=Vec3(scale,scale,scale)))
            }
        }
        if(look.whirlpools) lakePools.forEachIndexed { i, pool ->
            instance("pool-$i",if(pool.clockwise) "pool-cw" else "pool-ccw",Transform3D(Vec3(pool.x,0f,pool.z)))
        }
        if(look.buoy) instance("buoy","buoy",Buoyancy.pose(water,buoyPosition.x,buoyPosition.z,.3f,seconds,lift=-8f,
            energy=::lakeEnergyAt),kind=InstanceKind.Dynamic)
        boats.forEachIndexed { i, boat ->
            val imported = importedModelId != null
            instance("boat-$i",importedModelId ?: "boat",boat.transform(water,seconds,alpha,imported),kind=InstanceKind.Dynamic,
                materials=if(imported) emptyMap() else mapOf("hull" to playerHulls[i % playerHulls.size]))
        }
        weather(look.weather,camera,seconds,density=2.5f,scale=1.4f)
    }
}
