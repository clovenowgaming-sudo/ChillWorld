# Cari pemakaian Quality / texSize / maxAniso / pickPreset di file Kotlin (cuma baca)
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "app", "src", "main", "java", "com", "vinz", "chillworld")
pat = re.compile(r"Quality|texSize|maxAniso|autoPreset|GL_RENDERER|renderer\b|shadowRes|TextureLoader\.load")
for name in sorted(os.listdir(SRC)):
    if not name.endswith(".kt"):
        continue
    with open(os.path.join(SRC, name), "r", encoding="utf-8") as f:
        for i, line in enumerate(f, 1):
            if pat.search(line):
                print("%s:%d: %s" % (name, i, line.rstrip()[:160]))
