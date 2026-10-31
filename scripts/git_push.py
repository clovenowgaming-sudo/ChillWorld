# Commit build.yml lalu push ke origin
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


if run(["git", "add", ".github/workflows/build.yml"]) == 0:
    if run(["git", "commit", "-m", "Pakai SDK bawaan runner, platform 34 sesuai compileSdk"]) == 0:
        run(["git", "push", "origin", "master"])
        run(["git", "log", "--oneline", "-3"])
