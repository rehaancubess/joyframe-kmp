// SPDX-License-Identifier: Apache-2.0
package example

import io.github.rehaancubess.joyframe.input.Movement
import io.github.rehaancubess.joyframe.render.*
import io.github.rehaancubess.joyframe.render.gpu.*
import io.github.rehaancubess.joyframe.render.math.Vec3
import io.github.rehaancubess.joyframe.render.water.WaterConfig
import io.github.rehaancubess.joyframe.render.water.WaterField
import io.github.rehaancubess.joyframe.render.gltf.GltfModel
import kotlin.math.*

// The lagoon's shoreline ramps: wave energy and depth rise with distance from land.
private val shore=WaterField(Array(44) { x -> IntArray(44) { z -> minOf(x,z,43-x,43-z) } },50f)
fun lakeEnergyAt(x: Float,z: Float)=shore.energyAt(x+1100f,z+1100f)
private fun lakeDepthAt(x: Float,z: Float)=shore.depthAt(x+1100f,z+1100f)
fun lagoonWater()=WaterConfig(rippleStrength=.44f,foamIntensity=1.30f,fresnelStrength=.54f)

/** Procedural fallback geometry. An optional local GLB is loaded separately by PreviewBoat. */
val lakeAssets = sceneAssets("joyframe-lake-v1") {
    material("water",0xff14b4c8.toInt(),water=true)
    material("earth",0xffd4c193.toInt()); material("grass",0xff8fab70.toInt())
    material("trunk",0xff7d5847.toInt()); material("pine",0xff32766d.toInt())
    material("hull",0xffef7755.toInt()); material("cream",0xfffff0ca.toInt())
    material("glass",0xff274653.toInt())
    val surface=Primitives.water("lake",2200f,"water",80)
    mesh(surface.copy(textureCoordinates=surface.positions.map { Vec2f(lakeEnergyAt(it.x,it.z),lakeDepthAt(it.x,it.z)) }))
    box("ground",Vec3(2600f,80f,2600f),"earth")
    box("bank",Vec3(2600f,32f,200f),"grass")
    box("trunk",Vec3(27f,115f,27f),"trunk")
    cone("crown",90f,200f,"pine")
    box("deck",Vec3(145f,17f,75f),"cream")
    box("cabin",Vec3(64f,46f,58f),"cream")
    box("window",Vec3(5f,27f,48f),"glass")
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
    model("boat",ModelPart("hull"),ModelPart("deck",Transform3D(Vec3(-10f,3f,0f))),
        ModelPart("cabin",Transform3D(Vec3(-28f,30f,0f))),
        ModelPart("window",Transform3D(Vec3(6f,32f,0f))))
}

/** Sample-only movement, not a game rule built into the toolkit. */
class BoatMotion {
    var x = 0f; private set
    var z = 0f; private set
    var yaw = 0f; private set
    fun advance(movement: Movement, dt: Float) {
        require(dt.isFinite() && dt >= 0)
        x = (x + movement.x * 420f * dt).coerceIn(-900f,900f)
        z = (z - movement.y * 420f * dt).coerceIn(-900f,900f)
        if(abs(movement.x)+abs(movement.y) > .01f) {
            val target = atan2(movement.y,movement.x)
            val delta = atan2(sin(target-yaw),cos(target-yaw))
            yaw += delta * (1f-exp(-dt*9f))
        }
    }
    fun reset() { x=0f; z=0f; yaw=0f }
    fun transform(water: WaterConfig, seconds: Float, imported: Boolean = false): Transform3D {
        val energy=lakeEnergyAt(x,z)
        val slope = water.slopeAt(x,z,seconds,energy)
        val pitch = atan(slope.alongX*cos(yaw)-slope.alongZ*sin(yaw))
        val roll = -atan(slope.alongX*sin(yaw)+slope.alongZ*cos(yaw))
        val size=if(imported) 1f else 1.6f
        return Transform3D(Vec3(x,water.heightAt(x,z,seconds,energy)+(if(imported) 0f else 24f),z),
            Vec3(roll,yaw,pitch),Vec3(size,size,size))
    }
}

fun lakeFrame(boat: BoatMotion, water: WaterConfig, seconds: Float, tick: Long, zoom: Float, sunlight: Float,
              assets: GpuSceneAssets = lakeAssets, importedModelId: String? = null) = assets.frame(
    camera=GpuCamera(Vec3(1650f*zoom,2300f*zoom,2400f*zoom),Vec3.ZERO,
        verticalFovDegrees=48f,nearPlane=10f,farPlane=12000f),
    seconds=seconds,tick=tick,water=water,
    environment=SceneEnvironment(clearColor=PackedColor(0xff4ec8ff.toInt()),ambientColor=PackedColor(0xfffff6e8.toInt()),
        ambientIntensity=.88f,fogColor=PackedColor(0xff9edcf0.toInt()),fogStart=2600f,fogEnd=8400f),
    lights=listOf(DirectionalLight(Vec3(-.38f,-1f,-.22f).normalized(),PackedColor(0xfffff3c4.toInt()),sunlight,false),
        DirectionalLight(Vec3(.62f,-.55f,.48f).normalized(),PackedColor(0xffc4f0ff.toInt()),.38f,false))) {
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
    instance("boat",importedModelId ?: "boat",boat.transform(water,seconds,importedModelId!=null),kind=InstanceKind.Dynamic)
}
