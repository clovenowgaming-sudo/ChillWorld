#version 300 es
precision highp float;
#include "common.glsl"

// Ray Tracing buatan, pass B: denoise bilateral (sadar depth) untuk hasil pass A.
// 3x3 sampel dengan jarak 2 piksel = jejak efektif 5x5, tepi benda tidak ikut kabur.
in vec2 vUv;
uniform sampler2D uRt;      // unit 0: hasil pass A
uniform sampler2D uDepth;   // unit 1
out vec4 oColor;

#include "rtcommon.glsl"

void main() {
    vec2 px = uScreen.zw;
    float d0 = distance(worldAt(vUv), uCamPos.xyz);
    vec4 sum = texture(uRt, vUv);
    float wsum = 1.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            if (x == 0 && y == 0) continue;
            vec2 uv = vUv + vec2(float(x), float(y)) * px * 2.0;
            float d = distance(worldAt(uv), uCamPos.xyz);
            float w = exp(-abs(d - d0) / (0.04 + 0.02 * d0)) * ((x == 0 || y == 0) ? 0.7 : 0.5);
            sum += texture(uRt, uv) * w;
            wsum += w;
        }
    }
    oColor = sum / wsum;
}
