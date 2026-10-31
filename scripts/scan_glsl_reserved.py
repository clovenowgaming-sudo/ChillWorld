# Cari identifier yang terlarang di GLSL desktop 4.30 (aman di ES) pada shader (cuma baca)
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
SH = os.path.join(HERE, "..", "app", "src", "main", "assets", "shaders")
RESERVED = ["sample", "patch", "precise", "subroutine", "buffer", "shared", "coherent", "volatile", "restrict",
            "readonly", "writeonly", "resource", "common", "partition", "active", "filter", "input", "output",
            "asm", "class", "union", "enum", "typedef", "template", "this", "goto", "inline", "noinline",
            "public", "static", "extern", "external", "interface", "long", "short", "half", "fixed", "unsigned",
            "superp", "sizeof", "cast", "namespace", "using", "atomic_uint", "image2D", "image3D"]
pat = re.compile(r"\b(%s)\b" % "|".join(RESERVED))
found = 0
for name in sorted(os.listdir(SH)):
    text = open(os.path.join(SH, name), encoding="utf-8").read()
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    for i, line in enumerate(text.split("\n"), 1):
        code = line.split("//")[0]
        for m in pat.finditer(code):
            w = m.group(1)
            if name.endswith(".comp") and w in ("buffer", "shared", "readonly", "writeonly", "coherent", "restrict", "volatile"):
                continue
            print("%s:%d: [%s] %s" % (name, i, w, code.strip()[:110]))
            found += 1
print("Total temuan: %d" % found)
