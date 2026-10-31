#version 300 es
precision highp float;
#include "common.glsl"

// Terrain: posisi lokal chunk + offset chunk (uOfs). Varian: SHADOW, DEPTH, utama.
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNrm;

uniform vec2 uOfs;
uniform mat4 uShadowVP;

#ifndef SHADOW
out vec3 vWorld;
out vec3 vNrm;
#endif

invariant gl_Position;

void main() {
    vec3 wp = vec3(aPos.x + uOfs.x, aPos.y, aPos.z + uOfs.y);
#ifdef SHADOW
    gl_Position = uShadowVP * vec4(wp, 1.0);
#else
    gl_Position = uVP * vec4(wp, 1.0);
    vWorld = wp;
    vNrm = aNrm;
#endif
}
