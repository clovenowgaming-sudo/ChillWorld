#version 300 es
precision highp float;
#include "common.glsl"

// Ray Tracing buatan, pass A (screen-space, BUKAN ray tracing hardware):
//  - ambient occlusion (hemisfer cosine di ruang dunia)
//  - contact shadow (ray march pendek ke arah cahaya)
//  - color bleeding / pantulan cahaya palsu (ambil warna tetangga di layar)
// Output: rgb = warna pantulan, a = visibilitas (AO x contact). Noise-nya dirapikan pass B + TAA.
in vec2 vUv;
uniform sampler2D uScene;   // unit 0
uniform sampler2D uDepth;   // unit 1
uniform int uAoSamples;
uniform int uGiSamples;
uniform int uContactSteps;
uniform float uAoRadius;
uniform float uAoStrength;
uniform float uContactStrength;
uniform float uContactLen;
uniform float uGiRadiusPx;
out vec4 oColor;

#include "rtcommon.glsl"

void main() {
    float d = texture(uDepth, vUv).r;
    if (d >= 0.99999) {
        oColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }
    vec3 P = worldFromDepth(vUv, d);
    vec3 N = normalAt(vUv, P);
    float dist = distance(P, uCamPos.xyz);
    float bias = 0.02 + 0.003 * dist;
    float n0 = rtNoise();
    float ang0 = n0 * 6.2831853;

    // ---- ambient occlusion
    float ao = 1.0;
    if (uAoSamples > 0) {
        vec3 upv = (abs(N.y) < 0.99) ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0);
        vec3 T = normalize(cross(upv, N));
        vec3 B = cross(N, T);
        float occ = 0.0;
        for (int i = 0; i < 16; i++) {
            if (i >= uAoSamples) break;
            float fi = (float(i) + 0.5) / float(uAoSamples);
            float a = float(i) * 2.399963 + ang0;
            float r = sqrt(fi);
            vec3 dir = T * (cos(a) * r) + B * (sin(a) * r) + N * sqrt(max(1.0 - fi, 0.0));
            float len = uAoRadius * (0.2 + 0.8 * fract(fi * 3.7 + n0));
            vec3 sp = P + N * bias + dir * len;
            vec3 pr = projectUv(sp);
            if (pr.z <= 0.0 || pr.x < 0.0 || pr.x > 1.0 || pr.y < 0.0 || pr.y > 1.0) continue;
            float sd = distance(worldAt(pr.xy), uCamPos.xyz);
            float ed = distance(sp, uCamPos.xyz);
            float rc = smoothstep(0.0, 1.0, uAoRadius / max(abs(dist - sd), 0.001));
            if (ed - sd > bias) occ += rc;
        }
        ao = 1.0 - occ / float(uAoSamples);
        // cahaya langsung sudah dihitung di scene, jadi AO dilemahkan saat siang terik
        float k = uAoStrength * (1.0 - 0.45 * uLight.w);
        ao = mix(1.0, ao * ao, k);
    }

    // ---- contact shadow
    float contact = 1.0;
    if (uContactSteps > 0 && uLight.w > 0.01) {
        vec3 L = normalize(uLight.xyz);
        if (dot(N, L) > 0.0) {
            float stepLen = uContactLen / float(uContactSteps);
            float jitter = fract(n0 * 7.13);
            float shadow = 0.0;
            for (int i = 0; i < 16; i++) {
                if (i >= uContactSteps) break;
                float t = (float(i) + jitter) * stepLen + 0.04;
                vec3 sp = P + N * bias + L * t;
                vec3 pr = projectUv(sp);
                if (pr.z <= 0.0 || pr.x < 0.0 || pr.x > 1.0 || pr.y < 0.0 || pr.y > 1.0) break;
                float diff = distance(sp, uCamPos.xyz) - distance(worldAt(pr.xy), uCamPos.xyz);
                if (diff > bias * 1.5 && diff < 0.5 + t) {
                    shadow = 1.0;
                    break;
                }
            }
            contact = 1.0 - shadow * uContactStrength * uLight.w;
        }
    }

    // ---- color bleeding / pantulan cahaya palsu
    vec3 gi = vec3(0.0);
    if (uGiSamples > 0) {
        float rpx = clamp(uGiRadiusPx * 10.0 / (dist + 10.0), 6.0, uGiRadiusPx);
        for (int i = 0; i < 12; i++) {
            if (i >= uGiSamples) break;
            float a = float(i) * 2.399963 + ang0;
            float r = (float(i) + 1.0) / float(uGiSamples);
            vec2 suv = vUv + vec2(cos(a), sin(a)) * r * rpx * uScreen.zw;
            if (suv.x < 0.0 || suv.x > 1.0 || suv.y < 0.0 || suv.y > 1.0) continue;
            float sdp = texture(uDepth, suv).r;
            if (sdp >= 0.99999) continue;
            vec3 dv = worldFromDepth(suv, sdp) - P;
            float dl = length(dv);
            if (dl < 0.05 || dl > 6.0) continue;
            float facing = max(dot(N, dv / dl), 0.0);
            float w = (0.15 + facing) / (1.0 + dl * dl * 0.35);
            gi += texture(uScene, suv).rgb * w;
        }
        gi /= float(uGiSamples);
    }

    oColor = vec4(clamp(gi, 0.0, 1.0), clamp(ao * contact, 0.0, 1.0));
}
