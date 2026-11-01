// rtcommon.glsl - fungsi bersama pass Ray Tracing buatan (screen-space).
// Bukan shader mandiri: di-include SETELAH common.glsl dan SETELAH `uniform sampler2D uDepth;` dideklarasikan.

// posisi dunia dari depth buffer (inverse view-proj tanpa jitter)
vec3 worldFromDepth(vec2 uv, float d) {
    vec4 ndc = vec4(uv * 2.0 - 1.0, d * 2.0 - 1.0, 1.0);
    vec4 wp = uInvVP * ndc;
    return wp.xyz / wp.w;
}

vec3 worldAt(vec2 uv) {
    return worldFromDepth(uv, texture(uDepth, uv).r);
}

// proyeksi titik dunia ke layar: xy = uv, z = clip w (<= 0 berarti di belakang kamera)
vec3 projectUv(vec3 wp) {
    vec4 c = uVP * vec4(wp, 1.0);
    return vec3(c.xy / c.w * 0.5 + 0.5, c.w);
}

// normal permukaan dari turunan posisi dunia (ambil sisi tetangga yang lebih dekat supaya tepi benda tidak rusak)
vec3 normalAt(vec2 uv, vec3 P) {
    vec2 px = uScreen.zw;
    vec3 pl = worldAt(uv - vec2(px.x, 0.0));
    vec3 pr = worldAt(uv + vec2(px.x, 0.0));
    vec3 pd = worldAt(uv - vec2(0.0, px.y));
    vec3 pu = worldAt(uv + vec2(0.0, px.y));
    vec3 dx = (length(pr - P) < length(P - pl)) ? (pr - P) : (P - pl);
    vec3 dy = (length(pu - P) < length(P - pd)) ? (pu - P) : (P - pd);
    vec3 n = normalize(cross(dx, dy));
    if (dot(n, uCamPos.xyz - P) < 0.0) n = -n;
    return n;
}

// interleaved gradient noise + variasi per frame (TAA yang merata-ratakan noise ini)
float ign(vec2 p) {
    return fract(52.9829189 * fract(dot(p, vec2(0.06711056, 0.00583715))));
}

float rtNoise() {
    return ign(gl_FragCoord.xy + 5.588238 * mod(floor(uCamPos.w * 60.0), 64.0));
}
