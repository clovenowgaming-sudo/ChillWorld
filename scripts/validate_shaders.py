# Validasi semua shader dengan glslangValidator: ES 3.00/3.10 (Android) dan terjemahan desktop 430 core (PC)
# Meniru ShaderLoader: #include rekursif + define varian disisipkan setelah baris #version.
import os
import re
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
SH = os.path.join(HERE, "..", "app", "src", "main", "assets", "shaders")
VAL = "glslangValidator"


def resolve(name, depth=0):
    raw = open(os.path.join(SH, name), encoding="utf-8").read()
    if depth > 4:
        return raw
    out = []
    for line in raw.split("\n"):
        t = line.strip()
        if t.startswith("#include"):
            inc = t.split('"')[1]
            out.append(resolve(inc, depth + 1))
        else:
            out.append(line)
    return "\n".join(out)


def with_defs(src, defs):
    nl = src.index("\n")
    return src[:nl + 1] + "".join("#define %s 1\n" % d for d in defs) + src[nl + 1:]


def stage_of(name):
    return {"vert": "vert", "frag": "frag", "comp": "comp"}.get(name.rsplit(".", 1)[-1])


def used_defines(src):
    found = set(re.findall(r"#\s*(?:ifdef|ifndef)\s+(\w+)", src))
    found |= set(re.findall(r"defined\s*\(?\s*(\w+)\s*\)?", src))
    found -= {"GL_ES"}
    return sorted(found)


fails = 0
runs = 0
tmpdir = tempfile.mkdtemp(prefix="cw_glsl_")
only = sys.argv[1:]  # opsional: nama file tertentu
for name in sorted(os.listdir(SH)):
    st = stage_of(name)
    if st is None or (only and name not in only):
        continue
    src = resolve(name)
    variants = [[]] + [[d] for d in used_defines(src)]
    for defs in variants:
        text = with_defs(src, defs)
        for profile in ("es", "desktop"):
            t = text
            if profile == "desktop":
                t = re.sub(r"#version\s+3\d0\s+es", "#version 430 core", t)
            path = os.path.join(tmpdir, "t." + st)
            open(path, "w", encoding="utf-8").write(t)
            r = subprocess.run([VAL, "-S", st, path], capture_output=True, text=True, timeout=60)
            runs += 1
            if r.returncode != 0:
                fails += 1
                msg = [l for l in (r.stdout + r.stderr).splitlines() if "ERROR" in l or "error" in l.lower()]
                print("GAGAL %-18s %-8s defs=%s" % (name, profile, defs))
                for l in msg[:4]:
                    print("    " + l.replace(tmpdir + "/", "")[:200])
print("Selesai: %d kompilasi, %d gagal" % (runs, fails))
