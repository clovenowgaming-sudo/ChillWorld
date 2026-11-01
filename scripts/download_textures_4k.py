#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Download texture NATIVE 4K (4096x4096) CC0 dari ambientCG untuk versi PC Chill World.

  python scripts/download_textures_4k.py            (semua material)
  python scripts/download_textures_4k.py grass rock (hanya material tertentu)

- Hasil masuk ke assets_pc/textures/ (di luar APK dan di luar Git; ukurannya ratusan MB).
- ID yang dipakai sama dengan set 2K (scripts/texture_used.txt) supaya tampilan konsisten.
- Setiap file dicek lewat header JPEG: kalau bukan >= 4096 px, DITOLAK (tidak ada upscale palsu).
- Lisensi CC0, sumber dicatat di CREDITS.md. Hanya library standar Python.
"""

import importlib.util
import os
import shutil
import sys
import tempfile
import traceback

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("dt", os.path.join(HERE, "download_textures.py"))
dt = importlib.util.module_from_spec(spec)
spec.loader.exec_module(dt)

# arahkan skrip 2K ke mode 4K
dt.OUT_DIR = os.path.join(dt.ROOT, "assets_pc", "textures")
dt.URL = "https://ambientcg.com/get?file={id}_4K-JPG.zip"
dt.REPORT = os.path.join(dt.ROOT, "scripts", "texture_report_4k.txt")
_orig_download = dt.download
dt.download = lambda url, dest, timeout=60: _orig_download(url, dest, 60)

MIN_SIZE = 4096


def jpeg_size(path):
    """(lebar, tinggi) dari header JPEG, tanpa library gambar."""
    with open(path, "rb") as f:
        if f.read(2) != b"\xff\xd8":
            return None
        while True:
            b = f.read(1)
            if not b:
                return None
            if b != b"\xff":
                continue
            m = f.read(1)
            while m == b"\xff":
                m = f.read(1)
            if not m:
                return None
            if m in (b"\xc0", b"\xc1", b"\xc2"):
                f.read(3)
                h = int.from_bytes(f.read(2), "big")
                w = int.from_bytes(f.read(2), "big")
                return w, h
            if m == b"\xd8" or m == b"\x01" or (b"\xd0" <= m <= b"\xd7"):
                continue
            ln = int.from_bytes(f.read(2), "big")
            f.seek(ln - 2, 1)


def native_ok(name):
    for suffix in dt.WANTED.values():
        p = os.path.join(dt.OUT_DIR, name + suffix)
        if not os.path.isfile(p):
            return False
        s = jpeg_size(p)
        if not s or s[0] < MIN_SIZE or s[1] < MIN_SIZE:
            return False
    return True


def main():
    names = sys.argv[1:] or list(dt.MATERIALS.keys())
    used_2k = dt.load_used()
    used4k = {}
    os.makedirs(dt.OUT_DIR, exist_ok=True)
    tmp = tempfile.mkdtemp(prefix="chillworld_tex4k_")
    try:
        for name in names:
            dt.log("Material 4K: %s" % name)
            if native_ok(name) and not os.environ.get("CW_FORCE"):
                dt.log("  [lewati] sudah lengkap dan native 4K")
                continue
            ids = []
            if used_2k.get(name):
                ids.append(used_2k[name])
            ids += [i for i in dt.MATERIALS[name] if i not in ids]
            ok = False
            for asset_id in ids:
                if not dt.try_material(name, asset_id, tmp):
                    continue
                if native_ok(name):
                    dt.log("  [ok] %s native 4K untuk %s" % (asset_id, name))
                    used4k[name] = asset_id
                    ok = True
                    break
                s = jpeg_size(os.path.join(dt.OUT_DIR, name + "_albedo.jpg"))
                dt.log("  [tolak] %s: ukuran %s bukan 4096x4096, tidak dipakai" % (asset_id, s))
                for suffix in dt.WANTED.values():
                    try:
                        os.remove(os.path.join(dt.OUT_DIR, name + suffix))
                    except OSError:
                        pass
            if not ok:
                dt.log("  [gagal] %s: tidak ada versi native 4K, PC tetap pakai 2K untuk material ini" % name)
    except Exception:
        dt.log("ERROR tak terduga:\n" + traceback.format_exc())
        return 1
    finally:
        shutil.rmtree(tmp, ignore_errors=True)

    done = sum(1 for n in dt.MATERIALS if native_ok(n))
    dt.log("")
    dt.log("Ringkasan: %d dari %d material native 4K." % (done, len(dt.MATERIALS)))

    credits = os.path.join(dt.ROOT, "CREDITS.md")
    note = "\n## Texture 4K (khusus PC)\n\nVersi 4K asli dari ambientCG (CC0) yang sama, disimpan di `assets_pc/textures/` (tidak ikut repo/APK). ID: %s\n" % (
        ", ".join("%s=%s" % kv for kv in sorted(used4k.items())) or "-")
    if os.path.isfile(credits) and "Texture 4K (khusus PC)" not in open(credits, encoding="utf-8").read():
        with open(credits, "a", encoding="utf-8") as f:
            f.write(note)
    return 0


if __name__ == "__main__":
    sys.exit(main())
