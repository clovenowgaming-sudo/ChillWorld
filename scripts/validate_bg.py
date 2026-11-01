# Jalankan validate_shaders.py di latar belakang; hasil di scripts/validate.log
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
log = open(os.path.join(HERE, "validate.log"), "w")
subprocess.Popen([sys.executable, "-u", os.path.join(HERE, "validate_shaders.py")],
                 stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
print("Validasi shader dimulai. Hasil: scripts/validate.log")
