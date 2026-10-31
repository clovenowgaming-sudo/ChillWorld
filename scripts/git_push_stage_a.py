# Tahap (a): check -> stage file relevan -> commit -> push branch aktif (tanpa force)
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..")


def run(cmd, timeout=300):
    r = subprocess.run(cmd, cwd=ROOT, capture_output=True, text=True, timeout=timeout)
    print("$ " + " ".join(cmd))
    print((r.stdout + r.stderr).strip() or "(kosong)")
    print("exit=%d\n" % r.returncode)
    return r


chk = run([sys.executable, os.path.join(HERE, "check_project.py")])
if "0 error" not in chk.stdout:
    print("check_project tidak bersih, dibatalkan")
    sys.exit(1)

branch = run(["git", "branch", "--show-current"]).stdout.strip()
run(["gh", "repo", "view", "--json", "defaultBranchRef", "--jq", ".defaultBranchRef.name"])
run(["git", "add", "desktop", "settings.gradle.kts", "build.gradle.kts", ".github/workflows/windows.yml",
     "scripts/inspect_project.py", "scripts/api_surface.py", "scripts/scan_glsl_reserved.py"])
run(["git", "status", "--short"])
if run(["git", "commit", "-m", "feat: windows pc port skeleton (LWJGL, OpenGL 4.3, shared render code)"]).returncode == 0:
    run(["git", "push", "origin", branch])
