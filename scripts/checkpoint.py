# Checkpoint Git (tag) sebelum port Windows + status build Android terbaru
import os
import subprocess

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")


def run(cmd, timeout=120):
    r = subprocess.run(cmd, cwd=ROOT, capture_output=True, text=True, timeout=timeout)
    print("$ " + " ".join(cmd))
    print((r.stdout + r.stderr).strip() or "(kosong)")
    print()
    return r


run(["git", "status", "--short"])
run(["git", "branch", "--show-current"])
run(["git", "tag", "-f", "checkpoint-android-v2-before-pc-port"])  # tag lokal baru, bukan force push
run(["git", "push", "origin", "checkpoint-android-v2-before-pc-port"])
run(["gh", "run", "list", "--limit", "1", "--json", "status,conclusion,headSha,displayTitle",
     "--jq", '.[0] | "\\(.status) \\(.conclusion) \\(.headSha[0:7]) \\(.displayTitle)"'])
