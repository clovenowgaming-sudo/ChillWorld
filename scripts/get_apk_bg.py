# Jalankan get_apk.py di latar belakang (detached), log ke scripts/get_apk.log
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
log = open(os.path.join(HERE, "get_apk.log"), "w")
subprocess.Popen([sys.executable, os.path.join(HERE, "get_apk.py")],
                 stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
print("Download dimulai di latar belakang. Cek scripts/get_apk.log")
