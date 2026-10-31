#version 300 es
precision highp float;

// Ambil bagian terang saja untuk bloom (render ke 1/4 resolusi)
in vec2 vUv;
uniform sampler2D uImg;
uniform vec2 uTexel;
out vec4 oColor;

void main() {
    vec3 c = texture(uImg, vUv + vec2(-uTexel.x, -uTexel.y)).rgb;
    c += texture(uImg, vUv + vec2(uTexel.x, -uTexel.y)).rgb;
    c += texture(uImg, vUv + vec2(-uTexel.x, uTexel.y)).rgb;
    c += texture(uImg, vUv + vec2(uTexel.x, uTexel.y)).rgb;
    c *= 0.25;
    float l = dot(c, vec3(0.299, 0.587, 0.114));
    float k = smoothstep(0.68, 1.0, l);
    oColor = vec4(c * k, 1.0);
}
