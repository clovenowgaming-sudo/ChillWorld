#version 300 es
precision highp float;
#include "common.glsl"

// TAA: reprojection dengan history buffer + neighborhood clamping (anti-ghosting)
in vec2 vUv;
uniform sampler2D uScene;   // unit 0
uniform sampler2D uGod;     // unit 1
uniform sampler2D uHist;    // unit 2
uniform sampler2D uDepth;   // unit 3
uniform float uHistValid;
uniform float uGodOn;
out vec4 oColor;

vec3 fetchC(vec2 uv) {
    vec3 c = texture(uScene, uv).rgb;
    if (uGodOn > 0.5) c += texture(uGod, uv).rgb;
    return c;
}

void main() {
    vec2 px = uScreen.zw;
    vec3 c0 = fetchC(vUv);
    vec3 mn = c0;
    vec3 mx = c0;
    vec3 m1 = c0;
    vec3 m2 = c0 * c0;
    float closest = texture(uDepth, vUv).r;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            if (x == 0 && y == 0) continue;
            vec2 o = vec2(float(x), float(y)) * px;
            vec3 s = fetchC(vUv + o);
            mn = min(mn, s);
            mx = max(mx, s);
            m1 += s;
            m2 += s * s;
            closest = min(closest, texture(uDepth, vUv + o).r);
        }
    }
    vec3 mean = m1 / 9.0;
    vec3 sigma = sqrt(max(m2 / 9.0 - mean * mean, vec3(0.0)));
    vec3 vmn = max(mn, mean - 1.4 * sigma);
    vec3 vmx = min(mx, mean + 1.4 * sigma);

    // rekonstruksi posisi dunia dari depth, lalu proyeksikan ke frame lalu
    vec4 ndc = vec4(vUv * 2.0 - 1.0, closest * 2.0 - 1.0, 1.0);
    vec4 wp = uInvVP * ndc;
    wp /= wp.w;
    if (closest > 0.99999) {
        vec3 rd = normalize(wp.xyz - uCamPos.xyz);
        wp = vec4(uCamPos.xyz + rd * 20000.0, 1.0);
    }
    vec4 pc = uPrevVP * wp;
    vec2 prevUv = pc.xy / pc.w * 0.5 + 0.5;
    vec3 hist = texture(uHist, prevUv).rgb;
    hist = clamp(hist, vmn, vmx);

    float inside = step(0.0, prevUv.x) * step(prevUv.x, 1.0) * step(0.0, prevUv.y) * step(prevUv.y, 1.0);
    float speed = length((vUv - prevUv) / px);
    float blend = mix(0.10, 0.45, clamp(speed * 0.08, 0.0, 1.0));
    if (inside < 0.5 || uHistValid < 0.5) blend = 1.0;
    vec3 res = mix(hist, c0, blend);
    // sharpen ringan
    res = clamp(res + (res - mean) * 0.18, 0.0, 1.0);
    oColor = vec4(res, 1.0);
}
