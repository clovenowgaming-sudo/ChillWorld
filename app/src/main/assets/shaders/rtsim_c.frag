#version 300 es
precision highp float;
#include "common.glsl"

// Ray Tracing buatan, pass C: komposit (visibilitas + pantulan warna) lalu Screen Space Reflections.
// SSR hanya memantulkan apa yang kelihatan di layar; pantulan langit sudah dihitung di shader material.
in vec2 vUv;
uniform sampler2D uScene;   // unit 0
uniform sampler2D uDepth;   // unit 1
uniform sampler2D uRt;      // unit 2: hasil denoise pass B
uniform int uSsrSteps;
uniform float uGiStrength;
uniform float uSsrStrength;
out vec4 oColor;

#include "rtcommon.glsl"

void main() {
    vec3 col = texture(uScene, vUv).rgb;
    float d = texture(uDepth, vUv).r;
    if (d >= 0.99999) {
        oColor = vec4(col, 1.0);
        return;
    }
    vec4 rt = texture(uRt, vUv);
    col = col * rt.a;
    col += col * rt.rgb * uGiStrength;

    if (uSsrSteps > 0) {
        vec3 P = worldFromDepth(vUv, d);
        vec3 N = normalAt(vUv, P);
        vec3 V = normalize(P - uCamPos.xyz);
        vec3 R = reflect(V, N);
        float NoV = clamp(dot(N, -V), 0.0, 1.0);
        float F = 0.04 + 0.96 * pow(1.0 - NoV, 5.0);
        float flatness = mix(0.35, 1.0, smoothstep(0.5, 0.95, N.y));
        float facing = smoothstep(-0.05, 0.25, dot(R, V));
        float dist = distance(P, uCamPos.xyz);
        float bias = 0.03 + 0.004 * dist;

        float stepLen = 0.5 + 0.01 * dist;
        float t = stepLen * rtNoise();
        vec3 ro = P + N * bias;
        float hit = 0.0;
        vec3 hitCol = vec3(0.0);
        for (int i = 0; i < 32; i++) {
            if (i >= uSsrSteps) break;
            t += stepLen;
            vec3 sp = ro + R * t;
            vec3 pr = projectUv(sp);
            if (pr.z <= 0.0 || pr.x < 0.0 || pr.x > 1.0 || pr.y < 0.0 || pr.y > 1.0) break;
            float diff = distance(sp, uCamPos.xyz) - distance(worldAt(pr.xy), uCamPos.xyz);
            if (diff > 0.0 && diff < stepLen * 1.5 + bias) {
                vec2 e = min(pr.xy, 1.0 - pr.xy);
                hit = smoothstep(0.0, 0.12, min(e.x, e.y));
                hitCol = texture(uScene, pr.xy).rgb;
                break;
            }
            stepLen *= 1.18;
        }
        col = mix(col, hitCol, clamp(hit * F * uSsrStrength * flatness * facing, 0.0, 0.85));
    }
    oColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
