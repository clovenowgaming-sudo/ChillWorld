# Download artifact APK dari run terbaru yang sukses lalu salin ke ~/storage/downloads
import glob
import os
import shutil
import subprocess

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..")
OUT = os.path.join(ROOT, "apk_out")
DL = os.path.expanduser("~/storage/downloads")

rid = subprocess.run(
    ["gh", "run", "list", "--status", "success", "--limit", "1", "--json", "databaseId", "--jq", ".[0].databaseId"],
    cwd=ROOT, capture_output=True, text=True, timeout=60).stdout.strip()
print("Run sukses terbaru: " + rid)

shutil.rmtree(OUT, ignore_errors=True)
r = subprocess.run(["gh", "run", "download", rid, "-n", "ChillWorld-debug-apk", "-D", OUT],
                   cwd=ROOT, capture_output=True, text=True, timeout=600)
print((r.stdout + r.stderr).strip() or "(download selesai)")

apks = glob.glob(os.path.join(OUT, "**", "*.apk"), recursive=True)
if not apks:
    print("APK tidak ditemukan di " + OUT)
else:
    src = apks[0]
    print("APK: %s (%.1f MB)" % (src, os.path.getsize(src) / 1048576))
    if os.path.isdir(DL):
        dst = os.path.join(DL, "ChillWorld-debug.apk")
        shutil.copy2(src, dst)
        print("Disalin ke: %s (%.1f MB)" % (dst, os.path.getsize(dst) / 1048576))
    else:
        print("Folder %s tidak ada" % DL)
