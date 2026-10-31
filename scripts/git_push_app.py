# Commit semua perubahan di app/ lalu push ke origin
import os
import subprocess

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")


def run(cmd):
    print("$ " + " ".join(cmd))
    r = subprocess.run(cmd, cwd=ROOT, capture_output=True, text=True, timeout=120)
    out = (r.stdout + r.stderr).strip()
    print(out if out else "(kosong)")
    print("exit code: %d\n" % r.returncode)
    return r.returncode


if run(["git", "add", "app"]) == 0:
    if run(["git", "commit", "-m", "Fix platform declaration clash: setQuality -> applyQuality"]) == 0:
        run(["git", "push", "origin", "master"])
