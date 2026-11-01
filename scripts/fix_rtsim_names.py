# Ganti nama fungsi RtSim supaya tidak bentrok dengan setter JVM properti (platform declaration clash)
import os

p = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "java", "com", "vinz", "chillworld", "RtSim.kt")
t = open(p, encoding="utf-8").read()
for old, new in [("fun setEnabled(on: Boolean)", "fun enable(on: Boolean)"),
                 ("fun setLevel(l: Int)", "fun changeLevel(l: Int)"),
                 ("fun cycleLevel() = setLevel(", "fun cycleLevel() = changeLevel("),
                 ("fun autoFor(q: Quality) = setEnabled(", "fun autoFor(q: Quality) = enable(")]:
    assert t.count(old) == 1, old
    t = t.replace(old, new)
open(p, "w", encoding="utf-8").write(t)
print("OK")
