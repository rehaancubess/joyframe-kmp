// SPDX-License-Identifier: Apache-2.0
package io.github.rehaancubess.joyframe.render.metal

import io.github.rehaancubess.joyframe.render.water.WaterConfig
import io.github.rehaancubess.joyframe.render.water.WaterQuality

private val WATER_DEFINES: String = buildString {
    append("#define WATER_SWELL_TRAINS ").append(WaterConfig.SWELL_TRAINS).append('\n')
    append("#define WATER_QUALITY_MEDIUM ").append(glslFloat(WaterQuality.Medium.shaderLevel)).append('\n')
    append("#define WATER_QUALITY_HIGH ").append(glslFloat(WaterQuality.High.shaderLevel)).append('\n')
}

private fun glslFloat(value: Float): String =
    if (value % 1f == 0f) "${value.toInt()}.0" else value.toString()

internal val SCENE_SHADER_SOURCE: String = WATER_DEFINES + SCENE_SHADER_BODY

private const val SCENE_SHADER_BODY = """
#include <metal_stdlib>
using namespace metal;

struct Vertex {
    packed_float3 position;
    packed_float3 normal;
    packed_float2 uv;
};

struct FrameUniforms {
    float4x4 viewProjection;
    float4x4 lightViewProjection;
    float4 cameraPosition;   // xyz
    float4 lightDirection;   // xyz = direction the light travels
    float4 lightColor;       // rgb premultiplied by intensity
    float4 fillDirection;    // xyz = secondary bounce light
    float4 fillColor;        // rgb premultiplied by intensity
    float4 ambientColor;     // rgb premultiplied by intensity
    float4 fogColor;         // rgb
    float4 fogParams;        // x = start, y = end, z = maximum density
    float4 shadowParams;     // x = texel size, y = depth bias, z = strength, w = exposure
    float4 waterWaves[WATER_SWELL_TRAINS];  // xy = wave vector, z = angular speed, w = amplitude
    float4 waterShallow;     // rgb = sunlit shallow colour, a = 1 / total swell amplitude
    float4 waterDeep;        // rgb = open water colour,     a = depth tint strength
    float4 waterOptics;      // x = fresnel strength, y = fresnel hardness, z = glint, w = sharpness
    float4 waterDetail;      // x = ripple strength, y = ripple scale, z = ripple speed, w = tier
    float4 waterLod;         // x = fade start, y = fade end, z = caustic scale, w = caustic speed
    float4 waterFoam;        // x = crest start, y = crest end, z = foam, w = caustic intensity
    float4 waterSurf;        // x = surf edge, y = surge, z = patch scale, w = shallow calm
    float4 vortexMask;       // x = dish energy, y = depth mask, z = hull reaction, w = throat
    float4 vortexSpin;       // x = rotation, y = inflow, z = arms, w = spiral tightness
    float4 vortexChurn;      // x = distortion, y = turbulence, z = foam amount, w = foam speed
    float4 vortexLook;       // x = foam breakup, y = eye darkness, z = ripples, w = highlights
    float4 vortexHull;       // xy = hull position, z = its suction 0..1, w = 1 / reach
    float4 vortexDeath;      // xy = swallow position, z = envelope 0..1, w = climax strength
    float4 waterTime;        // x = seconds
};

struct DrawUniforms {
    float4x4 model;
    float4x4 normalMatrix;
    float4 baseColor;        // rgba, straight (non-premultiplied)
    float4 emissive;         // rgb premultiplied by strength, w = soap-bubble rim flag
    float4 materialParams;   // x = roughness, y = metallic, z = receives shadow, w = has albedo
    float4 waterParams;      // x = water shade, y = vortex, z = is foam, w = foam age 0..1
};

struct SkyUniforms {
    float4 forward;          // xyz = camera forward, w = tan(fovY / 2)
    float4 right;            // xyz = camera right,   w = aspect ratio
    float4 up;               // xyz = camera up,      w = time in seconds
    float4 zenithColor;
    float4 horizonColor;
    float4 cloudColor;
};

struct SceneVertexOut {
    float4 position [[position]];
    float3 worldPosition;
    float3 worldNormal;
    float4 lightClip;
    float2 uv;

    float waterCrest;

    float waterFade;
};

static float3 waterWaves(constant FrameUniforms& frame, float2 position, float time) {
    float3 wave = float3(0.0);
    const float peak = 0.16;
    for (int index = 0; index < WATER_SWELL_TRAINS; ++index) {
        float4 train = frame.waterWaves[index];
        float phase = dot(position, train.xy) + time * train.z;
        float s = sin(phase);
        float c = cos(phase);
        wave.x += (s + peak * s * s * s) * train.w;
        wave.yz += c * (1.0 + 3.0 * peak * s * s) * train.w * train.xy;
    }
    return wave;
}

vertex SceneVertexOut scene_vertex(
    uint vid [[vertex_id]],
    device const Vertex* vertices [[buffer(0)]],
    constant FrameUniforms& frame [[buffer(1)]],
    constant DrawUniforms& draw [[buffer(2)]]
) {
    Vertex v = vertices[vid];
    float4 world = draw.model * float4(v.position, 1.0);
    float3 normal = (draw.normalMatrix * float4(v.normal, 0.0)).xyz;
    float crest = 0.0;
    float fade = 1.0;

    if (draw.waterParams.y > 0.5) {
        float energy = frame.vortexMask.x;
        float3 wave = waterWaves(frame, world.xz, frame.waterTime.x);
        world.y += wave.x * energy;
        normal = normalize(normal + float3(-wave.y * energy, 0.0, -wave.z * energy));
        crest = wave.x * frame.waterShallow.a;
    } else if (draw.waterParams.x > 0.0) {
        float energy = v.uv.x;
        float3 wave = waterWaves(frame, world.xz, frame.waterTime.x);
        world.y += wave.x * energy;
        fade = v.normal.x;
        normal = normalize(float3(-wave.y * energy, 1.0, -wave.z * energy));
        crest = wave.x * frame.waterShallow.a;
    }

    SceneVertexOut out;
    out.position = frame.viewProjection * world;
    out.worldPosition = world.xyz;
    out.worldNormal = normal;
    out.lightClip = frame.lightViewProjection * world;
    out.uv = v.uv;
    out.waterCrest = crest;
    out.waterFade = fade;
    return out;
}

constexpr sampler shadowSampler(
    coord::normalized,
    filter::linear,
    address::clamp_to_edge,
    compare_func::less
);

static float sampleShadow(depth2d<float> map, float4 lightClip, float texel, float bias) {
    float3 projected = lightClip.xyz / max(lightClip.w, 1e-4);
    float2 uv = projected.xy * float2(0.5, -0.5) + 0.5;
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0 || projected.z > 1.0) return 1.0;

    float reference = projected.z - bias;
    float sum = 0.0;
    for (int y = -1; y <= 1; ++y) {
        for (int x = -1; x <= 1; ++x) {
            sum += map.sample_compare(shadowSampler, uv + float2(x, y) * texel, reference);
        }
    }
    return sum / 9.0;
}

constexpr sampler albedoSampler(
    coord::normalized,
    filter::linear,
    mip_filter::linear,
    address::repeat
);

constant float WATER_CAUSTIC_REFRACTION = 140.0;

static float foamBubbles(float2 position, float time) {
    const float scale = 0.055;
    float2 drift = position * scale + float2(time * 0.09, time * -0.06);
    float a = sin(drift.x * 1.7 + drift.y * 1.1);
    float b = sin(drift.x * -0.9 + drift.y * 2.3 + 1.7);
    float c = sin((drift.x + drift.y) * 3.1 - time * 0.4);
    return saturate(0.5 + 0.34 * (a * b + c * 0.5));
}

constant float VORTEX_EYE = 0.085;
constant float TWO_PI = 6.2831853;

constant float VORTEX_SPIRAL_GAIN = 3.1;

constant float VORTEX_SPIN_FLOOR = 0.26;

constant float VORTEX_THROAT_TONE = 0.72;

constant float VORTEX_BAND_SHADING = 0.36;

constant float VORTEX_NORMAL_TILT = 0.24;

static float vortexBand(float angle, float winding, float spinRate, float time, float arms, float tightness, float speed) {
    return sin(angle * arms + winding * tightness + time * speed * spinRate);
}

static float2 waterRippleSlope(constant FrameUniforms& frame, float2 position, float time, float fine) {
    float scale = frame.waterDetail.y;
    float speed = frame.waterDetail.z;
    const float2 dirA1 = float2(0.862, 0.507);
    const float2 dirA2 = float2(-0.391, 0.920);
    const float2 dirB1 = float2(0.643, -0.766);
    const float2 dirB2 = float2(0.970, 0.242);
    const float2 dirB3 = float2(-0.812, 0.584);
    float2 slope = cos(dot(position, dirA1) * scale + time * speed) * dirA1 * 0.85;
    slope += cos(dot(position, dirA2) * scale * 1.43 - time * speed * 0.77) * dirA2 * 0.95;
    if (fine > 0.0) {
        slope += cos(dot(position, dirB1) * scale * 2.31 + time * speed * 1.62) * dirB1 * 0.70 * fine;
        slope += cos(dot(position, dirB2) * scale * 3.77 - time * speed * 2.19) * dirB2 * 0.55 * fine;
        slope += cos(dot(position, dirB3) * scale * 5.13 + time * speed * 2.71) * dirB3 * 0.38 * fine;
    }
    return slope * mix(1.35, 1.0, fine);
}

static float waterChopPatches(constant FrameUniforms& frame, float2 position, float time) {
    float band = sin(dot(position, float2(0.71, 0.70)) * frame.waterDetail.y * frame.waterSurf.z + time * 0.11);
    return 0.42 + 0.58 * (band * 0.5 + 0.5);
}

static float waterSurfBreakup(float2 position, float time) {
    float a = sin(dot(position, float2(0.93, 0.37)) * 0.021 + time * 0.90);
    float b = sin(dot(position, float2(-0.41, 0.91)) * 0.013 - time * 0.61);
    float c = sin(dot(position, float2(0.22, -0.97)) * 0.034 + time * 1.35);
    return 0.48 + 0.52 * a * b * mix(0.55, 1.0, c * 0.5 + 0.5);
}

static float waterCaustic(constant FrameUniforms& frame, float2 position, float time) {
    float scale = frame.waterLod.z;
    float speed = frame.waterLod.w;
    float a = sin(dot(position, float2(0.724, 0.690)) * scale + time * speed);
    float b = sin(dot(position, float2(-0.659, 0.752)) * scale * 1.73 - time * speed * 0.83);
    float c = sin(dot(position, float2(0.180, -0.984)) * scale * 2.41 + time * speed * 1.19);
    float filaments = saturate(1.0 - abs(a * 0.55 + b * 0.45));
    float spark = saturate(1.0 - abs(b * 0.48 + c * 0.52));
    return pow(filaments, 4.0) * mix(0.28, 1.0, pow(spark, 3.0));
}

static float waterSparkle(float2 position, float time) {
    float a = sin(dot(position, float2(0.173, 0.985)) * 0.37 + time * 4.6);
    float b = sin(dot(position, float2(0.941, -0.338)) * 0.51 - time * 3.3);
    float c = sin(dot(position, float2(-0.582, 0.813)) * 0.73 + time * 5.8);
    float cluster = saturate(a * b * 0.55 + c * 0.45);
    return 0.05 + 0.95 * pow(cluster, 8.0);
}

fragment float4 scene_fragment(
    SceneVertexOut in [[stage_in]],
    constant FrameUniforms& frame [[buffer(1)]],
    constant DrawUniforms& draw [[buffer(2)]],
    depth2d<float> shadowMap [[texture(0)]],
    texture2d<float> albedoMap [[texture(1)]]
) {
    float4 base = draw.baseColor;
    if (draw.materialParams.w > 0.5) base *= albedoMap.sample(albedoSampler, in.uv);
    if (draw.waterParams.z > 0.0) {
        float2 local = in.uv * 2.0 - 1.0;
        float age = draw.waterParams.w;
        float footprint = saturate(1.0 - dot(local, local));
        float streak = 0.62 + 0.38 * sin(local.y * 7.0 + local.x * 2.3);
        float bubbles = foamBubbles(in.worldPosition.xz, frame.waterTime.x);
        float threshold = mix(0.16, 0.92, age * age);
        float mask = smoothstep(threshold, threshold + 0.26, footprint * streak * (0.45 + bubbles));
        base.a *= mask;
    }

    float3 albedo = base.rgb;
    float3 normal = normalize(in.worldNormal);
    float3 toLight = normalize(-frame.lightDirection.xyz);
    float3 toFill = normalize(-frame.fillDirection.xyz);
    float3 toView = normalize(frame.cameraPosition.xyz - in.worldPosition);
    float viewDistance = length(frame.cameraPosition.xyz - in.worldPosition);
    float waterShade = draw.waterParams.x;
    float isVortex = draw.waterParams.y;
    float waterEnergy = 0.0;
    float waterDepth = 0.0;
    float waterDetail = 0.0;
    float vortexRadius = 0.0;
    float vortexRim = 0.0;
    float vortexFlow = 0.0;
    float vortexSurge = 0.0;
    float vortexClimax = 0.0;
    float vortexDetail = 0.0;
    if (waterShade > 0.0) {
        waterEnergy = isVortex > 0.5 ? frame.vortexMask.x : in.uv.x;
        waterDepth = isVortex > 0.5 ? frame.vortexMask.y : in.uv.y;
        waterDetail = 1.0 - smoothstep(frame.waterLod.x, frame.waterLod.y, viewDistance);
        if (frame.waterDetail.w >= WATER_QUALITY_MEDIUM && waterDetail > 0.0) {
            float2 slope = waterRippleSlope(frame, in.worldPosition.xz, frame.waterTime.x, step(WATER_QUALITY_HIGH, frame.waterDetail.w));
            float chop = waterChopPatches(frame, in.worldPosition.xz, frame.waterTime.x);
            chop *= 0.72 + 0.28 * (in.waterCrest * 0.5 + 0.5);
            chop *= mix(frame.waterSurf.w, 1.0, waterDepth);
            normal = normalize(normal + float3(-slope.x, 0.0, -slope.y) *
                (frame.waterDetail.x * waterDetail * chop));
        }

        if (isVortex > 0.5) {
            vortexRadius = saturate(in.uv.x);
            float angle = in.uv.y * TWO_PI;
            float winding = log(max(vortexRadius, VORTEX_EYE)) * VORTEX_SPIRAL_GAIN;
            float spinRate = 1.0 / max(vortexRadius, VORTEX_SPIN_FLOOR);
            vortexRim = smoothstep(1.0, 0.62, vortexRadius);
            float2 toHull = (in.worldPosition.xz - frame.vortexHull.xy) * frame.vortexHull.w;
            float2 toGrave = (in.worldPosition.xz - frame.vortexDeath.xy) * frame.vortexHull.w;
            vortexClimax = frame.vortexDeath.z * frame.vortexDeath.w * saturate(1.0 - dot(toGrave, toGrave) * 0.4);
            vortexSurge = saturate(
                frame.vortexHull.z * saturate(1.0 - dot(toHull, toHull)) * frame.vortexMask.z + vortexClimax);
            vortexDetail = waterDetail * step(WATER_QUALITY_MEDIUM, frame.waterDetail.w);
            vortexFlow = vortexBand(angle, winding, spinRate, frame.waterTime.x, frame.vortexSpin.z, frame.vortexSpin.w, frame.vortexSpin.x) * 0.50;
            vortexFlow += vortexBand(angle, winding, spinRate, frame.waterTime.x, frame.vortexSpin.z * 2.5, -frame.vortexSpin.w * 0.62, frame.vortexSpin.x * 1.55) * 0.32;
            if (vortexDetail > 0.0) {
                vortexFlow += vortexBand(angle, winding, spinRate, frame.waterTime.x, frame.vortexSpin.z * 4.5, frame.vortexSpin.w * 1.6, frame.vortexSpin.x * 0.71) *
                    0.18 * frame.vortexChurn.y * vortexDetail;
                float ripple = sin(vortexRadius * 26.0 - frame.waterTime.x * frame.vortexSpin.y * 9.0 + vortexFlow * frame.vortexChurn.x * 4.0);
                vortexFlow += ripple * frame.vortexLook.z * vortexRim * vortexDetail;
            }
            float2 tangent = float2(-sin(angle), cos(angle));
            normal = normalize(normal + float3(tangent.x, 0.0, tangent.y) *
                (vortexFlow * VORTEX_NORMAL_TILT * vortexRim * (1.0 + vortexSurge)));
        }
        float3 depthColor = mix(frame.waterShallow.rgb, frame.waterDeep.rgb, waterDepth);
        depthColor *= 1.0 + in.waterCrest * waterEnergy * 1.18;
        depthColor *= 1.0 - saturate(-in.waterCrest) * waterEnergy * 0.55;
        depthColor = mix(depthColor, depthColor * float3(0.86, 1.02, 1.08), waterDepth * 0.35);
        albedo = mix(albedo, depthColor, frame.waterDeep.a * waterShade * waterShade);
    }
    const float wrap = 0.26;
    float key = saturate((dot(normal, toLight) + wrap) / (1.0 + wrap));
    float fill = saturate((dot(normal, toFill) + wrap) / (1.0 + wrap));

    float shadow = 1.0;
    if (draw.materialParams.z > 0.5 && frame.shadowParams.z > 0.001) {
        float lit = sampleShadow(shadowMap, in.lightClip, frame.shadowParams.x, frame.shadowParams.y);
        shadow = mix(1.0, lit, frame.shadowParams.z);
    }

    float roughness = clamp(draw.materialParams.x, 0.05, 1.0);
    float metallic = saturate(draw.materialParams.y);
    float3 halfway = normalize(toLight + toView);
    float shininess = mix(mix(110.0, 6.0, roughness), frame.waterOptics.w, waterShade);
    float highlight = pow(saturate(dot(normal, halfway)), shininess);
    float specular = highlight * (1.0 - roughness) * (1.0 - waterShade);
    float3 specularTint = mix(float3(1.0), albedo, metallic);

    float3 color = albedo * frame.ambientColor.rgb;
    color += albedo * frame.lightColor.rgb * key * shadow;
    color += albedo * frame.fillColor.rgb * fill;
    color += specularTint * frame.lightColor.rgb * specular * shadow * mix(0.35, 0.9, metallic);
    color = 1.0 - exp(-color * frame.shadowParams.w);
    const float saturation = 1.24;
    const float contrast = 0.20;
    float luma = dot(color, float3(0.2126, 0.7152, 0.0722));
    color = clamp(luma + (color - luma) * saturation, 0.0, 1.0);
    color = mix(color, smoothstep(0.0, 1.0, color), contrast);

    color += draw.emissive.rgb;
    if (waterShade > 0.0) {
        float grazing = 1.0 - saturate(dot(normal, toView));
        float grazing2 = grazing * grazing;
        float fresnel = frame.waterOptics.x * mix(grazing2, grazing2 * grazing2, frame.waterOptics.y) * waterShade;
        float3 refl = reflect(-toView, normal);
        float skyH = saturate(refl.y * 1.4);
        float3 skyHorizon = clamp(frame.fogColor.rgb * 0.28 + float3(0.70, 0.88, 0.98) * 0.72, 0.0, 1.0);
        float3 skyZenith = clamp(frame.fogColor.rgb * 0.12 + float3(0.18, 0.48, 0.92) * 0.88, 0.0, 1.0);
        float3 skyReflection = mix(skyHorizon, skyZenith, skyH);
        float sunSpec = pow(saturate(dot(refl, toLight)), 12.0);
        skyReflection = mix(skyReflection, clamp(frame.lightColor.rgb * 0.55 + float3(1.0, 0.94, 0.78) * 0.50, 0.0, 1.0), sunSpec);
        color = mix(color, skyReflection, saturate(fresnel));

        float trans = saturate(in.waterCrest * 0.62 + 0.16) * saturate(1.0 - waterDepth * 0.88);
        float scatter = saturate(dot(toView, -toLight) * 0.50 + 0.50);
        color += frame.waterShallow.rgb * trans * scatter * 0.26 * waterShade * waterEnergy;

        float sparkle = waterSparkle(in.worldPosition.xz, frame.waterTime.x);
        float glintReach = mix(0.08, 0.78, waterDetail);
        float tight = pow(saturate(dot(normal, halfway)), shininess * 2.6);
        color += frame.lightColor.rgb * (highlight * 0.50 + tight) * frame.waterOptics.z * waterShade * sparkle * glintReach;
        float surfDepth = waterDepth - in.waterCrest * frame.waterSurf.y;
        float surf = smoothstep(frame.waterSurf.x, frame.waterSurf.x * 0.15, surfDepth);
        if (surf > 0.0) {
            surf *= waterSurfBreakup(in.worldPosition.xz, frame.waterTime.x);
            color = mix(color, float3(0.92, 0.97, 1.0), saturate(surf * frame.waterFoam.z * waterShade));
        }
        if (isVortex > 0.5) {
            color *= 1.0 + vortexFlow * VORTEX_BAND_SHADING * vortexRim * mix(0.55, 1.0, key);
            float eye = smoothstep(frame.vortexMask.w, 0.0, vortexRadius);
            float darkness = saturate(frame.vortexLook.y * (1.0 + vortexClimax * 0.45));
            color = mix(color, frame.waterDeep.rgb * VORTEX_THROAT_TONE, eye * darkness * waterShade);
            float sheen = pow(saturate(vortexFlow * 0.5 + 0.5), 6.0) * smoothstep(0.0, 0.26, vortexRadius);
            color += frame.lightColor.rgb * sheen * frame.vortexLook.w * vortexRim * key * (0.6 + vortexSurge);
            float foamBand = saturate(vortexFlow * 0.5 + 0.5);
            if (vortexDetail > 0.0) {
                float tear = sin(in.uv.y * TWO_PI * 9.0 +
                    log(max(vortexRadius, VORTEX_EYE)) * VORTEX_SPIRAL_GAIN * 2.1 -
                    frame.waterTime.x * frame.vortexChurn.w * 1.7);
                foamBand *= mix(1.0, 0.35 + 0.65 * saturate(tear * 0.5 + 0.5), frame.vortexLook.x * vortexDetail);
            }
            float band = smoothstep(0.10, 0.30, vortexRadius) * smoothstep(1.0, 0.62, vortexRadius);
            float foam = smoothstep(0.60, 0.90, foamBand) * band * frame.vortexChurn.z;
            float closing = 1.0 - frame.vortexDeath.z;
            foam += smoothstep(0.10, 0.0, abs(vortexRadius - closing)) * frame.vortexDeath.z * frame.vortexDeath.w * 0.9;

            color = mix(color, float3(1.0), saturate(foam * (1.0 + vortexSurge) * vortexRim * waterShade));
        }

        if (frame.waterDetail.w >= WATER_QUALITY_HIGH && waterDetail > 0.12) {
            float shallow = pow(saturate(1.0 - waterDepth), 1.65) * waterDetail;
            if (shallow > 0.08) {
                float2 causticPoint = in.worldPosition.xz + normal.xz * WATER_CAUSTIC_REFRACTION;
                color += frame.waterShallow.rgb * waterCaustic(frame, causticPoint, frame.waterTime.x) *
                    frame.waterFoam.w * shallow * waterShade * mix(0.70, 1.15, key);
                color += frame.waterShallow.rgb * waterCaustic(frame, causticPoint * 1.67, frame.waterTime.x * 0.71) *
                    frame.waterFoam.w * shallow * 0.45 * waterShade;
            }

            float crest = smoothstep(frame.waterFoam.x, frame.waterFoam.y, in.waterCrest) * waterEnergy;
            if (crest > 0.0) {
                float torn = 0.42 + 0.58 * waterCaustic(frame, in.worldPosition.xz * 2.3, frame.waterTime.x * 1.7);
                color = mix(color, float3(0.93, 0.98, 1.0), saturate(crest * torn * frame.waterFoam.z * waterShade * 0.85));
            }
        }
    }

    float fogRange = max(frame.fogParams.y - frame.fogParams.x, 1.0);
    float fog = saturate((viewDistance - frame.fogParams.x) / fogRange) * frame.fogParams.z;
    color = mix(color, frame.fogColor.rgb, fog);
    float alpha = base.a * in.waterFade;
    if (draw.emissive.w > 0.5) {
        float grazing = 1.0 - saturate(abs(dot(normal, toView)));
        float rim = grazing * grazing;
        color = mix(color, float3(0.90, 0.99, 1.0), saturate(rim * 1.25));
        alpha = mix(0.05, 0.78, rim) * in.waterFade * base.a;
    }
    return float4(color, alpha);
}

vertex float4 shadow_vertex(
    uint vid [[vertex_id]],
    device const Vertex* vertices [[buffer(0)]],
    constant FrameUniforms& frame [[buffer(1)]],
    constant DrawUniforms& draw [[buffer(2)]]
) {
    float4 world = draw.model * float4(vertices[vid].position, 1.0);
    return frame.lightViewProjection * world;
}

struct SkyVertexOut {
    float4 position [[position]];
    float2 ndc;
};

vertex SkyVertexOut sky_vertex(uint vid [[vertex_id]]) {
    float2 corner = float2((vid << 1) & 2, vid & 2);
    SkyVertexOut out;
    out.ndc = corner * 2.0 - 1.0;
    out.position = float4(out.ndc, 1.0, 1.0);
    return out;
}

static float skyHash(float2 p) {
    p = fract(p * float2(127.31, 311.7));
    p += dot(p, p + 34.53);
    return fract(p.x * p.y);
}

static float skyNoise(float2 p) {
    float2 cell = floor(p);
    float2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = skyHash(cell);
    float b = skyHash(cell + float2(1.0, 0.0));
    float c = skyHash(cell + float2(0.0, 1.0));
    float d = skyHash(cell + float2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

fragment float4 sky_fragment(SkyVertexOut in [[stage_in]], constant SkyUniforms& sky [[buffer(0)]]) {
    float tanHalfFov = sky.forward.w;
    float3 direction = normalize(
        sky.forward.xyz +
        sky.right.xyz * (in.ndc.x * tanHalfFov * sky.right.w) +
        sky.up.xyz * (in.ndc.y * tanHalfFov)
    );

    float height = saturate(direction.y * 1.25);
    float3 color = mix(sky.horizonColor.rgb, sky.zenithColor.rgb, pow(height, 0.62));

    float3 sunDir = normalize(float3(0.38, 1.0, 0.22));
    float sun = pow(saturate(dot(direction, sunDir)), 72.0);
    float halo = pow(saturate(dot(direction, sunDir)), 8.0);
    color += float3(1.0, 0.93, 0.72) * sun * 0.95 + float3(1.0, 0.82, 0.45) * halo * 0.18;

    if (direction.y > 0.015) {
        float2 deck = direction.xz / direction.y * 0.6 + sky.up.w * float2(0.010, 0.004);
        float noise = skyNoise(deck) * 0.54 + skyNoise(deck * 2.13) * 0.29 + skyNoise(deck * 4.37) * 0.17;
        float cover = smoothstep(0.50, 0.76, noise) * smoothstep(0.015, 0.26, direction.y);
        float shading = 0.86 + 0.14 * smoothstep(0.48, 0.82, noise);
        color = mix(color, sky.cloudColor.rgb * shading, cover * 0.94);
    }

    return float4(color, 1.0);
}
"""
