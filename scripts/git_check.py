# Cek kondisi git dan gh (cuma baca, tidak mengubah apa pun)
import os
import subprocess

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")


def run(cmd):
    print("$ " + " ".join(cmd))
    try:
        r = subprocess.run(cmd, cwd=ROOT, capture_output=True, text=True, timeout=30)
        out = (r.stdout + r.stderr).strip()
        print(out if out else "(kosong)")
    except FileNotFoundError:
        print("(perintah tidak ditemukan)")
    except Exception as e:
        print("(error: %s)" % e)
    print()


run(["git", "--version"])
run(["gh", "--version"])
run(["gh", "auth", "status"])
run(["git", "remote", "-v"])
run(["git", "branch", "--show-current"])
run(["git", "status", "--short"])
run(["git", "log", "--oneline", "-5"])
