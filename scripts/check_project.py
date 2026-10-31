#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Cek kelengkapan proyek Chill World (statis, tanpa Gradle):
  1. semua file wajib ada
  2. tidak ada file kosong
  3. tiap shader punya #version dan precision
  4. kurung kurawal / kurung biasa / siku di file Kotlin seimbang
  5. semua shader yang dirujuk kode (dan #include) ada
  6. semua texture yang dirujuk kode ada (kalau kurang -> peringatan, karena ada fallback prosedural)
  7. tidak ada kata TODO / dst. di file kode

Cara pakai: python scripts/check_project.py
Exit code 0 kalau bersih, 1 kalau ada error.
"""

import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PKG = os.path.join(ROOT, "app", "src", "main", "java", "com", "vinz", "chillworld")
SHADERS = os.path.join(ROOT, "app", "src", "main", "assets", "shaders")
TEXTURES = os.path.join(ROOT, "app", "src", "main", "assets", "textures")

REQUIRED = [
    "settings.gradle.kts", "build.gradle.kts", "gradle.properties",
    "gradle/wrapper/gradle-wrapper.properties",
    "app/build.gradle.kts", "app/proguard-rules.pro",
    "app/src/main/AndroidManifest.xml",
    "app/src/main/res/values/strings.xml", "app/src/main/res/values/themes.xml",
    ".github/workflows/build.yml", "README.md", "CREDITS.md",
    "scripts/download_textures.py", "scripts/check_project.py", "scripts/pack_zip.py",
]

KOTLIN_REQUIRED = [
    "MainActivity", "GLCaps", "Renderer", "World", "Chunk", "Camera", "Input",
    "ShaderLoader", "TextureLoader", "Settings", "Sky", "Trees", "Houses", "Grass",
    "PostFx", "DayNight",
]

SHADER_REQUIRED = [
    "common.glsl", "lighting.glsl", "terrain.vert", "terrain.frag", "tree.vert", "tree.frag",
    "billboard.vert", "billboard.frag", "grass.vert", "grass.frag", "house.vert", "house.frag",
    "sky.vert", "sky.frag", "fullscreen.vert", "godray.frag", "taa.frag", "bright.frag",
    "blur.frag", "present.frag", "cull.comp",
]

MATERIALS = ["grass", "ground", "rock", "snow", "bark", "planks", "roof", "plaster"]

errors = []
warnings = []


def err(msg):
    errors.append(msg)


def warn(msg):
    warnings.append(msg)


def read(path):
    with open(path, encoding="utf-8", errors="replace") as f:
        return f.read()


def strip_kotlin(src):
    """Buang komentar, string, dan literal char supaya hitung kurung tidak tertipu."""
    out = []
    i = 0
    n = len(src)
    while i < n:
        c = src[i]
        two = src[i:i + 2]
        if two == "//":
            while i < n and src[i] != "\n":
                i += 1
        elif two == "/*":
            j = src.find("*/", i + 2)
            i = n if j < 0 else j + 2
        elif src[i:i + 3] == '"""':
            j = src.find('"""', i + 3)
            i = n if j < 0 else j + 3
        elif c == '"':
            i += 1
            while i < n and src[i] != '"':
                if src[i] == "\\":
                    i += 1
                i += 1
            i += 1
        elif c == "'":
            i += 1
            while i < n and src[i] != "'":
                if src[i] == "\\":
                    i += 1
                i += 1
            i += 1
        else:
            out.append(c)
            i += 1
    return "".join(out)


def check_required():
    for rel in REQUIRED:
        p = os.path.join(ROOT, rel)
        if not os.path.isfile(p):
            err("File wajib tidak ada: " + rel)
        elif os.path.getsize(p) == 0:
            err("File kosong: " + rel)
    for name in KOTLIN_REQUIRED:
        p = os.path.join(PKG, name + ".kt")
        if not os.path.isfile(p):
            err("File Kotlin wajib tidak ada: " + name + ".kt")
    for name in SHADER_REQUIRED:
        p = os.path.join(SHADERS, name)
        if not os.path.isfile(p):
            err("Shader wajib tidak ada: " + name)


def check_empty_all():
    for base in (PKG, SHADERS):
        if not os.path.isdir(base):
            err("Folder tidak ada: " + base)
            continue
        for fn in os.listdir(base):
            p = os.path.join(base, fn)
            if os.path.isfile(p) and os.path.getsize(p) == 0:
                err("File kosong: " + os.path.relpath(p, ROOT))


def check_shaders():
    if not os.path.isdir(SHADERS):
        return
    for fn in sorted(os.listdir(SHADERS)):
        if fn.endswith(".glsl"):
            continue  # potongan include, bukan shader mandiri
        if not fn.endswith((".vert", ".frag", ".comp")):
            continue
        src = read(os.path.join(SHADERS, fn))
        first = src.lstrip().splitlines()[0] if src.strip() else ""
        if not first.startswith("#version"):
            err("Shader tanpa #version di baris pertama: " + fn)
        if not re.search(r"^\s*precision\s+(highp|mediump|lowp)\s+float\s*;", src, re.M):
            err("Shader tanpa precision float: " + fn)
        for inc in re.findall(r'#include\s+"([^"]+)"', src):
            if not os.path.isfile(os.path.join(SHADERS, inc)):
                err("Shader %s meng-include file yang tidak ada: %s" % (fn, inc))


def check_kotlin():
    if not os.path.isdir(PKG):
        return
    for fn in sorted(os.listdir(PKG)):
        if not fn.endswith(".kt"):
            continue
        raw = read(os.path.join(PKG, fn))
        src = strip_kotlin(raw)
        for a, b, name in (("{", "}", "kurung kurawal"), ("(", ")", "kurung biasa"), ("[", "]", "kurung siku")):
            ca, cb = src.count(a), src.count(b)
            if ca != cb:
                err("%s: %s tidak seimbang (%d buka, %d tutup)" % (fn, name, ca, cb))
        if re.search(r"\bTODO\b", raw) or "dst." in raw or "kode lainnya" in raw:
            err("%s: ada penanda belum selesai (TODO / dst.)" % fn)


def check_shader_references():
    if not os.path.isdir(PKG):
        return
    refs = set()
    for fn in os.listdir(PKG):
        if fn.endswith(".kt"):
            for m in re.findall(r'"([A-Za-z0-9_]+\.(?:vert|frag|comp))"', read(os.path.join(PKG, fn))):
                refs.add((m, fn))
    for name, fn in sorted(refs):
        if not os.path.isfile(os.path.join(SHADERS, name)):
            err("%s merujuk shader yang tidak ada: %s" % (fn, name))


def check_textures():
    # TextureLoader membentuk nama dari MATERIALS + akhiran
    loader = os.path.join(PKG, "TextureLoader.kt")
    if os.path.isfile(loader):
        src = read(loader)
        for suffix in ("_albedo.jpg", "_normal.jpg", "_rough.jpg"):
            if suffix not in src:
                err("TextureLoader tidak merujuk akhiran " + suffix)
        for m in MATERIALS:
            if '"%s"' % m not in src:
                err("TextureLoader tidak memuat material: " + m)
    missing = []
    for m in MATERIALS:
        for suffix in ("_albedo.jpg", "_normal.jpg", "_rough.jpg"):
            p = os.path.join(TEXTURES, m + suffix)
            if not os.path.isfile(p) or os.path.getsize(p) == 0:
                missing.append(m + suffix)
    if missing:
        warn("Texture kurang (%d file), game pakai fallback prosedural untuk itu: %s" % (len(missing), ", ".join(missing)))


def check_manifest():
    p = os.path.join(ROOT, "app", "src", "main", "AndroidManifest.xml")
    if os.path.isfile(p):
        s = read(p)
        if 'android:glEsVersion="0x00030000"' not in s:
            err("Manifest tanpa glEsVersion 0x00030000")
        if "sensorLandscape" not in s and "landscape" not in s:
            err("Manifest tidak landscape")


def main():
    check_required()
    check_empty_all()
    check_shaders()
    check_kotlin()
    check_shader_references()
    check_textures()
    check_manifest()

    print("== check_project.py ==")
    for w in warnings:
        print("PERINGATAN: " + w)
    for e in errors:
        print("ERROR: " + e)
    if errors:
        print("Hasil: %d error, %d peringatan" % (len(errors), len(warnings)))
        return 1
    print("Hasil: 0 error, %d peringatan" % len(warnings))
    return 0


if __name__ == "__main__":
    sys.exit(main())
