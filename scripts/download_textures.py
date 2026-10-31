#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Download texture CC0 dari ambientCG (resolusi 2K JPG) buat Chill World.

Cara pakai:
  python scripts/download_textures.py            (semua material)
  python scripts/download_textures.py grass rock (hanya material tertentu)

Cuma pakai library standar Python (urllib, zipfile, os, shutil).
Tiap texture dicek; kalau ID gagal, dicoba ID cadangan. Hasilnya dicatat
ke scripts/texture_report.txt (ditulis bertahap, jadi aman kalau terputus).
Material yang sudah lengkap di folder textures dilewati, jadi bisa dijalankan
ulang untuk melanjutkan. Kalau gagal, game tetap jalan lewat fallback
texture prosedural di kode.
"""

import os
import shutil
import sys
import tempfile
import traceback
import urllib.error
import urllib.request
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, "app", "src", "main", "assets", "textures")
REPORT = os.path.join(ROOT, "scripts", "texture_report.txt")
CREDITS = os.path.join(ROOT, "CREDITS.md")
USED_FILE = os.path.join(ROOT, "scripts", "texture_used.txt")

URL = "https://ambientcg.com/get?file={id}_2K-JPG.zip"

# nama material -> daftar ID (ID utama dulu, sisanya cadangan)
MATERIALS = {
    "grass": ["Grass001", "Grass002", "Grass003", "Grass004"],
    "ground": ["Ground037", "Ground036", "Ground033", "Ground048"],
    "rock": ["Rock030", "Rock035", "Rock028", "Rock022"],
    "snow": ["Snow004", "Snow003", "Snow005", "Snow006"],
    "bark": ["Bark001", "Bark002", "Bark003", "Bark004"],
    "planks": ["Planks021", "Planks020", "Planks022", "Planks023"],
    "roof": ["RoofingTiles006", "RoofingTiles005", "RoofingTiles007", "RoofingTiles010"],
    "plaster": ["Plaster001", "Plaster002", "Plaster003", "Plaster004"],
}

# akhiran file di dalam zip -> akhiran nama file keluaran
WANTED = {
    "_Color.jpg": "_albedo.jpg",
    "_NormalGL.jpg": "_normal.jpg",
    "_Roughness.jpg": "_rough.jpg",
}


def log(msg):
    print(msg, flush=True)
    with open(REPORT, "a", encoding="utf-8") as f:
        f.write(msg + "\n")


def download(url, dest, timeout=30):
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (ChillWorld texture fetcher)"})
    with urllib.request.urlopen(req, timeout=timeout) as resp, open(dest, "wb") as f:
        shutil.copyfileobj(resp, f)


def is_complete(name):
    for out_suffix in WANTED.values():
        p = os.path.join(OUT_DIR, name + out_suffix)
        if not (os.path.isfile(p) and os.path.getsize(p) > 0):
            return False
    return True


def load_used():
    used = {}
    if os.path.isfile(USED_FILE):
        with open(USED_FILE, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if "=" in line:
                    k, v = line.split("=", 1)
                    used[k] = v
    return used


def save_used(used):
    with open(USED_FILE, "w", encoding="utf-8") as f:
        for k, v in used.items():
            f.write("%s=%s\n" % (k, v))


def try_material(name, asset_id, tmp):
    """Coba unduh satu ID. Return True kalau ketiga file berhasil disalin."""
    url = URL.format(id=asset_id)
    zip_path = os.path.join(tmp, asset_id + ".zip")
    try:
        download(url, zip_path)
    except (urllib.error.URLError, OSError, ValueError) as e:
        log("  [gagal] %s: unduh error (%r)" % (asset_id, e))
        return False

    if not zipfile.is_zipfile(zip_path):
        log("  [gagal] %s: file bukan zip valid (ID mungkin tidak ada)" % asset_id)
        return False

    found = {}
    with zipfile.ZipFile(zip_path) as zf:
        for member in zf.namelist():
            for suffix in WANTED:
                if member.endswith(suffix):
                    found[suffix] = member

        missing = [s for s in WANTED if s not in found]
        if missing:
            log("  [gagal] %s: file tidak lengkap, kurang %s" % (asset_id, ", ".join(missing)))
            return False

        os.makedirs(OUT_DIR, exist_ok=True)
        for suffix, member in found.items():
            target = os.path.join(OUT_DIR, name + WANTED[suffix])
            with zf.open(member) as src, open(target, "wb") as dst:
                shutil.copyfileobj(src, dst)
            if os.path.getsize(target) == 0:
                log("  [gagal] %s: hasil kosong %s" % (asset_id, target))
                return False
    try:
        os.remove(zip_path)
    except OSError:
        pass
    return True


def write_credits(used):
    text = [
        "# Credits",
        "",
        "## Texture",
        "",
        "Semua texture di folder `app/src/main/assets/textures/` berasal dari",
        "[ambientCG](https://ambientcg.com) dan berlisensi **CC0 1.0 (Public Domain)**.",
        "Lisensi: https://creativecommons.org/publicdomain/zero/1.0/",
        "",
        "| Material | ID ambientCG | Sumber |",
        "|---|---|---|",
    ]
    for name in MATERIALS:
        asset_id = used.get(name)
        if asset_id and is_complete(name):
            text.append("| %s | %s | https://ambientcg.com/view?id=%s |" % (name, asset_id, asset_id))
        else:
            text.append("| %s | (fallback prosedural, tanpa texture unduhan) | - |" % name)
    text += [
        "",
        "Material yang memakai fallback dibuat oleh kode (noise prosedural) dan",
        "tidak memakai aset pihak ketiga.",
        "",
    ]
    with open(CREDITS, "w", encoding="utf-8") as f:
        f.write("\n".join(text))


def main():
    names = sys.argv[1:] or list(MATERIALS.keys())
    for n in names:
        if n not in MATERIALS:
            print("Material tidak dikenal: %s" % n)
            return 2

    if not sys.argv[1:] and os.path.isfile(REPORT):
        # run penuh baru: tapi jangan hapus laporan lama kalau melanjutkan
        pass

    used = load_used()
    tmp = tempfile.mkdtemp(prefix="chillworld_tex_")
    try:
        for name in names:
            log("Material: %s" % name)
            if is_complete(name) and name in used and not os.environ.get("CW_FORCE"):
                log("  [lewati] sudah lengkap (%s)" % used[name])
                continue
            ok = False
            for asset_id in MATERIALS[name]:
                if try_material(name, asset_id, tmp):
                    log("  [ok] %s dipakai untuk %s" % (asset_id, name))
                    used[name] = asset_id
                    save_used(used)
                    ok = True
                    break
            if not ok:
                log("  [fallback] %s: semua ID gagal, game pakai texture prosedural" % name)
    except Exception:
        log("ERROR tak terduga:\n" + traceback.format_exc())
        return 1
    finally:
        shutil.rmtree(tmp, ignore_errors=True)

    done = sum(1 for n in MATERIALS if is_complete(n))
    log("")
    log("Ringkasan: %d dari %d material berhasil diunduh." % (done, len(MATERIALS)))
    write_credits(used)
    return 0


if __name__ == "__main__":
    sys.exit(main())
