#version 300 es
precision highp float;
#include "common.glsl"

// Rumah (dinding plester, atap genteng, papan, kaca). Varian: SHADOW, DEPTH, utama.
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNrm;
layout(location = 2) in vec2 aUv;
layout(location = 3) in vec2 aInfo;   // x = id material
layout(location = 4) in vec4 aA;
layout(location = 5) in vec4 aB;

uniform mat4 uShadowVP;

#ifndef SHADOW
out vec3 vWorld;
out vec3 vNrm;
out vec2 vUv;
out float vMat;
#endif

invariant gl_Position;

void main() {
    vec3 wp = instWorldPos(aPos, aA, aB);
#ifdef SHADOW
    gl_Position = uShadowVP * vec4(wp, 1.0);
#else
    gl_Position = uVP * vec4(wp, 1.0);
    vWorld = wp;
    vNrm = rotY(aNrm, aB.x);
    vUv = aUv;
    vMat = aInfo.x;
#endif
}
