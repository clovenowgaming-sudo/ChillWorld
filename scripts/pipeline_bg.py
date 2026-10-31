# Pipeline latar belakang: tunggu download texture selesai -> check -> commit -> push. Log: scripts/pipeline.log
import os
import subprocess
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..")
LOG = os.path.join(HERE, "pipeline.log")


def say(msg):
    with open(LOG, "a", encoding="utf-8") as f:
        f.write(msg + "\n")


def run(cmd, timeout=900):
    r = subprocess.run(cmd, cwd=ROOT, capture_output=True, text=True, timeout=timeout)
    say("$ " + " ".join(cmd) + "\n" + (r.stdout + r.stderr).strip() + "\nexit=%d" % r.returncode)
    return r


def downloader_alive():
    r = subprocess.run(["ps", "-eo", "args"], capture_output=True, text=True)
    return any("download_textures" in l for l in r.stdout.splitlines())


if "--child" not in sys.argv:
    open(LOG, "w").close()
    subprocess.Popen([sys.executable, os.path.abspath(__file__), "--child"],
                     stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, start_new_session=True)
    print("Pipeline dimulai di latar belakang. Log: scripts/pipeline.log")
    sys.exit(0)

say("menunggu download texture...")
for _ in range(600):
    if not downloader_alive():
        break
    time.sleep(5)
say("download selesai")

chk = run([sys.executable, os.path.join(HERE, "check_project.py")])
if "0 error" not in chk.stdout:
    say("check_project TIDAK bersih, push dibatalkan")
    sys.exit(1)

if run(["git", "add", "app", "CREDITS.md", "scripts/download_textures.py", "scripts/texture_report.txt", "scripts/texture_used.txt"]).returncode == 0:
    if run(["git", "commit", "-m", "Upgrade visual: preset Ultra, texture 2K, shadow Poisson, pantulan langit, anti-tiling"]).returncode == 0:
        run(["git", "push", "origin", "master"], timeout=900)
say("PIPELINE SELESAI")
