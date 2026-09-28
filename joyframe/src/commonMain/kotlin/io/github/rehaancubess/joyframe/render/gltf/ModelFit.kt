// SPDX-License-Identifier: Apache-2.0
package io.github.rehaancubess.joyframe.render.gltf

import io.github.rehaancubess.joyframe.render.gpu.Bounds3
import io.github.rehaancubess.joyframe.render.math.Vec3
import kotlin.math.cos
import kotlin.math.sin

/** Fit an imported static model to an X-forward footprint. Y uses length scale; beam may differ.
 * Normals use inverse-transpose scaling. Sink lowers the fitted bottom below model origin.
 */
fun GltfModel.fitTo(length: Float, beam: Float, bowYawRadians: Float = 0f, sink: Float = 0f): GltfModel {
    require(length.isFinite() && length>0 && beam.isFinite() && beam>0 && bowYawRadians.isFinite() && sink.isFinite())
    require(meshes.isNotEmpty())
    val positions=meshes.values.flatMap { it.positions }
    require(positions.isNotEmpty())
    val cx=(positions.minOf { it.x }+positions.maxOf { it.x })/2
    val cz=(positions.minOf { it.z }+positions.maxOf { it.z })/2
    val bottom=positions.minOf { it.y }
    val c=cos(bowYawRadians); val s=sin(bowYawRadians)
    fun turn(p: Vec3)=Vec3((p.x-cx)*c+(p.z-cz)*s,p.y-bottom,-(p.x-cx)*s+(p.z-cz)*c)
    val turned=positions.map(::turn)
    val extentX=turned.maxOf { it.x }-turned.minOf { it.x }
    val extentZ=turned.maxOf { it.z }-turned.minOf { it.z }
    require(extentX>1e-6f && extentZ>1e-6f) { "Model footprint must have length and width" }
    val sx=length/extentX; val sz=beam/extentZ
    val fitted=meshes.mapValues { (_,mesh) ->
        val vertices=mesh.positions.map { p -> turn(p).let { Vec3(it.x*sx,it.y*sx-sink,it.z*sz) } }
        mesh.copy(positions=vertices,normals=mesh.normals.map { n ->
            Vec3((n.x*c+n.z*s)/sx,n.y/sx,(-n.x*s+n.z*c)/sz).normalized()
        },bounds=Bounds3(Vec3(vertices.minOf{it.x},vertices.minOf{it.y},vertices.minOf{it.z}),
            Vec3(vertices.maxOf{it.x},vertices.maxOf{it.y},vertices.maxOf{it.z})))
    }
    return GltfModel(fitted,materials,textures,model)
}
