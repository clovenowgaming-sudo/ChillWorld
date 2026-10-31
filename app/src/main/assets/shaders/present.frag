#version 300 es
precision highp float;

// Pass akhir: upscale ke resolusi layar + sharpening ala CAS + bloom + vignette halus
in vec2 vUv;
uniform sampler2D uImg;     // unit 0: hasil render (resolusi render)
uniform sampler2D uBloom;   // unit 1
uniform vec2 uTexel;        // 1 / ukuran texture render
uniform float uSharp;       // 0 = mati
uniform float uBloomAmt;
out vec4 oColor;

float ign(vec2 p) {
    return fract(52.9829189 * fract(dot(p, vec2(0.06711056, 0.00583715))));
}

void main() {
    vec3 e = texture(uImg, vUv).rgb;
    vec3 col = e;
    if (uSharp > 0.0) {
        vec3 b = texture(uImg, vUv + vec2(0.0, -uTexel.y)).rgb;
        vec3 d = texture(uImg, vUv + vec2(-uTexel.x, 0.0)).rgb;
        vec3 f = texture(uImg, vUv + vec2(uTexel.x, 0.0)).rgb;
        vec3 h = texture(uImg, vUv + vec2(0.0, uTexel.y)).rgb;
        vec3 mn = min(min(min(d, e), min(f, b)), h);
        vec3 mx = max(max(max(d, e), max(f, b)), h);
        vec3 amp = clamp(min(mn, 1.0 - mx) / max(mx, vec3(0.0001)), 0.0, 1.0);
        amp = sqrt(amp);
        float peak = -1.0 / mix(8.0, 5.0, clamp(uSharp, 0.0, 1.0));
        vec3 w = amp * peak;
        col = clamp((b * w + d * w + f * w + h * w + e) / (1.0 + 4.0 * w), 0.0, 1.0);
    }
    if (uBloomAmt > 0.0) col += texture(uBloom, vUv).rgb * uBloomAmt;
    vec2 q = vUv - 0.5;
    float vig = 1.0 - 0.30 * smoothstep(0.25, 0.85, length(q * vec2(1.0, 0.85)));
    col *= vig;
    col += (ign(gl_FragCoord.xy) - 0.5) / 255.0;
    oColor = vec4(col, 1.0);
}
