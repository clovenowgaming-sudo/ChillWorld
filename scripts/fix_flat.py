# Ganti nama variabel `flat` (kata kunci GLSL) di rtsim_c.frag
import os

p = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "assets", "shaders", "rtsim_c.frag")
t = open(p, encoding="utf-8").read()
assert t.count("float flat = ") == 1 and t.count("* flat *") == 1
t = t.replace("float flat = ", "float flatness = ").replace("* flat *", "* flatness *")
open(p, "w", encoding="utf-8").write(t)
print("OK")
