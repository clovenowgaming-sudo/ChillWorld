# Chill World

Game 3D santai (chill): dunia terbuka tanpa batas berisi pohon, tanah, rumah, gunung, dan rumput.
Tanpa musuh, tanpa skor. Tinggal jalan-jalan sambil lihat siang berganti malam.

- Package: `com.vinz.chillworld`
- Platform: Android (minSdk 26, targetSdk 34), OpenGL ES 3.0 / 3.1 / 3.2
- Bahasa: Kotlin + GLSL ES

> Catatan jujur: "mirip DLSS" di sini artinya TAMPILANNYA mirip (tajam dan halus walau resolusi
> render lebih rendah). DLSS asli dan ray tracing tidak ada di OpenGL ES. Yang dipakai:
> render scale dinamis + TAA + upscaling + sharpening ala CAS.

## 1. Download texture (CC0 dari ambientCG)

Texture asli sudah diunduh kalau kamu menjalankan skrip ini. Kalau kosong atau gagal
(misalnya tidak ada internet), jalankan ulang manual:

```
python scripts/download_textures.py
```

Skrip ini hanya butuh Python standar. Hasilnya dicatat di `scripts/texture_report.txt` dan
sumbernya di `CREDITS.md`. Kalau ada material yang gagal diunduh, game tetap jalan: otomatis
dibuatkan texture prosedural sebagai pengganti (tidak akan crash).

Kalau terputus di tengah jalan, jalankan lagi saja: material yang sudah lengkap dilewati.

## 2. Push ke GitHub

```
cd ChillWorld
git init
git add .
git commit -m "Chill World"
git branch -M main
git remote add origin https://github.com/<username>/<repo>.git
git push -u origin main
```

Kalau pakai GitHub CLI: `gh repo create chillworld --public --source=. --push`.

## 3. Ambil APK dari tab Actions

1. Buka repo di GitHub, masuk tab **Actions**.
2. Pilih workflow **Build APK** (jalan otomatis tiap push, atau klik **Run workflow**).
3. Tunggu sampai hijau, lalu buka run-nya.
4. Di bagian **Artifacts**, unduh `ChillWorld-debug-apk`, ekstrak zip-nya, dan install `app-debug.apk`.

Workflow membuat Gradle wrapper sendiri kalau `gradlew` belum ada, jadi tidak perlu edit manual.

## 4. Preset grafik

| Preset  | Render scale | Cascade shadow | TAA | Godrays | Radius chunk | Rumput | Lain-lain |
|---------|--------------|----------------|-----|---------|--------------|--------|-----------|
| Low     | 0.50         | 1 x 1024 px    | tidak | tidak | 4            | 25%    | texture 512 px, precision mediump untuk warna |
| Medium  | 0.67         | 2 x 1536 px    | ya  | 8 sampel  | 6          | 50%    | bloom |
| High    | 0.80         | 3 x 2048 px    | ya  | 12 sampel | 8          | 75%    | bloom |
| Extreme | 1.00         | 4 x 2048 px    | ya + CAS | 16 sampel | 10    | 100%   | depth pre-pass, bloom |

Preset awal dipilih otomatis dari jenis GPU, versi GLES, dan RAM. Bisa diganti kapan saja dari menu.

Dynamic Resolution Scaling: rata-rata 30 frame terakhir di atas 33 ms, render scale turun 5%
(minimum 0.5). Di bawah 25 ms, naik 5% (maksimum sesuai preset).

Kalau FPS di bawah 20 selama beberapa detik di preset Low, muncul saran untuk menurunkan radius chunk
(tombol **Radius -** di menu).

## 5. Fitur grafis

- Cascaded Shadow Maps (PCF 3x3, bias berbasis kemiringan, blending antar cascade, bayangan daun berlubang)
- Godrays screen-space (kuat saat menghadap matahari atau cahaya menembus dedaunan)
- PBR sederhana: albedo + normal map + roughness, ambient hemisphere dari langit, Fresnel, GGX
- Langit prosedural (Rayleigh + Mie), matahari, bulan, bintang, awan, siklus siang-malam 10 menit
- Kabut tinggi + jarak, angin pada daun/rumput/ranting
- Tone mapping ACES, gamma 2.2, bloom ringan, vignette
- Dunia tak terbatas: chunk 64x64 m dibuat di thread latar, 3 level LOD, billboard untuk pohon jauh
- GLES 3.1+ : culling pohon dan rumput pakai compute shader (SSBO + `glDrawElementsIndirect`).
  GLES 3.0 : culling di CPU. Hasil tampilannya sama.

## 6. Kontrol game

- **Setengah layar kiri**: sentuh dan geser = joystick virtual (jalan).
- **Setengah layar kanan**: geser = lihat sekeliling.
- **Terbang**: tombol di kanan atas menyalakan mode terbang.
- **Naik / Turun**: tombol di kanan bawah (otomatis menyalakan mode terbang).
- **Menu**: ganti preset grafik, radius chunk, tampilkan/sembunyikan FPS, ganti jam, hentikan/jalankan waktu.

## 7. Struktur proyek

```
ChillWorld/
  app/src/main/java/com/vinz/chillworld/   kode Kotlin
  app/src/main/assets/shaders/             shader GLSL ES (.vert .frag .comp .glsl)
  app/src/main/assets/textures/            texture CC0 ambientCG
  scripts/download_textures.py             unduh texture
  scripts/check_project.py                 cek kelengkapan proyek
  scripts/pack_zip.py                      bikin ChillWorld.zip
  .github/workflows/build.yml              build APK di GitHub Actions
```

## 8. Batasan yang perlu diketahui

- Proyek ini ditulis dan dicek lewat skrip statis di Termux. Build Gradle dan uji jalan di HP
  BELUM dijalankan di sini, jadi kalau build di Actions gagal, kirim log errornya untuk diperbaiki.
- Target FPS (Low 30 FPS di Helio P35, dst.) belum diukur di perangkat sungguhan.
- Texture dan lisensi: lihat `CREDITS.md`.
