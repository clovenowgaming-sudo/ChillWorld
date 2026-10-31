# Inspeksi project: ukuran, import android.*, pemakaian GLES, versi shader (cuma baca)
import collections
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..")
KT = os.path.join(ROOT, "app", "src", "main", "java", "com", "vinz", "chillworld")
SH = os.path.join(ROOT, "app", "src", "main", "assets", "shaders")

print("== Kotlin: baris | import android.* | panggilan GLES ==")
tot = [0, 0, 0]
android_imports = collections.Counter()
for name in sorted(os.listdir(KT)):
    if not name.endswith(".kt"):
        continue
    text = open(os.path.join(KT, name), encoding="utf-8").read()
    lines = text.count("\n") + 1
    imps = re.findall(r"^import (android\.[\w.]+|java\.nio\.[\w.]+)", text, re.M)
    gles = len(re.findall(r"\bGLES\d*\w*\.\w+|\bGLES\d*\w*\b", text))
    for i in imps:
        android_imports[i] += 1
    print("%-22s %5d | %2d | %4d" % (name, lines, len([i for i in imps if i.startswith("android")]), gles))
    tot[0] += lines
    tot[1] += len([i for i in imps if i.startswith("android")])
    tot[2] += gles
print("TOTAL %d baris, %d import android, %d referensi GLES" % tuple(tot))

print("\n== import android.* yang dipakai ==")
for k, v in android_imports.most_common():
    if k.startswith("android"):
        print("%3d  %s" % (v, k))

print("\n== Shader: #version ==")
ver = collections.Counter()
for name in sorted(os.listdir(SH)):
    text = open(os.path.join(SH, name), encoding="utf-8").read()
    m = re.search(r"#version (\d+ ?\w*)", text)
    ver[m.group(1) if m else "(include/tanpa version)"] += 1
for k, v in ver.items():
    print("%3d file  %s" % (v, k))

print("\n== Fitur GLES yang dipakai (khusus ES / perlu dipetakan ke desktop) ==")
feat = ["glDrawElementsIndirect", "GL_SHADER_STORAGE", "glDispatchCompute", "glBindBufferBase", "GL_UNIFORM_BUFFER",
        "glDrawElementsInstanced", "GL_TEXTURE_2D_ARRAY", "GL_DEPTH_COMPONENT16", "GL_TEXTURE_COMPARE_MODE",
        "glTexStorage", "GL_KHR_texture_compression_astc", "GL_TEXTURE_MAX_ANISOTROPY", "glFramebufferTextureLayer"]
alltext = "".join(open(os.path.join(KT, n), encoding="utf-8").read() for n in os.listdir(KT) if n.endswith(".kt"))
for f in feat:
    print("%-40s %d" % (f, alltext.count(f)))
