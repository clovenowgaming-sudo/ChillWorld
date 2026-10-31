# Ambil log step yang gagal dari run gagal terbaru (cuma baca)
import os
import subprocess

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")


def gh(args, timeout=120):
    r = subprocess.run(["gh"] + args, cwd=ROOT, capture_output=True, text=True, timeout=timeout)
    return (r.stdout + r.stderr)


ids = gh(["run", "list", "--limit", "6", "--json", "databaseId,conclusion",
          "--jq", '.[] | select(.conclusion=="failure") | .databaseId']).split()
if not ids:
    print("Tidak ada run gagal.")
else:
    rid = ids[0]
    print("Run gagal terbaru: " + rid)
    log = gh(["run", "view", rid, "--log-failed"])
    lines = log.splitlines()
    print("\n".join(lines[-80:]))
