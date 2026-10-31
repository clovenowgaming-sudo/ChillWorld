# Commit + push (tanpa force) ke branch aktif. Pesan dari scripts/_commit_msg.txt.
# Yang di-stage: desktop, app, .github, file gradle root, scripts/*.py (bukan log). Token tidak pernah disentuh.
import glob
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..")


def run(cmd, timeout=600):
    r = subprocess.run(cmd, cwd=ROOT, capture_output=True, text=True, timeout=timeout)
    print("$ " + " ".join(cmd[:4]) + (" ..." if len(cmd) > 4 else ""))
    print((r.stdout + r.stderr).strip()[-1500:] or "(kosong)")
    print("exit=%d\n" % r.returncode)
    return r


msg = open(os.path.join(HERE, "_commit_msg.txt"), encoding="utf-8").read().strip()
chk = run([sys.executable, os.path.join(HERE, "check_project.py")])
if "0 error" not in chk.stdout:
    print("check_project tidak bersih, dibatalkan")
    sys.exit(1)

paths = ["desktop", "app", ".github", "settings.gradle.kts", "build.gradle.kts", "CREDITS.md", "README.md"]
paths += ["scripts/" + os.path.basename(p) for p in glob.glob(os.path.join(HERE, "*.py"))]
paths = [p for p in paths if os.path.exists(os.path.join(ROOT, p))]
run(["git", "add"] + paths)
if run(["git", "diff", "--cached", "--quiet"]).returncode == 0:
    print("Tidak ada perubahan untuk di-commit.")
    sys.exit(0)
branch = run(["git", "branch", "--show-current"]).stdout.strip()
if run(["git", "commit", "-m", msg]).returncode == 0:
    run(["git", "push", "origin", branch])
    print(run(["git", "log", "--oneline", "-1"]).stdout)
