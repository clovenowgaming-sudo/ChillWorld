# Tunggu run selesai (maks ~4 menit), lalu jalankan gh_latest.py
import os
import subprocess
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..")

for _ in range(16):
    r = subprocess.run(["gh", "run", "list", "--limit", "1", "--json", "status", "--jq", ".[0].status"],
                       cwd=ROOT, capture_output=True, text=True, timeout=60)
    if r.stdout.strip() == "completed":
        break
    time.sleep(15)

subprocess.run([sys.executable, os.path.join(HERE, "gh_latest.py")])
