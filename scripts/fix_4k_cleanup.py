# Tambahkan pembersihan file non-4K di download_textures_4k.py
import os
import sys

p = os.path.join(os.path.dirname(os.path.abspath(__file__)), "download_textures_4k.py")
t = open(p, encoding="utf-8").read()
old = '                dt.log("  [tolak] %s: ukuran %s bukan 4096x4096, tidak dipakai" % (asset_id, s))\n'
new = old + (
    '                for suffix in dt.WANTED.values():\n'
    '                    try:\n'
    '                        os.remove(os.path.join(dt.OUT_DIR, name + suffix))\n'
    '                    except OSError:\n'
    '                        pass\n')
if t.count(old) != 1:
    print("GAGAL")
    sys.exit(1)
open(p, "w", encoding="utf-8").write(t.replace(old, new))
print("OK")
