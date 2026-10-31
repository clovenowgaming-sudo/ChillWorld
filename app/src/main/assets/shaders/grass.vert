#version 300 es
precision highp float;
#include "common.glsl"

// Rumput: gerombol 3 kartu silang, instancing, bergoyang oleh angin
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNrm;
layout(location = 2) in vec2 aUv;
layout(location = 3) in vec2 aInfo;   // y = fraksi tinggi 0..1
layout(location = 4) in vec4 aA;
layout(location = 5) in vec4 aB;

uniform float uGrassDist;

out vec2 vUv;
out vec3 vWorld;
out vec2 vG;       // seed, fraksi tinggi

invariant gl_Position;

void main() {
    // mengecil di ujung jarak supaya tidak muncul tiba-tiba
    float d = distance(aA.xyz, uCamPos.xyz);
    float fade = 1.0 - smoothstep(0.7, 1.0, d / uGrassDist);
    vec4 a = vec4(aA.xyz, aA.w * fade);
    vec3 wp = instWorldPos(aPos, a, aB);
    float hf = aInfo.y;
    wp += windDisp(wp, hf * hf * 1.6, hf * 0.6);
    vUv = aUv;
    vWorld = wp;
    vG = vec2(aB.z, hf);
    gl_Position = uVP * vec4(wp, 1.0);
}
