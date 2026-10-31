# Hapus deteksi GPU untuk pilih preset; semua HP bebas pilih preset manual
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
KT = os.path.join(HERE, "..", "app", "src", "main", "java", "com", "vinz", "chillworld")


def patch(name, pairs):
    path = os.path.join(KT, name)
    with open(path, "r", encoding="utf-8") as f:
        text = f.read()
    for old, new in pairs:
        if text.count(old) != 1:
            print("GAGAL %s: %d kecocokan untuk %r" % (name, text.count(old), old[:60]))
            sys.exit(1)
        text = text.replace(old, new)
    return path, text


def save(path, text):
    with open(path, "w", encoding="utf-8") as f:
        f.write(text)
    print("OK  " + os.path.basename(path))


# Renderer: tanpa pilih preset dari GPU + peringatan FPS untuk semua preset
p, t = patch("Renderer.kt", [
    ("        if (!Settings.autoPresetDone) {\n"
     "            Settings.quality = caps.suggestedQuality()\n"
     "            Settings.autoPresetDone = true\n"
     "            Settings.presetVersion++\n"
     "            Log.i(Gl.TAG, \"Preset awal otomatis: \" + Settings.quality.label)\n"
     "        }\n",
     "        if (!Settings.autoPresetDone) {\n"
     "            // tanpa deteksi GPU: semua HP mulai dari preset bawaan, ganti manual lewat menu\n"
     "            Settings.autoPresetDone = true\n"
     "            Settings.presetVersion++\n"
     "            Log.i(Gl.TAG, \"Preset awal: \" + Settings.quality.label + \" (manual, tanpa deteksi GPU)\")\n"
     "        }\n"),
    ("            if (q == Quality.LOW && fps < 20f) lowFpsTime += 0.5f else lowFpsTime = 0f\n"
     "            if (lowFpsTime >= 5f && !hinted) {\n"
     "                hinted = true\n"
     "                ui.onHint(\"FPS masih di bawah 20 pada Low. Saran: turunkan radius chunk lewat menu (tombol gear).\")\n"
     "            }\n",
     "            if (fps < 20f) lowFpsTime += 0.5f else lowFpsTime = 0f\n"
     "            if (lowFpsTime >= 5f && !hinted) {\n"
     "                hinted = true\n"
     "                if (q == Quality.LOW) {\n"
     "                    ui.onHint(\"FPS masih di bawah 20 pada Low. Saran: turunkan radius chunk lewat Menu.\")\n"
     "                } else {\n"
     "                    ui.onHint(\"FPS di bawah 20 pada preset \" + q.label + \". Coba turunkan preset lewat Menu.\")\n"
     "                }\n"
     "            }\n"),
])
save(p, t)

# GLCaps: hapus fungsi suggestedQuality (deteksi GPU -> preset)
path = os.path.join(KT, "GLCaps.kt")
with open(path, "r", encoding="utf-8") as f:
    text = f.read()
text2, n = re.subn(r"\n    /\*\* Pilih preset awal.*?        return q\n    }\n", "\n", text, flags=re.S)
if n != 1:
    print("GAGAL GLCaps: suggestedQuality tidak ketemu (%d)" % n)
    sys.exit(1)
save(path, text2)

# Settings: komentar Ultra
p, t = patch("Settings.kt", [
    ("    // khusus GPU kelas atas (Adreno 8xx / Red Magic 11 Pro): bayangan 4096, texture 2K\n",
     "    // paling berat (bayangan 4096, texture 2K): bebas dicoba di HP apa pun, HP lama bisa lag\n"),
])
save(p, t)
print("Selesai.")
