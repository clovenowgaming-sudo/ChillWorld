# Cetak pemanggilan GLES lengkap (termasuk yang multi-baris) untuk fungsi yang sensitif terhadap tipe argumen
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
KT = os.path.join(HERE, "..", "app", "src", "main", "java", "com", "vinz", "chillworld")
FUNCS = ["glBufferData", "glBufferSubData", "glTexSubImage3D", "glVertexAttribPointer", "glDrawElementsInstanced",
         "glUniformMatrix4fv", "glUniform4fv", "glGetIntegerv", "glShaderSource", "glGetFloatv", "glDrawBuffers",
         "glDrawElements", "glGetShaderiv", "glGetProgramiv", "glTexStorage3D"]
seen = {}
for name in sorted(os.listdir(KT)):
    if not name.endswith(".kt") or name in ("MainActivity.kt", "Hud.kt", "Input.kt"):
        continue
    lines = open(os.path.join(KT, name), encoding="utf-8").read().split("\n")
    i = 0
    while i < len(lines):
        m = re.search(r"GLES\d+\.(%s)\(" % "|".join(FUNCS), lines[i])
        if m:
            stmt = lines[i].strip()
            depth = stmt.count("(") - stmt.count(")")
            j = i
            while depth > 0 and j + 1 < len(lines):
                j += 1
                stmt += " " + lines[j].strip()
                depth += lines[j].count("(") - lines[j].count(")")
            key = (m.group(1), len(stmt) // 40)
            if (m.group(1), re.sub(r"\w+", "x", stmt)) not in seen:
                seen[(m.group(1), re.sub(r"\w+", "x", stmt))] = "%s:%d: %s" % (name, i + 1, stmt[:230])
            i = j
        i += 1
for v in seen.values():
    print(v)
