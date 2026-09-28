package io.github.rehaancubess.joyframe

import io.github.rehaancubess.joyframe.render.Primitives
import io.github.rehaancubess.joyframe.render.gltf.*
import io.github.rehaancubess.joyframe.render.gpu.*
import io.github.rehaancubess.joyframe.render.math.Vec3
import kotlin.math.PI
import kotlin.test.*

class ModelFitTest {
    private fun model(): GltfModel {
        val mesh=Primitives.box("box",Vec3(2f,4f,6f),"m")
        return GltfModel(mapOf("box" to mesh),mapOf("m" to GpuMaterial("m",PackedColor.White)),emptyMap(),
            ModelDefinition("test",listOf(ModelPart("box"))))
    }
    @Test fun fitTurnsSizesAndSinksWithoutMutatingSource() {
        val source=model()
        val fit=source.fitTo(300f,156f,PI.toFloat()/2,22f).meshes.getValue("box")
        assertEquals(300f,fit.bounds.maximum.x-fit.bounds.minimum.x,.001f)
        assertEquals(156f,fit.bounds.maximum.z-fit.bounds.minimum.z,.001f)
        assertEquals(-22f,fit.bounds.minimum.y,.001f)
        fit.normals.forEach { assertEquals(1f,it.length(),.0001f) }
        assertEquals(-2f,source.meshes.getValue("box").bounds.minimum.y)
    }
    @Test fun fitRejectsInvalidDimensions() {
        assertFailsWith<IllegalArgumentException> { model().fitTo(0f,1f) }
        assertFailsWith<IllegalArgumentException> { model().fitTo(1f,Float.NaN) }
    }
}
