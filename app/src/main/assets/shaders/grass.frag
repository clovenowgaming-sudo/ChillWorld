#version 300 es
precision highp float;
#include "common.glsl"
#include "lighting.glsl"

in vec2 vUv;
in vec3 vWorld;
in vec2 vG;
layout(location = 0) out vec4 oColor;

// 3 helai daun per kartu, runcing ke ujung
float bladeMask(vec2 uv, float seed) {
    float gx = uv.x * 3.0;
    float id = floor(gx);
    float f = fract(gx) - 0.5;
    float tilt = (hash12(vec2(id, seed * 10.0)) - 0.5) * 0.3 * uv.y;
    float w = (1.0 - uv.y) * 0.34 + 0.02;
    float top = 0.75 + 0.25 * hash12(vec2(id + 4.0, seed * 7.0));
    return step(abs(f - tilt), w) * step(uv.y, top);
}

void main() {
    if (bladeMask(vUv, vG.x) < 0.5) discard;
    vec3 wp = vWorld;
    float nz = fbm2(wp.xz * 0.08);
    CP vec3 root = vec3(0.020, 0.055, 0.012);
    CP vec3 tip = vec3(0.120, 0.260, 0.050);
    CP vec3 dry = vec3(0.200, 0.170, 0.040);
    float hv = vUv.y;
    CP vec3 col = mix(root, tip, hv * hv);
    col = mix(col, dry * (0.5 + 0.5 * hv), smoothstep(0.55, 0.8, nz) * 0.7);
    col *= 0.75 + 0.5 * vG.x;

    vec3 L = uLight.xyz;
    vec3 V = normalize(uCamPos.xyz - wp);
    vec3 N = normalize(vec3((vG.x - 0.5) * 0.5, 1.0, (fract(vG.x * 7.3) - 0.5) * 0.5));
    float wrap = 0.5 + 0.5 * dot(N, L);
    float shadow = shadowFactor(wp, vec3(0.0, 1.0, 0.0), max(L.y, 0.0));
    float trans = pow(max(dot(V, -L), 0.0), 3.0) * hv;
    vec3 amb = mix(uAmbGround.rgb, uAmbSky.rgb, 0.7) * (0.5 + 0.5 * hv);
    vec3 lit = col * (amb + uLightColor.rgb * (wrap * 0.9 + trans * 0.6) * shadow);
    lit = applyFog(lit, wp);
    oColor = vec4(toLdr(lit), 1.0);
}
