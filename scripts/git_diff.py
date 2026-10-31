# Tampilkan diff build.yml terhadap commit terakhir (cuma baca)
import os
import subprocess

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
r = subprocess.run(["git", "diff", "HEAD", "--", ".github/workflows/build.yml"],
                   cwd=ROOT, capture_output=True, text=True, timeout=30)
print((r.stdout + r.stderr).strip() or "(tidak ada perbedaan)")
