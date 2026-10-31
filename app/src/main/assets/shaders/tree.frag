#version 300 es
precision highp float;
#include "common.glsl"
#include "lighting.glsl"

in vec2 vUv;
in vec4 vInfo;      // bagian, seed, jenis, shade

#if defined(SHADOW) || defined(DEPTH)
void main() {
    // alpha-test daun: bayangan berbintik tembus dedaunan
    if (vInfo.x > 0.5 && leafMask(vUv, vInfo.z, vInfo.y) < 0.5) discard;
}
#else
in vec3 vWorld;
in vec3 vNrm;
layout(location = 0) out vec4 oColor;

void main() {
    bool leaf = vInfo.x > 0.5;
    if (leaf && leafMask(vUv, vInfo.z, vInfo.y) < 0.5) discard;

    vec3 wp = vWorld;
    vec3 N = normalize(vNrm);
    vec3 V = normalize(uCamPos.xyz - wp);
    vec3 L = uLight.xyz;
    CP vec3 base;
    float rough;
    float ao;

    if (leaf) {
        float species = vInfo.z;
        float seed = vInfo.y;
        float v = hash12(floor(vUv * 7.0) + seed * 17.0);
        vec3 pine = vec3(0.025, 0.085, 0.035);
        vec3 broad = vec3(0.060, 0.150, 0.025);
        vec3 autumn = vec3(0.20, 0.12, 0.02);
        base = species < 0.5 ? pine : broad;
        base *= 0.75 + 0.5 * v;
        if (species > 0.5 && fract(seed * 7.31) > 0.88) base = mix(base, autumn, 0.7);
        rough = 0.55;
        ao = mix(0.35, 1.0, smoothstep(0.1, 0.9, vInfo.w));
    } else {
        vec3 c = vec3(vUv, 4.0);
        base = texture(uAlbedo, c).rgb;
        vec4 t = texture(uNormRough, c);
        N = applyNormalVec(N, wp, vUv, t.xyz * 2.0 - 1.0);
        rough = t.w;
        ao = mix(0.55, 1.0, vInfo.w);
    }

    float NoL0 = max(dot(normalize(vNrm), L), 0.0);
    float shadow = shadowFactor(wp, normalize(vNrm), NoL0);
    vec3 col = lightPBR(base, N, V, rough, shadow, ao);
    if (leaf) {
        // cahaya tembus daun saat menghadap matahari
        float trans = pow(max(dot(V, -L), 0.0), 2.0) * 0.6;
        col += base * uLightColor.rgb * trans * shadow;
    }
    col = applyFog(col, wp);
    oColor = vec4(toLdr(col), 1.0);
}
#endif
