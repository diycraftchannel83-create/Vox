package com.example.render

import android.opengl.GLES30

class ShaderProgram(vertexSource: String, fragmentSource: String) {
    val programId: Int

    init {
        val vs = compileShader(GLES30.GL_VERTEX_SHADER, vertexSource)
        val fs = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource)
        programId = GLES30.glCreateProgram().also { prog ->
            GLES30.glAttachShader(prog, vs)
            GLES30.glAttachShader(prog, fs)
            GLES30.glLinkProgram(prog)
            val status = IntArray(1)
            GLES30.glGetProgramiv(prog, GLES30.GL_LINK_STATUS, status, 0)
            if (status[0] == 0) {
                val log = GLES30.glGetProgramInfoLog(prog)
                GLES30.glDeleteProgram(prog)
                throw IllegalStateException("GL program link failed: $log")
            }
        }
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)
    }

    fun use() = GLES30.glUseProgram(programId)

    fun uniformLocation(name: String): Int = GLES30.glGetUniformLocation(programId, name)

    fun release() {
        GLES30.glDeleteProgram(programId)
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            throw IllegalStateException("GL shader compile failed: $log")
        }
        return shader
    }

    companion object {
        const val AVATAR_VERTEX_SHADER = """#version 300 es
layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec2 aUv;

uniform mat4 uModel;
uniform mat4 uView;
uniform mat4 uProjection;
uniform mat3 uNormalMatrix;

uniform int uPartRole;
uniform int uAutoRigMode;
uniform float uJawOpen;
uniform float uMouthWide;
uniform float uMouthPucker;
uniform float uLipPress;
uniform float uUpperLipRaise;
uniform float uEyeBlink;
uniform float uBrowRaise;
uniform float uVocalEnergy;

out vec3 vWorldPos;
out vec3 vWorldNormal;
out vec2 vUv;

mat3 rotX(float a) {
    float c = cos(a);
    float s = sin(a);
    return mat3(
        1.0, 0.0, 0.0,
        0.0, c,   s,
        0.0, -s,  c
    );
}

void main() {
    vec3 pos = aPosition;
    vec3 nrm = aNormal;

    // PartRole mapping:
    // 1=JAW, 2=UPPER_LIP, 3=LOWER_LIP, 4=MOUTH_CAVITY,
    // 7=LEFT_EYELID, 8=RIGHT_EYELID, 9=LEFT_BROW, 10=RIGHT_BROW,
    // 13=HALO_RING, 14=CUSTOM_DEFORMABLE
    if (uPartRole == 1) {
        vec3 pivot = vec3(0.0, -0.09, -0.04);
        float angle = uJawOpen * 0.42;
        mat3 r = rotX(angle);
        pos = pivot + r * (pos - pivot);
        pos.y -= uJawOpen * 0.025;
        nrm = r * nrm;
    } else if (uPartRole == 2) {
        float xNorm = clamp(pos.x / 0.10, -1.0, 1.0);
        float centerW = max(0.0, 1.0 - xNorm * xNorm);
        pos.y += uUpperLipRaise * 0.022 * centerW + uJawOpen * 0.010 * centerW - uLipPress * 0.014 * centerW;
        pos.x *= (1.0 + uMouthWide * 0.20 - uMouthPucker * 0.28);
        pos.z += uMouthPucker * 0.032 * centerW;
    } else if (uPartRole == 3) {
        vec3 pivot = vec3(0.0, -0.09, -0.04);
        float angle = uJawOpen * 0.44;
        mat3 r = rotX(angle);
        pos = pivot + r * (pos - pivot);
        float xNorm = clamp(pos.x / 0.10, -1.0, 1.0);
        float centerW = max(0.0, 1.0 - xNorm * xNorm);
        pos.y += uLipPress * 0.016 * centerW - uJawOpen * 0.015 * centerW;
        pos.x *= (1.0 + uMouthWide * 0.22 - uMouthPucker * 0.30);
        pos.z += uMouthPucker * 0.034 * centerW;
        nrm = r * nrm;
    } else if (uPartRole == 7 || uPartRole == 8) {
        float side = (uPartRole == 7) ? -0.128 : 0.128;
        vec3 eyeCenter = vec3(side, 0.105, 0.285);
        float blinkAngle = uEyeBlink * 1.15;
        mat3 r = rotX(blinkAngle);
        pos = eyeCenter + r * (pos - eyeCenter);
        nrm = r * nrm;
    } else if (uPartRole == 9 || uPartRole == 10) {
        pos.y += uBrowRaise * 0.032;
        float side = (uPartRole == 9) ? -1.0 : 1.0;
        pos.y += side * (pos.x * 0.06 * uBrowRaise);
    } else if (uPartRole == 13) {
        float scaleBoost = 1.0 + uVocalEnergy * 0.14;
        pos.xz *= scaleBoost;
        pos.y += uVocalEnergy * 0.035;
    } else if (uPartRole == 14 && uAutoRigMode == 1) {
        // Automatic procedural jaw & lip articulation for imported static OBJ/GLB models
        if (pos.z > 0.02 && pos.y < -0.02 && pos.y > -0.52) {
            float yWeight = smoothstep(-0.02, -0.16, pos.y) * (1.0 - smoothstep(-0.36, -0.52, pos.y));
            float zWeight = smoothstep(0.02, 0.18, pos.z);
            float xWeight = 1.0 - smoothstep(0.12, 0.34, abs(pos.x));
            float w = yWeight * zWeight * xWeight;
            pos.y -= uJawOpen * 0.14 * w;
            pos.z -= uJawOpen * 0.035 * w;
            pos.x *= (1.0 + (uMouthWide * 0.16 - uMouthPucker * 0.20) * w);
            pos.z += uMouthPucker * 0.04 * w;
        }
    }

    vec4 worldPos = uModel * vec4(pos, 1.0);
    vWorldPos = worldPos.xyz;
    vWorldNormal = normalize(uNormalMatrix * nrm);
    vUv = aUv;
    gl_Position = uProjection * uView * worldPos;
}
"""

        const val AVATAR_FRAGMENT_SHADER = """#version 300 es
precision highp float;

in vec3 vWorldPos;
in vec3 vWorldNormal;
in vec2 vUv;

uniform vec3 uCameraPos;
uniform vec4 uBaseColor;
uniform float uMetallic;
uniform float uRoughness;
uniform vec3 uEmissive;
uniform int uIsSkin;
uniform int uHasTexture;
uniform sampler2D uBaseColorTex;

// 3-point studio lighting uniforms
uniform vec3 uKeyColor;
uniform vec3 uFillColor;
uniform vec3 uRimColor;
uniform vec3 uAmbientSky;
uniform vec3 uAmbientGround;

out vec4 fragColor;

const float PI = 3.14159265359;

float distributionGGX(vec3 N, vec3 H, float roughness) {
    float a = roughness * roughness;
    float a2 = a * a;
    float NdotH = max(dot(N, H), 0.0);
    float denom = (NdotH * NdotH * (a2 - 1.0) + 1.0);
    return a2 / max(PI * denom * denom, 0.0001);
}

float geometrySmith(float NdotV, float NdotL, float roughness) {
    float r = roughness + 1.0;
    float k = (r * r) / 8.0;
    float ggxV = NdotV / (NdotV * (1.0 - k) + k);
    float ggxL = NdotL / (NdotL * (1.0 - k) + k);
    return ggxV * ggxL;
}

vec3 fresnelSchlick(float cosTheta, vec3 F0) {
    return F0 + (1.0 - F0) * pow(clamp(1.0 - cosTheta, 0.0, 1.0), 5.0);
}

vec3 evalLight(vec3 L, vec3 lightColor, vec3 N, vec3 V, vec3 albedo, vec3 F0, float metallic, float roughness) {
    vec3 H = normalize(V + L);
    float NdotL = max(dot(N, L), 0.0);
    float NdotV = max(dot(N, V), 0.001);

    float D = distributionGGX(N, H, roughness);
    float G = geometrySmith(NdotV, NdotL, roughness);
    vec3 F = fresnelSchlick(max(dot(H, V), 0.0), F0);

    vec3 spec = (D * G * F) / max(4.0 * NdotV * NdotL, 0.001);
    vec3 kD = (vec3(1.0) - F) * (1.0 - metallic);
    vec3 diffuse = kD * albedo / PI;

    // Subtle wrap subsurface warmth on organic skin
    vec3 sss = vec3(0.0);
    if (uIsSkin == 1) {
        float wrap = clamp((dot(N, L) + 0.45) / 1.45, 0.0, 1.0);
        sss = albedo * vec3(1.0, 0.55, 0.45) * wrap * 0.14 * lightColor;
    }

    return (diffuse * NdotL + spec * NdotL) * lightColor + sss;
}

void main() {
    vec4 texSample = (uHasTexture == 1) ? texture(uBaseColorTex, vUv) : vec4(1.0);
    vec3 albedo = uBaseColor.rgb * texSample.rgb;
    float roughness = clamp(uRoughness, 0.08, 0.96);
    float metallic = clamp(uMetallic, 0.0, 1.0);

    vec3 N = normalize(vWorldNormal);
    vec3 V = normalize(uCameraPos - vWorldPos);
    vec3 F0 = mix(vec3(0.04), albedo, metallic);

    vec3 keyDir = normalize(vec3(0.55, 0.68, 0.72));
    vec3 fillDir = normalize(vec3(-0.75, 0.25, 0.55));
    vec3 rimDir = normalize(vec3(-0.35, 0.52, -0.82));

    vec3 color = vec3(0.0);
    color += evalLight(keyDir, uKeyColor, N, V, albedo, F0, metallic, roughness);
    color += evalLight(fillDir, uFillColor, N, V, albedo, F0, metallic, roughness);
    color += evalLight(rimDir, uRimColor, N, V, albedo, F0, metallic, roughness);

    // Hemisphere IBL ambient
    float hemi = 0.5 + 0.5 * N.y;
    vec3 ambient = mix(uAmbientGround, uAmbientSky, hemi) * albedo * (1.0 - metallic * 0.45);
    color += ambient;

    // Crisp Fresnel rim highlight
    float rimFresnel = pow(1.0 - max(dot(N, V), 0.0), 3.4);
    color += uRimColor * rimFresnel * 0.24;

    // Emissive contribution
    color += uEmissive;

    // ACES filmic tone mapping
    vec3 a = color * (2.51 * color + 0.03);
    vec3 b = color * (2.43 * color + 0.59) + 0.14;
    color = clamp(a / b, 0.0, 1.0);
    color = pow(color, vec3(1.0 / 2.2));

    fragColor = vec4(color, uBaseColor.a * texSample.a);
}
"""

        const val BACKDROP_VERTEX_SHADER = """#version 300 es
layout(location = 0) in vec2 aPos;
out vec2 vScreenUv;
void main() {
    vScreenUv = aPos * 0.5 + 0.5;
    gl_Position = vec4(aPos, 0.999, 1.0);
}
"""

        const val BACKDROP_FRAGMENT_SHADER = """#version 300 es
precision mediump float;
in vec2 vScreenUv;
uniform int uBackdropMode;
uniform float uVocalEnergy;
uniform vec2 uResolution;
out vec4 fragColor;

void main() {
    if (uBackdropMode == 3) {
        // Chroma Key Green
        fragColor = vec4(0.0, 0.694, 0.251, 1.0);
        return;
    }

    vec2 aspectUv = (vScreenUv - 0.5);
    aspectUv.x *= uResolution.x / max(uResolution.y, 1.0);
    float dist = length(aspectUv);

    vec3 bg = vec3(0.035, 0.045, 0.068);
    if (uBackdropMode == 0) {
        // Obsidian Studio spotlight
        float spot = exp(-dist * dist * 3.2);
        bg = mix(vec3(0.025, 0.032, 0.048), vec3(0.08, 0.13, 0.19), spot);
        bg += vec3(0.03, 0.16, 0.22) * exp(-dist * dist * 6.0) * (0.35 + uVocalEnergy * 0.45);
    } else if (uBackdropMode == 1) {
        // Gradient Stage
        vec3 top = vec3(0.05, 0.07, 0.14);
        vec3 bottom = vec3(0.09, 0.05, 0.12);
        bg = mix(bottom, top, vScreenUv.y);
        float glow = exp(-dist * dist * 2.8);
        bg += mix(vec3(0.06, 0.30, 0.38), vec3(0.32, 0.14, 0.08), vScreenUv.x) * glow * 0.55;
    } else {
        // Tech Holo Grid
        bg = mix(vec3(0.02, 0.035, 0.06), vec3(0.05, 0.09, 0.15), vScreenUv.y);
        vec2 grid = abs(fract(vScreenUv * vec2(16.0, 12.0) - 0.5) - 0.5) / fwidth(vScreenUv * vec2(16.0, 12.0));
        float line = 1.0 - min(min(grid.x, grid.y), 1.0);
        float fade = smoothstep(0.55, 0.05, vScreenUv.y) * 0.32;
        bg += vec3(0.13, 0.82, 0.93) * line * (fade + 0.04 + uVocalEnergy * 0.08);
    }

    fragColor = vec4(bg, 1.0);
}
"""
    }
}
