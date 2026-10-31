// lighting.glsl - fungsi pencahayaan (khusus fragment shader), di-include ShaderLoader.
// Harus di-include SETELAH common.glsl.

precision highp sampler2DArray;
precision highp sampler2DArrayShadow;

uniform highp sampler2DArrayShadow uShadow;   // unit 0
uniform CP sampler2DArray uAlbedo;            // unit 1 (sRGB)
uniform CP sampler2DArray uNormRough;         // unit 2 (rgb = normal, a = roughness)

const vec3 BETA_R = vec3(0.0058, 0.0135, 0.0331);
const float BETA_M = 0.014;

// ---------------------------------------------------------------- atmosfer
vec3 scatterFrom(vec3 rd, vec3 ldir, vec3 lcol) {
    float mu = dot(rd, ldir);
    float air = min(1.0 / (max(rd.y, 0.0) * 0.8 + 0.12), 8.0);
    vec3 beta = BETA_R + vec3(BETA_M);
    vec3 ext = exp(-beta * air * 12.0);
    float phR = 0.0596831 * (1.0 + mu * mu);
    float g = 0.76;
    float phM = 0.0795775 * ((1.0 - g * g) * (1.0 + mu * mu)) /
                ((2.0 + g * g) * pow(1.0 + g * g - 2.0 * g * mu, 1.5));
    vec3 sc = (BETA_R * phR + vec3(BETA_M * phM)) / beta;
    return sc * (1.0 - ext) * lcol * 10.0;
}

vec3 scatterAll(vec3 rd) {
    vec3 c = scatterFrom(rd, uSunDir.xyz, uSunColor.rgb) +
             scatterFrom(rd, uMoonDir.xyz, uMoonColor.rgb);
    return c + vec3(0.003, 0.005, 0.012) * (1.0 - uMisc.z);
}

// ---------------------------------------------------------------- tone mapping
vec3 tonemapAces(vec3 x) {
    x *= uAmbSky.w;
    vec3 a = x * (2.51 * x + 0.03);
    vec3 b = x * (2.43 * x + 0.59) + 0.14;
    return clamp(a / b, 0.0, 1.0);
}

vec3 toLdr(vec3 hdr) {
    return pow(tonemapAces(hdr), vec3(1.0 / 2.2));
}

// ---------------------------------------------------------------- kabut
vec3 applyFog(vec3 col, vec3 wp) {
    vec3 v = wp - uCamPos.xyz;
    float d = length(v);
    float h = 0.5 * (wp.y + uCamPos.y);
    float x = d * uFog.x * exp(-max(h, 0.0) * 0.0035);
    float f = 1.0 - exp(-pow(x, 1.6));
    vec3 rd = v / max(d, 0.001);
    vec3 fc = scatterAll(normalize(vec3(rd.x, max(rd.y, 0.0) * 0.4 + 0.03, rd.z)));
    return mix(col, fc, clamp(f, 0.0, 1.0));
}

// ---------------------------------------------------------------- normal map
mat3 cotangentFrame(vec3 N, vec3 p, vec2 uv) {
    vec3 dp1 = dFdx(p);
    vec3 dp2 = dFdy(p);
    vec2 duv1 = dFdx(uv);
    vec2 duv2 = dFdy(uv);
    vec3 dp2perp = cross(dp2, N);
    vec3 dp1perp = cross(N, dp1);
    vec3 T = dp2perp * duv1.x + dp1perp * duv2.x;
    vec3 B = dp2perp * duv1.y + dp1perp * duv2.y;
    float invmax = inversesqrt(max(max(dot(T, T), dot(B, B)), 1e-12));
    return mat3(T * invmax, B * invmax, N);
}

// n = vektor normal sudah di-decode (-1..1), format NormalGL
vec3 applyNormalVec(vec3 N, vec3 p, vec2 uv, vec3 n) {
    n.y = -n.y;
    mat3 TBN = cotangentFrame(N, p, uv);
    return normalize(TBN * n);
}

// ---------------------------------------------------------------- bayangan (CSM)
float viewDepthOf(vec3 wp) {
    return -(uView[0][2] * wp.x + uView[1][2] * wp.y + uView[2][2] * wp.z + uView[3][2]);
}

float cascadeTexel(int i) {
    return i == 0 ? uTexel.x : (i == 1 ? uTexel.y : (i == 2 ? uTexel.z : uTexel.w));
}

float cascadeEnd(int i) {
    return i == 0 ? uSplits.x : (i == 1 ? uSplits.y : (i == 2 ? uSplits.z : uSplits.w));
}

float shadowCascade(int ci, vec3 wp, vec3 N, float NoL) {
    // bias berbasis kemiringan: makin miring permukaan, offset normal makin besar
    float texel = cascadeTexel(ci);
    vec3 off = N * texel * (1.5 + 2.5 * (1.0 - NoL));
    vec4 lp = uLightVP[ci] * vec4(wp + off, 1.0);
    vec3 sc = lp.xyz * 0.5 + 0.5;
    if (sc.x < 0.0 || sc.x > 1.0 || sc.y < 0.0 || sc.y > 1.0 || sc.z > 1.0) return 1.0;
    float ref = sc.z - 0.0004;
    float layer = float(ci);
#ifdef FAST_SHADOW
    return texture(uShadow, vec4(sc.xy, layer, ref));
#else
    float inv = uMisc.y;
    float sum = 0.0;
    for (int x = -1; x <= 1; x++) {
        for (int y = -1; y <= 1; y++) {
            sum += texture(uShadow, vec4(sc.xy + vec2(float(x), float(y)) * inv, layer, ref));
        }
    }
    return sum / 9.0;
#endif
}

// hasil 1 = terang, 0 = bayangan. Antar cascade dicampur halus.
float shadowFactor(vec3 wp, vec3 N, float NoL) {
    if (uLight.w <= 0.001) return 1.0;
    int nc = int(uMisc.x + 0.5);
    float vd = viewDepthOf(wp);
    int ci = 0;
    if (nc > 1 && vd > uSplits.x) ci = 1;
    if (nc > 2 && vd > uSplits.y) ci = 2;
    if (nc > 3 && vd > uSplits.z) ci = 3;
    float s = shadowCascade(ci, wp, N, NoL);
    float endD = cascadeEnd(ci);
    float beginD = ci == 0 ? 0.0 : cascadeEnd(ci - 1);
    float bw = (endD - beginD) * 0.12;
    if (ci < nc - 1 && vd > endD - bw) {
        float s2 = shadowCascade(ci + 1, wp, N, NoL);
        s = mix(s, s2, clamp((vd - (endD - bw)) / bw, 0.0, 1.0));
    }
    float fade = smoothstep(uSplits.w * 0.85, uSplits.w, vd);
    s = mix(s, 1.0, fade);
    return mix(1.0, s, uLight.w);
}

// ---------------------------------------------------------------- PBR sederhana
vec3 lightPBR(vec3 albedo, vec3 N, vec3 V, float rough, float shadow, float ao) {
    vec3 L = uLight.xyz;
    float NoL = max(dot(N, L), 0.0);
    vec3 H = normalize(L + V);
    float NoV = max(dot(N, V), 0.001);
    float NoH = max(dot(N, H), 0.0);
    float VoH = max(dot(V, H), 0.0);
    float a = max(rough * rough, 0.04);
    float a2 = a * a;
    float dd = NoH * NoH * (a2 - 1.0) + 1.0;
    float D = a2 / (PI * dd * dd);
    float k = a * 0.5;
    float vis = 0.25 / ((NoL * (1.0 - k) + k) * (NoV * (1.0 - k) + k) + 0.0001);
    float F = 0.04 + 0.96 * pow(1.0 - VoH, 5.0);
    vec3 spec = vec3(D * vis * F * NoL);
    vec3 direct = (albedo * NoL + spec) * uLightColor.rgb * shadow;
    // ambient hemisphere dari langit + sedikit pantulan Fresnel
    vec3 amb = mix(uAmbGround.rgb, uAmbSky.rgb, N.y * 0.5 + 0.5);
    float fr = 0.04 + 0.96 * pow(1.0 - NoV, 5.0);
    vec3 envRefl = mix(uAmbGround.rgb, uAmbSky.rgb, clamp(reflect(-V, N).y * 0.5 + 0.5, 0.0, 1.0));
    return direct + albedo * amb * ao + envRefl * fr * (1.0 - rough) * 0.6 * ao;
}
