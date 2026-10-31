#version 300 es
precision highp float;
#include "common.glsl"
#include "lighting.glsl"

#if defined(SHADOW) || defined(DEPTH)
void main() {
}
#else
in vec3 vWorld;
in vec3 vNrm;
in vec2 vUv;
in float vMat;
layout(location = 0) out vec4 oColor;

void main() {
    vec3 wp = vWorld;
    vec3 N0 = normalize(vNrm);
    vec3 V = normalize(uCamPos.xyz - wp);
    int mat = int(vMat + 0.5);
    CP vec3 albedo;
    float rough;
    vec3 N = N0;
    float glass = 0.0;

    if (mat == 3) {
        // kaca jendela
        albedo = vec3(0.02, 0.03, 0.04);
        rough = 0.06;
        glass = 1.0;
    } else {
        // 0 = plester, 1 = papan kayu, 2 = genteng
        float layer = mat == 0 ? 7.0 : (mat == 1 ? 5.0 : 6.0);
        vec3 c = vec3(vUv, layer);
        albedo = texture(uAlbedo, c).rgb;
        vec4 t = texture(uNormRough, c);
        N = applyNormalVec(N0, wp, vUv, t.xyz * 2.0 - 1.0);
        rough = t.w;
    }

    float NoL0 = max(dot(N0, uLight.xyz), 0.0);
    float shadow = shadowFactor(wp, N0, NoL0);
    vec3 col = lightPBR(albedo, N, V, rough, shadow, 1.0);
    if (glass > 0.5) {
        float fr = 0.1 + 0.9 * pow(1.0 - max(dot(N0, V), 0.0), 4.0);
        col += mix(uAmbGround.rgb, uAmbSky.rgb, 0.8) * fr;
    }
    col = applyFog(col, wp);
    oColor = vec4(toLdr(col), 1.0);
}
#endif
