# Pastikan APK sudah ada di ~/storage/downloads (salin kalau belum), cetak ukuran
import os
import shutil

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "apk_out", "app-debug.apk")
DL = os.path.expanduser("~/storage/downloads")
DST = os.path.join(DL, "ChillWorld-debug.apk")

print("Sumber: %s" % (("%.1f MB" % (os.path.getsize(SRC) / 1048576)) if os.path.exists(SRC) else "TIDAK ADA"))
if os.path.exists(SRC) and os.path.isdir(DL):
    if not os.path.exists(DST) or os.path.getsize(DST) != os.path.getsize(SRC):
        shutil.copy2(SRC, DST)
        print("Disalin.")
    print("Tujuan: %s (%.1f MB)" % (DST, os.path.getsize(DST) / 1048576))
    print("Ukuran sama: %s" % (os.path.getsize(DST) == os.path.getsize(SRC)))
else:
    print("Folder download ada: %s" % os.path.isdir(DL))
