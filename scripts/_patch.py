import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PKG = os.path.join(ROOT, "app", "src", "main", "java", "com", "vinz", "chillworld")


def patch(fn, old, new):
    path = os.path.join(PKG, fn)
    with open(path, encoding="utf-8") as f:
        s = f.read()
    if old not in s:
        print("POLA TIDAK DITEMUKAN di " + fn + ": " + old[:50])
        return False
    with open(path, "w", encoding="utf-8") as f:
        f.write(s.replace(old, new, 1))
    print("patch ok: " + fn)
    return True


# 1. 'overlay' di dalam GLSurfaceView anonim bentrok dengan View.getOverlay()
patch("MainActivity.kt",
      "                overlay.postInvalidateOnAnimation()\n                return r",
      "                this@MainActivity.overlay.postInvalidateOnAnimation()\n                return r")

# 2. buang fungsi tak terpakai di ShaderLoader
patch("ShaderLoader.kt",
      "    private fun link(vs: Int, label: String): Int {\n        return vs\n    }\n\n", "")

# 3. info statistik: sederhanakan (parameter tak terpakai)
patch("Renderer.kt",
      "        if (statTimer == 0f) lastInfo(w, c)\n    }\n\n    private var infoVisible = 0\n\n    private fun lastInfo(w: World, c: InstanceCuller) {\n        infoVisible = w.chunks.size\n    }\n",
      "        infoVisible = w.chunks.size\n    }\n\n    private var infoVisible = 0\n")
