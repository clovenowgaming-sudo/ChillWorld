# Cari semua field BuildConfig yang dipakai kode bersama (cuma baca)
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
KT = os.path.join(HERE, "..", "app", "src", "main", "java", "com", "vinz", "chillworld")
found = set()
for name in sorted(os.listdir(KT)):
    if name.endswith(".kt"):
        for m in re.finditer(r"BuildConfig\.(\w+)", open(os.path.join(KT, name), encoding="utf-8").read()):
            found.add((m.group(1), name))
print(sorted(found))
