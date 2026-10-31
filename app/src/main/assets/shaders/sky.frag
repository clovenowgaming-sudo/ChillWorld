#version 300 es
precision highp float;
#include "common.glsl"
#include "lighting.glsl"

in vec2 vUv;
layout(location = 0) out vec4 oColor;

void main() {
    vec2 ndc = vUv * 2.0 - 1.0;
    vec4 w = uInvVP * vec4(ndc, 1.0, 1.0);
    vec3 rd = normalize(w.xyz / w.w - uCamPos.xyz);

    // langit dasar (Rayleigh + Mie), di bawah horizon dipakai warna horizon
    vec3 rdc = normalize(vec3(rd.x, max(rd.y, 0.0), rd.z));
    vec3 col = scatterAll(rdc);
    if (rd.y < 0.0) col *= 0.7;

    float night = 1.0 - uMisc.z;

    // matahari
    float sd = dot(rd, uSunDir.xyz);
    float sunDisc = smoothstep(0.99955, 0.99985, sd);
    col += uSunColor.rgb * sunDisc * 12.0;

    // bulan
    float md = dot(rd, uMoonDir.xyz);
    float moonDisc = smoothstep(0.99965, 0.99985, md);
    float crater = 0.8 + 0.2 * vnoise(rd.xz * 400.0 + rd.y * 300.0);
    col += vec3(0.75, 0.82, 1.0) * moonDisc * crater * 1.6 * night;

    // bintang
    if (rd.y > 0.0) {
        vec3 q = floor(rd * 220.0);
        float s = hash12(q.xy + q.z * 57.0);
        float star = step(0.9972, s) * (0.5 + 0.5 * hash12(q.yz));
        star *= smoothstep(0.0, 0.2, rd.y) * night * (1.0 - moonDisc);
        col += vec3(star) * 1.5;
    }

    // awan 2D bergerak pelan
    if (rd.y > 0.015) {
        float t = 1500.0 / rd.y;
        vec2 cp = (uCamPos.xz + rd.xz * t) * 0.00028 + vec2(uCamPos.w * 0.004, uCamPos.w * 0.0015);
        float n = fbm2(cp * 3.0);
        float cov = uMisc.w;
        float dens = smoothstep(0.58 - cov * 0.35, 0.82, n);
        float fadeH = smoothstep(0.015, 0.2, rd.y);
        vec3 cloudCol = (uAmbSky.rgb * 1.1 + uLightColor.rgb * 0.5) * (1.0 - 0.35 * dens);
        cloudCol += uSunColor.rgb * pow(max(sd, 0.0), 8.0) * 0.3;
        col = mix(col, cloudCol, dens * fadeH * 0.92);
    }

    oColor = vec4(toLdr(col), 1.0);
}
