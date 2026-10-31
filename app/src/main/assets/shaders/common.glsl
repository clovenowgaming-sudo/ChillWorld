// common.glsl - potongan shader bersama, di-include oleh ShaderLoader.
// Bukan shader mandiri, jadi tidak ada #version di sini.

#ifdef LOW_PRECISION
#define CP mediump
#else
#define CP highp
#endif

// UBO "Frame": data yang dipakai bareng semua shader (binding point 0)
layout(std140) uniform Frame {
    mat4 uVP;            // view-proj (sudah kena jitter TAA)
    mat4 uView;
    mat4 uInvVP;         // inverse view-proj TANPA jitter
    mat4 uPrevVP;        // view-proj frame lalu TANPA jitter
    mat4 uLightVP[4];
    vec4 uCamPos;        // xyz = posisi kamera, w = waktu (detik)
    vec4 uSunDir;        // arah KE matahari
    vec4 uSunColor;
    vec4 uMoonDir;
    vec4 uMoonColor;
    vec4 uLightColor;    // warna cahaya utama (matahari atau bulan)
    vec4 uAmbSky;        // rgb = ambient langit, w = exposure
    vec4 uAmbGround;
    vec4 uFog;           // x = kepadatan kabut
    vec4 uSplits;        // batas jarak tiap cascade
    vec4 uTexel;         // ukuran texel shadow (meter) tiap cascade
    vec4 uScreen;        // lebar, tinggi, 1/lebar, 1/tinggi (resolusi render)
    vec4 uWind;          // xy = arah angin, z = kekuatan, w = hembusan
    vec4 uMisc;          // x = jumlah cascade, y = 1/resolusi shadow, z = faktor siang, w = awan
    vec4 uLight;         // xyz = arah ke cahaya utama, w = kekuatan bayangan
};

const float PI = 3.14159265;

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec2 hash22(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.xx + p3.yz) * p3.zy);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash12(i);
    float b = hash12(i + vec2(1.0, 0.0));
    float c = hash12(i + vec2(0.0, 1.0));
    float d = hash12(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm2(vec2 p) {
    float s = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        s += a * vnoise(p);
        p = p * 2.03 + vec2(17.1, 5.3);
        a *= 0.5;
    }
    return s;
}

vec3 rotY(vec3 v, float a) {
    float c = cos(a);
    float s = sin(a);
    return vec3(c * v.x + s * v.z, v.y, -s * v.x + c * v.z);
}

// posisi dunia dari vertex lokal + data instance (a = xyz+skala, b = yaw,...)
vec3 instWorldPos(vec3 local, vec4 a, vec4 b) {
    return a.xyz + rotY(local, b.x) * a.w;
}

// goyangan angin: flex = seberapa lentur, flutter = getaran halus daun
vec3 windDisp(vec3 wp, float flex, float flutter) {
    float t = uCamPos.w;
    vec2 dir = uWind.xy;
    float str = uWind.z;
    float ph = dot(wp.xz, dir) * 0.12 - t * 1.4;
    float wave = sin(ph) * 0.55 + sin(ph * 2.3 + 1.7) * 0.25 + 0.5;
    wave += uWind.w * (0.5 + 0.5 * sin(ph * 0.5 - t * 0.6));
    vec2 d = dir * wave * str * flex;
    float fl = sin(t * 7.0 + wp.x * 1.7 + wp.z * 2.3) * flutter * str * 0.06;
    return vec3(d.x + fl, fl * 0.5, d.y + fl * 0.7);
}

// mask daun prosedural (0 = bolong, 1 = daun). species 0 = pinus, 1 = daun lebar
float leafMask(vec2 uv, float species, float seed) {
    vec2 g = (species < 0.5) ? uv * vec2(6.0, 11.0) : uv * vec2(8.0, 8.0);
    g += vec2(seed * 13.7, seed * 7.1);
    vec2 id = floor(g);
    vec2 f = fract(g) - 0.5;
    vec2 j = hash22(id) - 0.5;
    f -= j * 0.5;
    float r = 0.30 + 0.22 * hash12(id + 3.7);
    vec2 s = (species < 0.5) ? f * vec2(1.9, 0.75) : f;
    float blob = step(length(s), r);
    vec2 c = uv * 2.0 - 1.0;
    float edge = 1.0 - smoothstep(0.55, 1.0, length(c));
    float keep = step(hash12(id + 9.1) * 0.55, edge + 0.15);
    return blob * keep;
}
