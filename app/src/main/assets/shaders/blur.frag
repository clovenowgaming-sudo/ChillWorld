#version 300 es
precision highp float;

// Blur gaussian terpisah (horizontal / vertikal lewat uDir), 5 tap bilinear
in vec2 vUv;
uniform sampler2D uImg;
uniform vec2 uDir;
out vec4 oColor;

void main() {
    vec3 c = texture(uImg, vUv).rgb * 0.2270270270;
    c += texture(uImg, vUv + uDir * 1.3846153846).rgb * 0.3162162162;
    c += texture(uImg, vUv - uDir * 1.3846153846).rgb * 0.3162162162;
    c += texture(uImg, vUv + uDir * 3.2307692308).rgb * 0.0702702703;
    c += texture(uImg, vUv - uDir * 3.2307692308).rgb * 0.0702702703;
    oColor = vec4(c, 1.0);
}
