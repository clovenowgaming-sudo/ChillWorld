# Jalankan download_textures_4k.py di latar belakang (detached); log ke scripts/tex4k.log
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
open(os.path.join(HERE, "texture_report_4k.txt"), "w").close()
log = open(os.path.join(HERE, "tex4k.log"), "w")
env = dict(os.environ)
env["PYTHONUNBUFFERED"] = "1"
subprocess.Popen([sys.executable, os.path.join(HERE, "download_textures_4k.py")],
                 stdout=log, stderr=subprocess.STDOUT, env=env, start_new_session=True)
print("Download 4K dimulai di latar belakang. Laporan: scripts/texture_report_4k.txt")
