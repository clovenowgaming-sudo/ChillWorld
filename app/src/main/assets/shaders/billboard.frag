#version 300 es
precision highp float;
#include "common.glsl"
#include "lighting.glsl"

in vec2 vUv;
in vec3 vWorld;
in vec2 vSeed;      // seed, jenis
layout(location = 0) out vec4 oColor;

// siluet pohon prosedural: pinus = kerucut bertingkat, daun lebar = mahkota bundar
float billAlpha(vec2 uv, float species, float seed) {
    float x = (uv.x - 0.5) * 2.0;
    float y = uv.y;
    float trunk = step(abs(x), 0.05 + 0.03 * (1.0 - y)) * step(y, 0.35);
    float a;
    if (species < 0.5) {
        float w = (1.0 - y) * 0.95;
        w *= 0.75 + 0.25 * sin(y * 38.0 + seed * 6.0);
        a = step(abs(x), w) * step(0.12, y);
    } else {
        vec2 c = vec2(x, (y - 0.62) / 0.45);
        a = step(length(c), 1.0 + 0.12 * sin(atan(c.y, c.x) * 7.0 + seed * 9.0));
    }
    float n = hash12(floor(uv * vec2(10.0, 16.0)) + seed * 31.0);
    a *= step(0.18, n);
    return max(a, trunk);
}

void main() {
    if (billAlpha(vUv, vSeed.y, vSeed.x) < 0.5) discard;
    vec3 wp = vWorld;
    CP vec3 base = vSeed.y < 0.5 ? vec3(0.025, 0.085, 0.035) : vec3(0.060, 0.150, 0.025);
    base *= 0.75 + 0.5 * hash12(vec2(vSeed.x * 91.0, 3.0));
    if (vUv.y < 0.3) base = vec3(0.07, 0.045, 0.03);
    vec3 amb = mix(uAmbGround.rgb, uAmbSky.rgb, 0.6);
    float shade = mix(0.55, 1.0, vUv.y);
    vec3 col = base * (amb * shade + uLightColor.rgb * 0.5 * shade);
    col = applyFog(col, wp);
    oColor = vec4(toLdr(col), 1.0);
}
