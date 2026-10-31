#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Bikin ChillWorld.zip dari seluruh proyek (tanpa folder build / .gradle).
Kalau ~/storage/downloads ada, zip-nya disalin ke sana juga.

Cara pakai: python scripts/pack_zip.py
"""

import os
import shutil
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ZIP_PATH = os.path.join(ROOT, "ChillWorld.zip")

SKIP_DIRS = {"build", ".gradle", ".idea", "__pycache__", ".cxx", ".externalNativeBuild", "captures"}
SKIP_FILES = {"ChillWorld.zip", "local.properties", "_patch.py", "diag_net.py"}


def main():
    if os.path.exists(ZIP_PATH):
        os.remove(ZIP_PATH)

    count = 0
    total = 0
    with zipfile.ZipFile(ZIP_PATH, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as zf:
        for folder, dirs, files in os.walk(ROOT):
            dirs[:] = sorted(d for d in dirs if d not in SKIP_DIRS)
            for fn in sorted(files):
                if fn in SKIP_FILES or fn.endswith(".pyc"):
                    continue
                full = os.path.join(folder, fn)
                rel = os.path.relpath(full, ROOT).replace(os.sep, "/")
                zf.write(full, "ChillWorld/" + rel)
                count += 1
                total += os.path.getsize(full)

    size = os.path.getsize(ZIP_PATH)
    print("Zip dibuat: %s" % ZIP_PATH)
    print("Isi: %d file, %.2f MB asli, %.2f MB zip" % (count, total / 1048576.0, size / 1048576.0))

    # cek isi zip
    with zipfile.ZipFile(ZIP_PATH) as zf:
        bad = zf.testzip()
        if bad:
            print("ZIP RUSAK di: " + bad)
            return 1
        names = zf.namelist()
        for must in ("ChillWorld/settings.gradle.kts", "ChillWorld/app/build.gradle.kts",
                     "ChillWorld/.github/workflows/build.yml"):
            if must not in names:
                print("Zip tidak memuat: " + must)
                return 1

    dl = os.path.expanduser("~/storage/downloads")
    if os.path.isdir(dl):
        try:
            dest = os.path.join(dl, "ChillWorld.zip")
            shutil.copyfile(ZIP_PATH, dest)
            print("Disalin juga ke: " + dest)
        except OSError as e:
            print("Gagal menyalin ke downloads: %r" % (e,))
    else:
        print("Folder ~/storage/downloads tidak ada, lewati salinan.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
