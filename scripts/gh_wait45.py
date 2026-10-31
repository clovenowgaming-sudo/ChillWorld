# Tunggu 45 detik lalu cek status (aman dari timeout)
import os
import subprocess
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
time.sleep(45)
subprocess.run([sys.executable, os.path.join(HERE, "gh_latest.py")])
