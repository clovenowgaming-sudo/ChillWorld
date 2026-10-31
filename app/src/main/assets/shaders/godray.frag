#version 300 es
precision highp float;

// Godrays: radial blur dari posisi matahari di layar.
// Sumber cahaya = piksel langit (depth == 1), jadi bolongan daun ikut meloloskan sinar.
in vec2 vUv;
uniform sampler2D uDepth;
uniform vec2 uSunUv;
uniform vec3 uColor;
uniform float uIntensity;
uniform int uSamples;
out vec4 oColor;

float ign(vec2 p) {
    return fract(52.9829189 * fract(dot(p, vec2(0.06711056, 0.00583715))));
}

void main() {
    vec2 delta = (uSunUv - vUv) * (0.9 / float(uSamples));
    vec2 uv = vUv + delta * ign(gl_FragCoord.xy);
    float illum = 0.0;
    float decay = 1.0;
    for (int i = 0; i < uSamples; i++) {
        uv += delta;
        float d = texture(uDepth, uv).r;
        float inb = step(0.0, uv.x) * step(uv.x, 1.0) * step(0.0, uv.y) * step(uv.y, 1.0);
        illum += step(0.99999, d) * inb * decay;
        decay *= 0.965;
    }
    illum /= float(uSamples);
    float fall = clamp(1.0 - length(vUv - uSunUv) * 0.8, 0.0, 1.0);
    oColor = vec4(uColor * illum * uIntensity * (0.35 + 0.65 * fall), 1.0);
}
