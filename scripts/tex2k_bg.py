# Jalankan download_textures.py (paksa, 2K) di latar belakang; log ke scripts/tex2k.log
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
env = dict(os.environ)
env["CW_FORCE"] = "1"
env["PYTHONUNBUFFERED"] = "1"
with open(os.path.join(HERE, "texture_report.txt"), "w") as f:
    f.write("== Download ulang 2K ==\n")
log = open(os.path.join(HERE, "tex2k.log"), "w")
subprocess.Popen([sys.executable, os.path.join(HERE, "download_textures.py")],
                 stdout=log, stderr=subprocess.STDOUT, env=env, start_new_session=True)
print("Download 2K dimulai di latar belakang.")
