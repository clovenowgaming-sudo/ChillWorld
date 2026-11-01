# Pasang glslang (glslangValidator) di Termux di latar belakang; log ke scripts/pkg_glslang.log
import os
import subprocess

HERE = os.path.dirname(os.path.abspath(__file__))
log = open(os.path.join(HERE, "pkg_glslang.log"), "w")
env = dict(os.environ)
env["DEBIAN_FRONTEND"] = "noninteractive"
subprocess.Popen(
    ["sh", "-c", "which glslangValidator || (pkg install -y glslang 2>&1; which glslangValidator; echo DONE)"],
    stdout=log, stderr=subprocess.STDOUT, env=env, start_new_session=True)
print("Instalasi glslang dimulai di latar belakang.")
