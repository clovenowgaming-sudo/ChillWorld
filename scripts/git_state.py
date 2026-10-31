# Periksa kondisi branch/commit setelah perubahan di luar sesi (cuma baca)
import os
import subprocess

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")


def run(cmd, timeout=120):
    r = subprocess.run(cmd, cwd=ROOT, capture_output=True, text=True, timeout=timeout)
    print("$ " + " ".join(cmd))
    print((r.stdout + r.stderr).strip() or "(kosong)")
    print()


run(["git", "branch", "-a", "-vv"])
run(["git", "log", "--oneline", "--decorate", "-8"])
run(["git", "branch", "-a", "--contains", "6ded6fa"])
run(["git", "remote", "-v"])
run(["gh", "run", "list", "--limit", "5", "--json", "status,conclusion,headBranch,headSha,displayTitle",
     "--jq", '.[] | "\\(.status) \\(.conclusion) \\(.headBranch) \\(.headSha[0:7]) \\(.displayTitle)"'])
