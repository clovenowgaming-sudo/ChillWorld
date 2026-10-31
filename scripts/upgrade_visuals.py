# Patch upgrade visual untuk Red Magic 11 Pro (preset Ultra, bayangan lembut, pantulan langit, anti-tiling)
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..")
KT = os.path.join(ROOT, "app", "src", "main", "java", "com", "vinz", "chillworld")
SH = os.path.join(ROOT, "app", "src", "main", "assets", "shaders")


def patch(path, pairs):
    with open(path, "r", encoding="utf-8") as f:
        text = f.read()
    for old, new, count in pairs:
        n = text.count(old)
        if n != count:
            print("GAGAL %s: ditemukan %d (butuh %d) untuk: %r" % (os.path.basename(path), n, count, old[:70]))
            sys.exit(1)
        text = text.replace(old, new)
    with open(path, "w", encoding="utf-8") as f:
        f.write(text)
    print("OK  " + os.path.basename(path))


# 1. skrip download: 2K + opsi paksa unduh ulang
patch(os.path.join(HERE, "download_textures.py"), [
    ("_1K-JPG.zip", "_2K-JPG.zip", 1),
    ("(resolusi 1K JPG)", "(resolusi 2K JPG)", 1),
    ("if is_complete(name) and name in used:",
     "if is_complete(name) and name in used and not os.environ.get(\"CW_FORCE\"):", 1),
])

# 2. preset Ultra
patch(os.path.join(KT, "Settings.kt"), [
    ("1024)\n}\n\n/** Pengaturan",
     "1024),\n"
     "    // khusus GPU kelas atas (Adreno 8xx / Red Magic 11 Pro): bayangan 4096, texture 2K\n"
     "    ULTRA(\"Ultra\", 1.00f, 4, 4096, true, true, 16, 12, 1.00f, true, true, 400f, false, 130f, 100f, 280f, 2048)\n"
     "}\n\n/** Pengaturan", 1),
])

# 3. auto-pilih Ultra di Adreno 8xx
patch(os.path.join(KT, "GLCaps.kt"), [
    ("                    n >= 700 -> Quality.HIGH\n",
     "                    n >= 800 -> Quality.ULTRA\n                    n >= 700 -> Quality.HIGH\n", 1),
])

# 4. anisotropic 16x untuk texture 2K
patch(os.path.join(KT, "TextureLoader.kt"), [
    ("minOf(caps.maxAniso, 4f)", "minOf(caps.maxAniso, if (size >= 2048) 16f else 4f)", 1),
])

# 5. panel menu dilebarkan (5 tombol preset)
patch(os.path.join(KT, "MainActivity.kt"), [
    ("FrameLayout.LayoutParams(dp(300), -2, Gravity.CENTER)", "FrameLayout.LayoutParams(dp(400), -2, Gravity.CENTER)", 1),
])

# 6. lighting.glsl: bayangan Poisson lembut + pantulan langit
patch(os.path.join(SH, "lighting.glsl"), [
    ("float shadowCascade(int ci, vec3 wp, vec3 N, float NoL) {",
     "const vec2 POISSON[16] = vec2[16](\n"
     "    vec2(-0.94201624, -0.39906216), vec2(0.94558609, -0.76890725),\n"
     "    vec2(-0.094184101, -0.92938870), vec2(0.34495938, 0.29387760),\n"
     "    vec2(-0.91588581, 0.45771432), vec2(-0.81544232, -0.87912464),\n"
     "    vec2(-0.38277543, 0.27676845), vec2(0.97484398, 0.75648379),\n"
     "    vec2(0.44323325, -0.97511554), vec2(0.53742981, -0.47373420),\n"
     "    vec2(-0.26496911, -0.41893023), vec2(0.79197514, 0.19090188),\n"
     "    vec2(-0.24188840, 0.99706507), vec2(-0.81409955, 0.91437590),\n"
     "    vec2(0.19984126, 0.78641367), vec2(0.14383161, -0.14100790));\n\n"
     "float shadowCascade(int ci, vec3 wp, vec3 N, float NoL) {", 1),
    ("    float inv = uMisc.y;\n"
     "    float sum = 0.0;\n"
     "    for (int x = -1; x <= 1; x++) {\n"
     "        for (int y = -1; y <= 1; y++) {\n"
     "            sum += texture(uShadow, vec4(sc.xy + vec2(float(x), float(y)) * inv, layer, ref));\n"
     "        }\n"
     "    }\n"
     "    return sum / 9.0;\n",
     "    // 16 sampel Poisson diputar acak per piksel (TAA yang merapikan) = bayangan lembut\n"
     "    float inv = uMisc.y;\n"
     "    float ign = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));\n"
     "    float ang = ign * 6.2831853;\n"
     "    vec2 rot = vec2(cos(ang), sin(ang));\n"
     "    float rad = inv * (1.8 + 0.6 * layer);\n"
     "    float sum = 0.0;\n"
     "    for (int i = 0; i < 16; i++) {\n"
     "        vec2 pp = POISSON[i];\n"
     "        vec2 o = vec2(pp.x * rot.x - pp.y * rot.y, pp.x * rot.y + pp.y * rot.x) * rad;\n"
     "        sum += texture(uShadow, vec4(sc.xy + o, layer, ref));\n"
     "    }\n"
     "    return sum * 0.0625;\n", 1),
    ("    float fr = 0.04 + 0.96 * pow(1.0 - NoV, 5.0);\n"
     "    vec3 envRefl = mix(uAmbGround.rgb, uAmbSky.rgb, clamp(reflect(-V, N).y * 0.5 + 0.5, 0.0, 1.0));\n"
     "    return direct + albedo * amb * ao + envRefl * fr * (1.0 - rough) * 0.6 * ao;\n",
     "    // pantulan langit asli (atmosfer + matahari/bulan), makin kasar makin buram\n"
     "    vec3 R = reflect(-V, N);\n"
     "    vec3 skyR = scatterAll(normalize(vec3(R.x, max(R.y, 0.02), R.z)));\n"
     "    vec3 envSoft = mix(uAmbGround.rgb, uAmbSky.rgb, clamp(R.y * 0.5 + 0.5, 0.0, 1.0));\n"
     "    vec3 envR = mix(uAmbGround.rgb, skyR, smoothstep(-0.15, 0.1, R.y));\n"
     "    envR = mix(envR, envSoft, clamp(rough * 1.4, 0.0, 1.0));\n"
     "    float Fr = 0.04 + (max(1.0 - rough, 0.04) - 0.04) * pow(1.0 - NoV, 5.0);\n"
     "    return direct + albedo * amb * ao + envR * Fr * (1.0 - rough) * 0.9 * mix(0.4, 1.0, ao);\n", 1),
])

# 7. terrain.frag: anti-tiling + normal map lebih tegas
patch(os.path.join(SH, "terrain.frag"), [
    ("void main() {\n    vec3 wp = vWorld;",
     "// campur dua sampel (skala & rotasi beda) supaya pola texture tidak keliatan berulang\n"
     "vec4 texA(vec2 uv, float layer) {\n"
     "    vec4 a = texture(uAlbedo, vec3(uv, layer));\n"
     "    vec2 uv2 = mat2(0.8, 0.6, -0.6, 0.8) * uv * 0.37 + vec2(0.31, 0.57);\n"
     "    vec4 b = texture(uAlbedo, vec3(uv2, layer));\n"
     "    return mix(a, b, 0.25 + 0.5 * vnoise(uv * 0.11));\n"
     "}\n\n"
     "vec4 texN(vec2 uv, float layer) {\n"
     "    vec4 a = texture(uNormRough, vec3(uv, layer));\n"
     "    vec2 uv2 = mat2(0.8, 0.6, -0.6, 0.8) * uv * 0.37 + vec2(0.31, 0.57);\n"
     "    vec4 b = texture(uNormRough, vec3(uv2, layer));\n"
     "    return mix(a, b, 0.25 + 0.5 * vnoise(uv * 0.11));\n"
     "}\n\n"
     "void main() {\n    vec3 wp = vWorld;", 1),
    ("texture(uAlbedo, c).rgb", "texA(c.xy, c.z).rgb", 3),
    ("texture(uNormRough, c)", "texN(c.xy, c.z)", 3),
    ("vec3(nAcc.xy * detail, max(nAcc.z, 0.2))", "vec3(nAcc.xy * detail * 1.4, max(nAcc.z, 0.2))", 1),
])

print("Semua patch berhasil.")
