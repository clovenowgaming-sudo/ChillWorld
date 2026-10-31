# Cek progres unduhan 2K: ukuran file zip sementara + proses python yang jalan
import glob
import os
import subprocess
import tempfile

tmp = tempfile.gettempdir()
print("tmp: " + tmp)
for d in glob.glob(os.path.join(tmp, "chillworld_tex_*")):
    for fn in os.listdir(d):
        p = os.path.join(d, fn)
        print("%s  %.1f MB" % (p, os.path.getsize(p) / 1048576))
r = subprocess.run(["ps", "-eo", "pid,etime,args"], capture_output=True, text=True)
for line in r.stdout.splitlines():
    if "download_textures" in line:
        print(line)
