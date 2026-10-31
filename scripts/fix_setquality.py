# Ganti nama fungsi setQuality -> applyQuality di semua file Kotlin (hindari bentrok setter JVM)
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "app", "src", "main", "java", "com", "vinz", "chillworld")
pat = re.compile(r"\bsetQuality\(")

total = 0
for name in sorted(os.listdir(SRC)):
    if not name.endswith(".kt"):
        continue
    path = os.path.join(SRC, name)
    with open(path, "r", encoding="utf-8") as f:
        text = f.read()
    n = len(pat.findall(text))
    if n:
        with open(path, "w", encoding="utf-8") as f:
            f.write(pat.sub("applyQuality(", text))
        print("%s: %d penggantian" % (name, n))
        total += n
print("Total: %d" % total)
