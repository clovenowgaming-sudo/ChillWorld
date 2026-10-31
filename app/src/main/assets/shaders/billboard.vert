#version 300 es
precision highp float;
#include "common.glsl"

// Impostor pohon jauh: kartu yang selalu menghadap kamera (secara horizontal)
layout(location = 0) in vec3 aPos;    // x -1..1, y 0..1
layout(location = 1) in vec3 aNrm;
layout(location = 2) in vec2 aUv;
layout(location = 3) in vec2 aInfo;
layout(location = 4) in vec4 aA;
layout(location = 5) in vec4 aB;

out vec2 vUv;
out vec3 vWorld;
out vec2 vSeed;     // seed, jenis

invariant gl_Position;

void main() {
    vec3 f = vec3(uCamPos.x - aA.x, 0.0, uCamPos.z - aA.z);
    f = normalize(f + vec3(0.0001, 0.0, 0.0));
    vec3 r = vec3(f.z, 0.0, -f.x);
    bool pine = aB.y < 0.5;
    float H = aA.w * (pine ? 15.0 : 12.0);
    float W = aA.w * (pine ? 3.6 : 5.0);
    vec3 wp = aA.xyz + r * aPos.x * W + vec3(0.0, 1.0, 0.0) * aPos.y * H;
    wp += windDisp(wp, aPos.y * aPos.y * 0.5, 0.0);
    vUv = aUv;
    vWorld = wp;
    vSeed = vec2(aB.z, aB.y);
    gl_Position = uVP * vec4(wp, 1.0);
}
