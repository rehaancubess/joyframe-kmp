// SPDX-License-Identifier: Apache-2.0
package io.github.rehaancubess.joyframe.render.gl

import io.github.rehaancubess.joyframe.render.water.WaterConfig
import io.github.rehaancubess.joyframe.render.water.WaterQuality

internal enum class GlslDialect(val header: String, val fragmentHeader: String) {
    Core330(
        header = "#version 330 core\n",
        fragmentHeader = "#version 330 core\n",
    ),
    Es300(
        header = "#version 300 es\nprecision highp float;\n",
        fragmentHeader = "#version 300 es\nprecision highp float;\nprecision highp sampler2DShadow;\n",
    ),
}

internal object GlShaders {

    fun sceneVertex(dialect: GlslDialect) = dialect.header + WATER_DEFINES + SCENE_VERTEX
    fun sceneFragment(dialect: GlslDialect) = dialect.fragmentHeader + WATER_DEFINES + SCENE_FRAGMENT
    fun shadowVertex(dialect: GlslDialect) = dialect.header + SHADOW_VERTEX
    fun shadowFragment(dialect: GlslDialect) = dialect.fragmentHeader + SHADOW_FRAGMENT
    fun skyVertex(dialect: GlslDialect) = dialect.header + SKY_VERTEX
    fun skyFragment(dialect: GlslDialect) = dialect.fragmentHeader + SKY_FRAGMENT
    fun spriteVertex(dialect: GlslDialect) = dialect.header + SPRITE_VERTEX
    fun spriteFragment(dialect: GlslDialect) = dialect.fragmentHeader + SPRITE_FRAGMENT


    const val ATTRIB_POSITION = 0
    const val ATTRIB_NORMAL = 1
    const val ATTRIB_TEXCOORD = 2


    const val ATTRIB_COLOR = 1


    private val WATER_DEFINES: String = buildString {
        append("#define WATER_SWELL_TRAINS ").append(WaterConfig.SWELL_TRAINS).append('\n')
        append("#define WATER_QUALITY_MEDIUM ").append(glslFloat(WaterQuality.Medium.shaderLevel)).append('\n')
        append("#define WATER_QUALITY_HIGH ").append(glslFloat(WaterQuality.High.shaderLevel)).append('\n')
    }


    private fun glslFloat(value: Float): String = if (value % 1f == 0f) "${value.toInt()}.0" else value.toString()

    private const val SCENE_VERTEX = """
in vec3 aPosition;
in vec3 aNormal;
in vec2 aTexCoord;

uniform mat4 uViewProjection;
uniform mat4 uLightViewProjection;
uniform mat4 uModel;
uniform mat4 uNormalMatrix;

uniform float uTime;

uniform float uWaterShade;

uniform float uVortex;

uniform vec4 uVortexMask;

uniform vec4 uWaterWaves[WATER_SWELL_TRAINS];

uniform vec4 uWaterShallow;

out vec3 vWorldPosition;
out vec3 vWorldNormal;
out vec4 vLightClip;
out vec2 vTexCoord;

out float vWaterCrest;

out float vWaterFade;

vec3 waterWaves(vec2 position, float time) {
    vec3 wave = vec3(0.0);
    const float peak = 0.16;
    for (int index = 0; index < WATER_SWELL_TRAINS; ++index) {
        vec4 train = uWaterWaves[index];
        float phase = dot(position, train.xy) + time * train.z;
        float s = sin(phase);
        float c = cos(phase);
        wave.x += (s + peak * s * s * s) * train.w;
        wave.yz += c * (1.0 + 3.0 * peak * s * s) * train.w * train.xy;
    }
    return wave;
}

void main() {
    vec4 world = uModel * vec4(aPosition, 1.0);
    vec3 normal = (uNormalMatrix * vec4(aNormal, 0.0)).xyz;
    vTexCoord = aTexCoord;
    vWaterCrest = 0.0;
    vWaterFade = 1.0;

    if (uVortex > 0.5) {
        float energy = uVortexMask.x;
        vec3 wave = waterWaves(world.xz, uTime);
        world.y += wave.x * energy;
        normal = normalize(normal + vec3(-wave.y * energy, 0.0, -wave.z * energy));
        vWaterCrest = wave.x * uWaterShallow.a;
    } else if (uWaterShade > 0.0) {
        float energy = aTexCoord.x;
        vec3 wave = waterWaves(world.xz, uTime);
        world.y += wave.x * energy;
        vWaterFade = aNormal.x;
        normal = normalize(vec3(-wave.y * energy, 1.0, -wave.z * energy));
        vWaterCrest = wave.x * uWaterShallow.a;
    }

    vWorldPosition = world.xyz;
    vWorldNormal = normal;
    vLightClip = uLightViewProjection * world;
    gl_Position = uViewProjection * world;
}
"""

    private const val SCENE_FRAGMENT = """
in vec3 vWorldPosition;
in vec3 vWorldNormal;
in vec4 vLightClip;
in vec2 vTexCoord;
in float vWaterCrest;
in float vWaterFade;

uniform vec4 uCameraPosition;
uniform vec4 uLightDirection;
uniform vec4 uLightColor;
uniform vec4 uFillDirection;
uniform vec4 uFillColor;
uniform vec4 uAmbientColor;
uniform vec4 uFogColor;
uniform vec4 uFogParams;      // x = start, y = end, z = maximum density
uniform vec4 uShadowParams;   // x = texel size, y = depth bias, z = strength, w = exposure
uniform vec4 uBaseColor;
uniform vec4 uEmissive;       // rgb = glow, a = soap-bubble rim flag
uniform vec4 uMaterialParams; // x = roughness, y = metallic, z = receives shadow, w = has albedo

uniform float uTime;
uniform float uWaterShade;    // per draw: 0 = not water, else how much surface treatment
uniform vec4 uWaterShallow;   // rgb = sunlit shallow colour, a = 1 / total swell amplitude
uniform vec4 uWaterDeep;      // rgb = open water colour,     a = depth tint strength
uniform vec4 uWaterOptics;    // x = fresnel strength, y = fresnel hardness, z = glint, w = sharp
uniform vec4 uWaterDetail;    // x = ripple strength, y = ripple scale, z = ripple speed, w = tier
uniform vec4 uWaterLod;       // x = fade start, y = fade end, z = caustic scale, w = caustic speed
uniform vec4 uWaterFoam;      // x = crest start, y = crest end, z = foam, w = caustic intensity
uniform vec4 uWaterSurf;      // x = surf edge, y = surge, z = patch scale, w = shallow calm

uniform float uVortex;        // per draw: above 0.5 the UV channel is polar, not the shore masks
uniform vec2 uFoam;           // per draw: x = is aerated water, y = this instance's age 0..1
uniform vec4 uVortexMask;     // x = swell energy, y = depth mask, z = hull reaction, w = throat
uniform vec4 uVortexSpin;     // x = rotation, y = inflow, z = arms, w = spiral tightness
uniform vec4 uVortexChurn;    // x = distortion, y = turbulence, z = foam amount, w = foam speed
uniform vec4 uVortexLook;     // x = foam breakup, y = eye darkness, z = ripples, w = highlights
uniform vec4 uVortexHull;     // xy = hull position, z = its suction 0..1, w = 1 / reach
uniform vec4 uVortexDeath;    // xy = swallow position, z = envelope 0..1, w = climax strength

uniform sampler2DShadow uShadowMap;
uniform sampler2D uAlbedo;

out vec4 fragColor;

float saturate(float value) { return clamp(value, 0.0, 1.0); }
vec3 saturate3(vec3 value) { return clamp(value, 0.0, 1.0); }

const float WATER_CAUSTIC_REFRACTION = 140.0;

vec2 waterRippleSlope(vec2 position, float time, float fine) {
    float scale = uWaterDetail.y;
    float speed = uWaterDetail.z;
    const vec2 dirA1 = vec2(0.862, 0.507);
    const vec2 dirA2 = vec2(-0.391, 0.920);
    const vec2 dirB1 = vec2(0.643, -0.766);
    const vec2 dirB2 = vec2(0.970, 0.242);
    const vec2 dirB3 = vec2(-0.812, 0.584);
    vec2 slope = cos(dot(position, dirA1) * scale + time * speed) * dirA1 * 0.85;
    slope += cos(dot(position, dirA2) * scale * 1.43 - time * speed * 0.77) * dirA2 * 0.95;
    if (fine > 0.0) {
        slope += cos(dot(position, dirB1) * scale * 2.31 + time * speed * 1.62) * dirB1 * 0.70 * fine;
        slope += cos(dot(position, dirB2) * scale * 3.77 - time * speed * 2.19) * dirB2 * 0.55 * fine;
        slope += cos(dot(position, dirB3) * scale * 5.13 + time * speed * 2.71) * dirB3 * 0.38 * fine;
    }
    return slope * mix(1.35, 1.0, fine);
}

float waterChopPatches(vec2 position, float time) {
    float band = sin(dot(position, vec2(0.71, 0.70)) * uWaterDetail.y * uWaterSurf.z + time * 0.11);
    return 0.42 + 0.58 * (band * 0.5 + 0.5);
}

float waterSurfBreakup(vec2 position, float time) {
    float a = sin(dot(position, vec2(0.93, 0.37)) * 0.021 + time * 0.90);
    float b = sin(dot(position, vec2(-0.41, 0.91)) * 0.013 - time * 0.61);
    float c = sin(dot(position, vec2(0.22, -0.97)) * 0.034 + time * 1.35);
    return 0.48 + 0.52 * a * b * mix(0.55, 1.0, c * 0.5 + 0.5);
}

float waterCaustic(vec2 position, float time) {
    float scale = uWaterLod.z;
    float speed = uWaterLod.w;
    float a = sin(dot(position, vec2(0.724, 0.690)) * scale + time * speed);
    float b = sin(dot(position, vec2(-0.659, 0.752)) * scale * 1.73 - time * speed * 0.83);
    float c = sin(dot(position, vec2(0.180, -0.984)) * scale * 2.41 + time * speed * 1.19);
    float filaments = saturate(1.0 - abs(a * 0.55 + b * 0.45));
    float spark = saturate(1.0 - abs(b * 0.48 + c * 0.52));
    return pow(filaments, 4.0) * mix(0.28, 1.0, pow(spark, 3.0));
}

float waterSparkle(vec2 position, float time) {
    float a = sin(dot(position, vec2(0.173, 0.985)) * 0.37 + time * 4.6);
    float b = sin(dot(position, vec2(0.941, -0.338)) * 0.51 - time * 3.3);
    float c = sin(dot(position, vec2(-0.582, 0.813)) * 0.73 + time * 5.8);
    float cluster = saturate(a * b * 0.55 + c * 0.45);
    return 0.05 + 0.95 * pow(cluster, 8.0);
}

float foamBubbles(vec2 position, float time) {
    const float scale = 0.055;
    vec2 drift = position * scale + vec2(time * 0.09, time * -0.06);
    float a = sin(drift.x * 1.7 + drift.y * 1.1);
    float b = sin(drift.x * -0.9 + drift.y * 2.3 + 1.7);
    float c = sin((drift.x + drift.y) * 3.1 - time * 0.4);
    return saturate(0.5 + 0.34 * (a * b + c * 0.5));
}

const float VORTEX_EYE = 0.085;
const float TWO_PI = 6.2831853;

const float VORTEX_SPIRAL_GAIN = 3.1;

const float VORTEX_SPIN_FLOOR = 0.26;

const float VORTEX_THROAT_TONE = 0.72;

const float VORTEX_NORMAL_TILT = 0.24;

const float VORTEX_BAND_SHADING = 0.36;

float vortexBand(float angle, float winding, float spinRate, float time, float arms, float tightness, float speed) {
    return sin(angle * arms + winding * tightness + time * speed * spinRate);
}

float sampleShadow(vec4 lightClip, float texel, float bias) {
    vec3 projected = lightClip.xyz / max(lightClip.w, 1e-4);
    vec3 uvz = projected * 0.5 + 0.5;
    if (uvz.x < 0.0 || uvz.x > 1.0 || uvz.y < 0.0 || uvz.y > 1.0 || uvz.z > 1.0) return 1.0;

    float reference = uvz.z - bias;
    vec2 tap = vec2(texel * 0.75);
    float sum = texture(uShadowMap, vec3(uvz.xy + vec2(-tap.x, -tap.y), reference));
    sum += texture(uShadowMap, vec3(uvz.xy + vec2(tap.x, -tap.y), reference));
    sum += texture(uShadowMap, vec3(uvz.xy + vec2(-tap.x, tap.y), reference));
    sum += texture(uShadowMap, vec3(uvz.xy + vec2(tap.x, tap.y), reference));
    return sum * 0.25;
}

void main() {
    vec4 base = uBaseColor;
    if (uMaterialParams.w > 0.5) base *= texture(uAlbedo, vTexCoord);
    if (uFoam.x > 0.0) {
        vec2 local = vTexCoord * 2.0 - 1.0;
        float age = uFoam.y;
        float footprint = saturate(1.0 - dot(local, local));
        float streak = 0.62 + 0.38 * sin(local.y * 7.0 + local.x * 2.3);
        float bubbles = foamBubbles(vWorldPosition.xz, uTime);
        float threshold = mix(0.16, 0.92, age * age);
        float mask = smoothstep(threshold, threshold + 0.26, footprint * streak * (0.45 + bubbles));
        base.a *= mask;
    }

    vec3 albedo = base.rgb;
    vec3 normal = normalize(vWorldNormal);
    vec3 toLight = normalize(-uLightDirection.xyz);
    vec3 toFill = normalize(-uFillDirection.xyz);
    vec3 toView = normalize(uCameraPosition.xyz - vWorldPosition);
    float viewDistance = length(uCameraPosition.xyz - vWorldPosition);
    float waterEnergy = 0.0;
    float waterDepth = 0.0;
    float waterDetail = 0.0;
    float vortexRadius = 0.0;
    float vortexRim = 0.0;
    float vortexFlow = 0.0;
    float vortexSurge = 0.0;
    float vortexClimax = 0.0;
    float vortexDetail = 0.0;
    if (uWaterShade > 0.0) {
        waterEnergy = uVortex > 0.5 ? uVortexMask.x : vTexCoord.x;
        waterDepth = uVortex > 0.5 ? uVortexMask.y : vTexCoord.y;
        waterDetail = 1.0 - smoothstep(uWaterLod.x, uWaterLod.y, viewDistance);
        if (uWaterDetail.w >= WATER_QUALITY_MEDIUM && waterDetail > 0.0) {
            vec2 slope = waterRippleSlope(vWorldPosition.xz, uTime, step(WATER_QUALITY_HIGH, uWaterDetail.w));
            float chop = waterChopPatches(vWorldPosition.xz, uTime);
            chop *= 0.72 + 0.28 * (vWaterCrest * 0.5 + 0.5);
            chop *= mix(uWaterSurf.w, 1.0, waterDepth);
            normal = normalize(normal + vec3(-slope.x, 0.0, -slope.y) *
                (uWaterDetail.x * waterDetail * chop));
        }

        if (uVortex > 0.5) {
            vortexRadius = saturate(vTexCoord.x);
            float angle = vTexCoord.y * TWO_PI;
            float winding = log(max(vortexRadius, VORTEX_EYE)) * VORTEX_SPIRAL_GAIN;
            float spinRate = 1.0 / max(vortexRadius, VORTEX_SPIN_FLOOR);
            vortexRim = smoothstep(1.0, 0.62, vortexRadius);
            vec2 toHull = (vWorldPosition.xz - uVortexHull.xy) * uVortexHull.w;
            vec2 toGrave = (vWorldPosition.xz - uVortexDeath.xy) * uVortexHull.w;
            vortexClimax = uVortexDeath.z * uVortexDeath.w * saturate(1.0 - dot(toGrave, toGrave) * 0.4);
            vortexSurge = saturate(
                uVortexHull.z * saturate(1.0 - dot(toHull, toHull)) * uVortexMask.z + vortexClimax);
            vortexDetail = waterDetail * step(WATER_QUALITY_MEDIUM, uWaterDetail.w);
            vortexFlow = vortexBand(angle, winding, spinRate, uTime, uVortexSpin.z, uVortexSpin.w, uVortexSpin.x) * 0.50;
            vortexFlow += vortexBand(angle, winding, spinRate, uTime, uVortexSpin.z * 2.5, -uVortexSpin.w * 0.62, uVortexSpin.x * 1.55) * 0.32;
            if (vortexDetail > 0.0) {
                vortexFlow += vortexBand(angle, winding, spinRate, uTime, uVortexSpin.z * 4.5, uVortexSpin.w * 1.6, uVortexSpin.x * 0.71) *
                    0.18 * uVortexChurn.y * vortexDetail;
                float ripple = sin(vortexRadius * 26.0 - uTime * uVortexSpin.y * 9.0 + vortexFlow * uVortexChurn.x * 4.0);
                vortexFlow += ripple * uVortexLook.z * vortexRim * vortexDetail;
            }
            vec2 tangent = vec2(-sin(angle), cos(angle));
            normal = normalize(normal + vec3(tangent.x, 0.0, tangent.y) *
                (vortexFlow * VORTEX_NORMAL_TILT * vortexRim * (1.0 + vortexSurge)));
        }
        vec3 depthColor = mix(uWaterShallow.rgb, uWaterDeep.rgb, waterDepth);
        depthColor *= 1.0 + vWaterCrest * waterEnergy * 1.18;
        depthColor *= 1.0 - saturate(-vWaterCrest) * waterEnergy * 0.55;
        depthColor = mix(depthColor, depthColor * vec3(0.86, 1.02, 1.08), waterDepth * 0.35);
        albedo = mix(albedo, depthColor, uWaterDeep.a * uWaterShade * uWaterShade);
    }
    const float wrap = 0.26;
    float key = saturate((dot(normal, toLight) + wrap) / (1.0 + wrap));
    float fill = saturate((dot(normal, toFill) + wrap) / (1.0 + wrap));

    float shadow = 1.0;
    if (uMaterialParams.z > 0.5 && uShadowParams.z > 0.001) {
        float lit = sampleShadow(vLightClip, uShadowParams.x, uShadowParams.y);
        shadow = mix(1.0, lit, uShadowParams.z);
    }

    float roughness = clamp(uMaterialParams.x, 0.05, 1.0);
    float metallic = saturate(uMaterialParams.y);
    vec3 halfway = normalize(toLight + toView);
    float shininess = mix(mix(110.0, 6.0, roughness), uWaterOptics.w, uWaterShade);
    float highlight = pow(saturate(dot(normal, halfway)), shininess);
    float specular = highlight * (1.0 - roughness) * (1.0 - uWaterShade);
    vec3 specularTint = mix(vec3(1.0), albedo, metallic);

    vec3 color = albedo * uAmbientColor.rgb;
    color += albedo * uLightColor.rgb * key * shadow;
    color += albedo * uFillColor.rgb * fill;
    color += specularTint * uLightColor.rgb * specular * shadow * mix(0.35, 0.9, metallic);
    color = 1.0 - exp(-color * uShadowParams.w);
    const float saturation = 1.24;
    const float contrast = 0.20;
    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    color = saturate3(luma + (color - luma) * saturation);
    color = mix(color, smoothstep(0.0, 1.0, color), contrast);

    color += uEmissive.rgb;
    if (uWaterShade > 0.0) {
        float grazing = 1.0 - saturate(dot(normal, toView));
        float grazing2 = grazing * grazing;
        float fresnel = uWaterOptics.x * mix(grazing2, grazing2 * grazing2, uWaterOptics.y) * uWaterShade;
        vec3 refl = reflect(-toView, normal);
        float skyH = saturate(refl.y * 1.4);
        vec3 skyHorizon = saturate3(uFogColor.rgb * 0.28 + vec3(0.70, 0.88, 0.98) * 0.72);
        vec3 skyZenith = saturate3(uFogColor.rgb * 0.12 + vec3(0.18, 0.48, 0.92) * 0.88);
        vec3 skyReflection = mix(skyHorizon, skyZenith, skyH);
        float sunSpec = pow(saturate(dot(refl, toLight)), 12.0);
        skyReflection = mix(skyReflection, saturate3(uLightColor.rgb * 0.55 + vec3(1.0, 0.94, 0.78) * 0.50), sunSpec);
        color = mix(color, skyReflection, saturate(fresnel));
        float trans = saturate(vWaterCrest * 0.62 + 0.16) * saturate(1.0 - waterDepth * 0.88);
        float scatter = saturate(dot(toView, -toLight) * 0.50 + 0.50);
        color += uWaterShallow.rgb * trans * scatter * 0.26 * uWaterShade * waterEnergy;
        float sparkle = waterSparkle(vWorldPosition.xz, uTime);
        float glintReach = mix(0.08, 0.78, waterDetail);
        float tight = pow(saturate(dot(normal, halfway)), shininess * 2.6);
        color += uLightColor.rgb * (highlight * 0.50 + tight) * uWaterOptics.z * uWaterShade * sparkle * glintReach;
        float surfDepth = waterDepth - vWaterCrest * uWaterSurf.y;
        float surf = smoothstep(uWaterSurf.x, uWaterSurf.x * 0.15, surfDepth);
        if (surf > 0.0) {
            surf *= waterSurfBreakup(vWorldPosition.xz, uTime);
            color = mix(color, vec3(0.92, 0.97, 1.0), saturate(surf * uWaterFoam.z * uWaterShade));
        }
        if (uVortex > 0.5) {
            color *= 1.0 + vortexFlow * VORTEX_BAND_SHADING * vortexRim * mix(0.55, 1.0, key);
            float eye = smoothstep(uVortexMask.w, 0.0, vortexRadius);
            float darkness = saturate(uVortexLook.y * (1.0 + vortexClimax * 0.45));
            color = mix(color, uWaterDeep.rgb * VORTEX_THROAT_TONE, eye * darkness * uWaterShade);
            float sheen = pow(saturate(vortexFlow * 0.5 + 0.5), 6.0) * smoothstep(0.0, 0.26, vortexRadius);
            color += uLightColor.rgb * sheen * uVortexLook.w * vortexRim * key * (0.6 + vortexSurge);
            float foamBand = saturate(vortexFlow * 0.5 + 0.5);
            if (vortexDetail > 0.0) {
                float tear = sin(vTexCoord.y * TWO_PI * 9.0 +
                    log(max(vortexRadius, VORTEX_EYE)) * VORTEX_SPIRAL_GAIN * 2.1 -
                    uTime * uVortexChurn.w * 1.7);
                foamBand *= mix(1.0, 0.35 + 0.65 * saturate(tear * 0.5 + 0.5), uVortexLook.x * vortexDetail);
            }
            float band = smoothstep(0.10, 0.30, vortexRadius) * smoothstep(1.0, 0.62, vortexRadius);
            float foam = smoothstep(0.60, 0.90, foamBand) * band * uVortexChurn.z;
            float closing = 1.0 - uVortexDeath.z;
            foam += smoothstep(0.10, 0.0, abs(vortexRadius - closing)) * uVortexDeath.z * uVortexDeath.w * 0.9;

            color = mix(color, vec3(1.0), saturate(foam * (1.0 + vortexSurge) * vortexRim * uWaterShade));
        }

        if (uWaterDetail.w >= WATER_QUALITY_HIGH && waterDetail > 0.12) {
            float shallow = pow(saturate(1.0 - waterDepth), 1.65) * waterDetail;
            if (shallow > 0.08) {
                vec2 causticPoint = vWorldPosition.xz + normal.xz * WATER_CAUSTIC_REFRACTION;
                color += uWaterShallow.rgb * waterCaustic(causticPoint, uTime) *
                    uWaterFoam.w * shallow * uWaterShade * mix(0.70, 1.15, key);
                color += uWaterShallow.rgb * waterCaustic(causticPoint * 1.67, uTime * 0.71) *
                    uWaterFoam.w * shallow * 0.45 * uWaterShade;
            }

            float crest = smoothstep(uWaterFoam.x, uWaterFoam.y, vWaterCrest) * waterEnergy;
            if (crest > 0.0) {
                float torn = 0.42 + 0.58 * waterCaustic(vWorldPosition.xz * 2.3, uTime * 1.7);
                color = mix(color, vec3(0.93, 0.98, 1.0), saturate(crest * torn * uWaterFoam.z * uWaterShade * 0.85));
            }
        }
    }

    float fogRange = max(uFogParams.y - uFogParams.x, 1.0);
    float fog = saturate((viewDistance - uFogParams.x) / fogRange) * uFogParams.z;
    color = mix(color, uFogColor.rgb, fog);
    float alpha = base.a * vWaterFade;
    if (uEmissive.a > 0.5) {
        float grazing = 1.0 - saturate(abs(dot(normal, toView)));
        float rim = grazing * grazing;
        color = mix(color, vec3(0.90, 0.99, 1.0), saturate(rim * 1.25));
        alpha = mix(0.05, 0.78, rim) * vWaterFade * base.a;
    }
    fragColor = vec4(color, alpha);
}
"""

    private const val SHADOW_VERTEX = """
in vec3 aPosition;

uniform mat4 uLightViewProjection;
uniform mat4 uModel;

void main() {
    gl_Position = uLightViewProjection * (uModel * vec4(aPosition, 1.0));
}
"""


    private const val SHADOW_FRAGMENT = """
void main() { }
"""

    private const val SKY_VERTEX = """
out vec2 vNdc;

void main() {
    vec2 corner = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
    vNdc = corner * 2.0 - 1.0;
    gl_Position = vec4(vNdc, 1.0, 1.0);
}
"""

    private const val SKY_FRAGMENT = """
in vec2 vNdc;

uniform vec4 uSkyForward;   // xyz = camera forward, w = tan(fovY / 2)
uniform vec4 uSkyRight;     // xyz = camera right,   w = aspect ratio
uniform vec4 uSkyUp;        // xyz = camera up,      w = time in seconds
uniform vec4 uZenithColor;
uniform vec4 uHorizonColor;
uniform vec4 uCloudColor;

out vec4 fragColor;

float saturate(float value) { return clamp(value, 0.0, 1.0); }

float skyHash(vec2 p) {
    p = fract(p * vec2(127.31, 311.7));
    p += dot(p, p + 34.53);
    return fract(p.x * p.y);
}

float skyNoise(vec2 p) {
    vec2 cell = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = skyHash(cell);
    float b = skyHash(cell + vec2(1.0, 0.0));
    float c = skyHash(cell + vec2(0.0, 1.0));
    float d = skyHash(cell + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

void main() {
    float tanHalfFov = uSkyForward.w;
    vec3 direction = normalize(
        uSkyForward.xyz +
        uSkyRight.xyz * (vNdc.x * tanHalfFov * uSkyRight.w) +
        uSkyUp.xyz * (vNdc.y * tanHalfFov)
    );

    float height = saturate(direction.y * 1.25);
    vec3 color = mix(uHorizonColor.rgb, uZenithColor.rgb, pow(height, 0.62));
    vec3 sunDir = normalize(vec3(0.38, 1.0, 0.22));
    float sun = pow(saturate(dot(direction, sunDir)), 72.0);
    float halo = pow(saturate(dot(direction, sunDir)), 8.0);
    color += vec3(1.0, 0.93, 0.72) * sun * 0.95 + vec3(1.0, 0.82, 0.45) * halo * 0.18;

    if (direction.y > 0.015) {
        vec2 deck = direction.xz / direction.y * 0.6 + uSkyUp.w * vec2(0.010, 0.004);
        float noise = skyNoise(deck) * 0.54 + skyNoise(deck * 2.13) * 0.29 + skyNoise(deck * 4.37) * 0.17;
        float cover = smoothstep(0.50, 0.76, noise) * smoothstep(0.015, 0.26, direction.y);
        float shading = 0.86 + 0.14 * smoothstep(0.48, 0.82, noise);
        color = mix(color, uCloudColor.rgb * shading, cover * 0.94);
    }

    fragColor = vec4(color, 1.0);
}
"""


    private const val SPRITE_VERTEX = """
in vec2 aPosition;   // pixels from the top left of the viewport
in vec4 aColor;
in vec2 aTexCoord;

uniform vec4 uViewport;  // x = width, y = height in pixels

out vec2 vTexCoord;
out vec4 vColor;

void main() {
    vTexCoord = aTexCoord;
    vColor = aColor;
    vec2 ndc = vec2(
        aPosition.x / uViewport.x * 2.0 - 1.0,
        1.0 - aPosition.y / uViewport.y * 2.0
    );
    gl_Position = vec4(ndc, 0.0, 1.0);
}
"""


    private const val SPRITE_FRAGMENT = """
in vec2 vTexCoord;
in vec4 vColor;

uniform sampler2D uSprite;

out vec4 fragColor;

void main() {
    fragColor = texture(uSprite, vTexCoord) * vColor;
}
"""
}
