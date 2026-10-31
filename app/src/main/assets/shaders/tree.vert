#version 300 es
precision highp float;
#include "common.glsl"

// Pohon (batang + kartu daun) dengan instancing. Varian: SHADOW, DEPTH, utama.
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNrm;
layout(location = 2) in vec2 aUv;
layout(location = 3) in vec2 aInfo;   // x = bagian (0 batang, 1 daun), y = fleksibilitas 0..1
layout(location = 4) in vec4 aA;      // xyz posisi, w skala
layout(location = 5) in vec4 aB;      // yaw, jenis, seed, radius

uniform mat4 uShadowVP;

out vec2 vUv;
out vec4 vInfo;      // bagian, seed, jenis, shade
#ifndef SHADOW
out vec3 vWorld;
out vec3 vNrm;
#endif

invariant gl_Position;

void main() {
    vec3 wp = instWorldPos(aPos, aA, aB);
    float fl = aInfo.y * aInfo.y;
    wp += windDisp(wp, fl * 0.7, aInfo.x * aInfo.y);
    vUv = aUv;
    vInfo = vec4(aInfo.x, aB.z, aB.y, aInfo.y);
#ifdef SHADOW
    gl_Position = uShadowVP * vec4(wp, 1.0);
#else
    gl_Position = uVP * vec4(wp, 1.0);
    vWorld = wp;
    vNrm = rotY(aNrm, aB.x);
#endif
}
