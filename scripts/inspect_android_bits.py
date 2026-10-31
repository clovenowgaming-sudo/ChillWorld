# Cetak baris relevan: Renderer (UI/Input/GLSurfaceView), GLCaps, TextureLoader, ShaderLoader (bagian Android)
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
KT = os.path.join(HERE, "..", "app", "src", "main", "java", "com", "vinz", "chillworld")


def show(name, pattern, ctx=0):
    lines = open(os.path.join(KT, name), encoding="utf-8").read().split("\n")
    print("=== %s ===" % name)
    last = -10
    for i, l in enumerate(lines):
        if re.search(pattern, l):
            lo, hi = max(0, i - ctx), min(len(lines), i + ctx + 1)
            if lo <= last:
                lo = last + 1
            for j in range(lo, hi):
                print("%4d: %s" % (j + 1, lines[j][:170]))
            last = hi - 1
    print()


show("Renderer.kt", r"^import|class |interface |Input\.|Hud|\bui\b|\bui\.|GL10|EGLConfig|Context|ctx|Handler|Looper|onSurface|onDraw|fun ")
show("GLCaps.kt", r".", 0)
show("ShaderLoader.kt", r"^import|Context|ctx|assets|open\(|readBytes|readText|#version|#define|fun |include", 0)
show("TextureLoader.kt", r"^import|Context|ctx|assets|Bitmap|bmp|getPixels|recycle|fun |ASTC|astc|fmt", 0)
for n in ("Camera.kt", "World.kt", "Settings.kt", "DayNight.kt", "Sky.kt", "Trees.kt"):
    show(n, r"^import|Input\.|Context|ctx", 0)
