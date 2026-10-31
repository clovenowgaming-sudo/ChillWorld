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
layout(location = 0) out vec4 oColor;

void main() {
    vec3 wp = vWorld;
    vec3 N0 = normalize(vNrm);
    vec3 V = normalize(uCamPos.xyz - wp);
    float dist = distance(uCamPos.xyz, wp);
    float h = wp.y;
    float slope = 1.0 - N0.y;
    float nz = fbm2(wp.xz * 0.045);
    float nz2 = vnoise(wp.xz * 0.008);

    // bobot material: salju di puncak, batu di tebing, tanah di lereng/ketinggian
    float wSnow = smoothstep(200.0, 250.0, h + (nz - 0.4) * 60.0) * (1.0 - smoothstep(0.28, 0.5, slope));
    float wRock = max(smoothstep(0.20, 0.42, slope + (nz - 0.4) * 0.14),
                      smoothstep(140.0, 220.0, h + (nz - 0.4) * 70.0) * 0.85);
    float wDirt = clamp(smoothstep(0.06, 0.2, slope) * 0.8 + smoothstep(0.62, 0.8, nz2) * 0.7 +
                        smoothstep(80.0, 150.0, h) * 0.6, 0.0, 1.0);
    float wGrass = (1.0 - wDirt) * (1.0 - wRock) * (1.0 - wSnow);
    float wD = wDirt * (1.0 - wRock) * (1.0 - wSnow);
    float wR = wRock * (1.0 - wSnow);
    float wS = wSnow;
    float sum = wGrass + wD + wR + wS + 0.0001;
    wGrass /= sum;
    wD /= sum;
    wR /= sum;
    wS /= sum;

    vec2 uv = wp.xz * 0.22;
    CP vec3 albedo = vec3(0.0);
    vec3 nAcc = vec3(0.0);
    float rAcc = 0.0;

    if (wGrass > 0.01) {
        vec3 c = vec3(uv, 0.0);
        albedo += texture(uAlbedo, c).rgb * wGrass * (0.85 + 0.3 * nz);
        vec4 t = texture(uNormRough, c);
        nAcc += (t.xyz * 2.0 - 1.0) * wGrass;
        rAcc += t.w * wGrass;
    }
    if (wD > 0.01) {
        vec3 c = vec3(wp.xz * 0.25, 1.0);
        albedo += texture(uAlbedo, c).rgb * wD;
        vec4 t = texture(uNormRough, c);
        nAcc += (t.xyz * 2.0 - 1.0) * wD;
        rAcc += t.w * wD;
    }
    if (wS > 0.01) {
        vec3 c = vec3(wp.xz * 0.3, 3.0);
        albedo += texture(uAlbedo, c).rgb * wS;
        vec4 t = texture(uNormRough, c);
        nAcc += (t.xyz * 2.0 - 1.0) * wS;
        rAcc += t.w * wS;
    }
    if (wR > 0.01) {
        // triplanar supaya tebing curam tidak melar
        vec3 bw = pow(abs(N0), vec3(4.0));
        bw /= (bw.x + bw.y + bw.z + 0.0001);
        vec3 cx = vec3(wp.zy * 0.16, 2.0);
        vec3 cy = vec3(wp.xz * 0.16, 2.0);
        vec3 cz = vec3(wp.xy * 0.16, 2.0);
        vec3 a = texture(uAlbedo, cx).rgb * bw.x + texture(uAlbedo, cy).rgb * bw.y + texture(uAlbedo, cz).rgb * bw.z;
        vec4 t = texture(uNormRough, cx) * bw.x + texture(uNormRough, cy) * bw.y + texture(uNormRough, cz) * bw.z;
        albedo += a * wR;
        nAcc += (t.xyz * 2.0 - 1.0) * wR;
        rAcc += t.w * wR;
    }

    // detail normal memudar di kejauhan
    float detail = 1.0 - smoothstep(60.0, 220.0, dist);
    vec3 nl = vec3(nAcc.xy * detail, max(nAcc.z, 0.2));
    vec3 N = applyNormalVec(N0, wp, uv, normalize(nl));

    float ao = 0.78 + 0.22 * smoothstep(0.1, 0.7, nz);
    float NoL0 = max(dot(N0, uLight.xyz), 0.0);
    float shadow = shadowFactor(wp, N0, NoL0);

    vec3 col = lightPBR(albedo, N, V, clamp(rAcc, 0.05, 1.0), shadow, ao);
    col = applyFog(col, wp);
    oColor = vec4(toLdr(col), 1.0);
}
#endif
