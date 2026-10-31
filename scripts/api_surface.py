# Ekstrak permukaan API Android yang dipakai kode bersama (cuma baca)
import collections
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
KT = os.path.join(HERE, "..", "app", "src", "main", "java", "com", "vinz", "chillworld")
SKIP = {"MainActivity.kt", "Hud.kt", "Input.kt"}  # murni UI Android, diganti versi desktop

calls = collections.OrderedDict()
consts = set()
others = collections.OrderedDict()
for name in sorted(os.listdir(KT)):
    if not name.endswith(".kt") or name in SKIP:
        continue
    for i, line in enumerate(open(os.path.join(KT, name), encoding="utf-8"), 1):
        s = line.strip()
        if s.startswith("import") or s.startswith("//") or s.startswith("*") or s.startswith("/*"):
            continue
        for m in re.finditer(r"\b(GLES\d+)\.([A-Za-z_0-9]+)", s):
            api, member = m.group(1), m.group(2)
            if member.startswith("GL_"):
                consts.add(member)
            else:
                key = api + "." + member
                if key not in calls:
                    calls[key] = [0, "%s:%d: %s" % (name, i, s[:150])]
                calls[key][0] += 1
        for m in re.finditer(r"\b(Matrix|Log|Color|BitmapFactory|Bitmap|Canvas|Paint|ActivityManager|Context)\b\.?(\w*)", s):
            if m.group(1) in ("Matrix", "Log", "BitmapFactory", "Color") or "Context" == m.group(1) or "Bitmap" == m.group(1):
                key = m.group(1) + "." + m.group(2)
                if key not in others:
                    others[key] = "%s:%d: %s" % (name, i, s[:150])

print("== GLES fungsi (%d unik) ==" % len(calls))
for k, (n, ex) in calls.items():
    print("%-34s x%-3d %s" % (k, n, ex))
print("\n== GLES konstanta (%d) ==" % len(consts))
print(" ".join(sorted(consts)))
print("\n== API Android lain ==")
for k, ex in others.items():
    print("%-30s %s" % (k, ex))
