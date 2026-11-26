# CPK Editor (Android)

Editor arsip **CRI CPK** untuk Android — versi mobile dari alur kerja "buka CPK, tambah/ganti file, simpan CPK baru"
yang biasa dipakai modder game (mis. Naruto Storm 4).

> **Catatan soal [ns4moddingapi](https://github.com/zealottormunds/ns4moddingapi):** repo itu adalah *Modding API* untuk game
> (proxy DLL yang **memuat** CPK custom ke game), bukan editor CPK, dan tidak berisi kode baca/tulis CPK.
> Jadi parser dan penulis CPK di sini ditulis dari nol berdasarkan format CRI CPK. CPK yang dihasilkan aplikasi ini
> bisa dipakai sebagai CPK mod yang dimuat oleh API tersebut (belum diuji di game, lihat "Batasan").

## Fitur

- Buka file `.cpk` (juga lewat "Buka dengan" dari file manager) dan jelajahi isinya, lengkap dengan pencarian.
- Ekstrak satu file, beberapa file, atau semuanya (struktur folder dipertahankan). File terkompresi CRILAYLA otomatis didekompresi.
- Tambah file atau seluruh folder ke dalam CPK, pilih folder tujuan di dalam CPK. Path yang sudah ada akan **diganti**.
- Hapus file dari CPK.
- Buat CPK baru dari nol.
- Simpan sebagai CPK baru. File asli tidak pernah diubah; data file ditulis secara streaming (aman untuk file besar).
- UI Indonesia + Inggris, tema terang/gelap, tanpa izin storage (memakai Storage Access Framework).

## Membangun APK

**Tanpa Android Studio:** push ke GitHub, buka tab *Actions* → *Build APK* → unduh artifact `cpk-editor-debug-apk`.

**Dengan Android Studio:** *File → Open* folder ini, lalu *Run*. Atau lewat terminal (butuh Gradle 8.9+ dan Android SDK):

```
gradle :app:assembleDebug
```

Menjalankan tes parser/writer CPK (JVM murni, tanpa emulator):

```
gradle :app:testDebugUnitTest
```

## Struktur

| Path | Isi |
|---|---|
| `app/src/main/kotlin/.../cpk/` | Inti CPK tanpa dependensi Android: `CpkArchive` (baca), `CpkWriter` (tulis), `Utf` (tabel @UTF), `Layla` (dekompresi), `Editing` (tambah/ganti/hapus) |
| `app/src/main/kotlin/.../data/SafStorage.kt` | Akses file Android lewat SAF |
| `app/src/main/kotlin/.../ui/` | `CpkViewModel` + UI Jetpack Compose |
| `app/src/test/` | Tes JUnit untuk inti CPK |

## Cara kerja penyimpanan

**Mode patch (otomatis bila kamu hanya mengganti file).** CPK asli disalin byte demi byte: header, tabel ETOC/ITOC, urutan
dan perataan file tidak berubah. Yang berubah hanya baris TOC file yang diganti. Data baru menimpa slot lama bila muat
(ukuran file CPK tidak berubah), kalau tidak ditaruh di akhir file. Data yang dipakai bersama beberapa entri tidak pernah
ditimpa. Ini yang paling aman untuk game yang rewel terhadap struktur CPK.

**Mode tulis ulang (bila ada file ditambah/dihapus).** CPK ditulis ulang utuh ke file baru: area header 0x800 byte, tabel `TOC` di 0x800, lalu setiap file diratakan ke
kelipatan 0x800. Byte file lama disalin apa adanya (file terkompresi tetap terkompresi); file yang ditambahkan disimpan
tanpa kompresi. Nama file di dalam CPK tidak peka huruf besar/kecil saat menimpa.

## Batasan (jujur)

- **Belum diuji dengan CPK asli maupun di dalam game.** Inti CPK diuji dengan tes round-trip (tulis → baca) dan
  dekompresi CRILAYLA dengan data buatan, bukan dengan file CPK dari game. Coba dulu dengan salinan file.
- CPK berbasis ID tanpa nama file (tabel `ITOC`) belum didukung; aplikasi menampilkan pesan jelas.
- Tabel `ETOC`/`ITOC`/`GTOC`/`HTOC` tidak ikut ditulis ulang, dan flag `Sorted` dimatikan bila ada file baru ditambahkan
  (urutan file asli dipertahankan, file baru ditaruh di akhir).
- Satu entri maksimal 4 GiB (batas format). Entri terkompresi dibaca penuh ke memori saat diekstrak.
- Nama dalam CPK dibaca sebagai UTF-8.
- Untuk "menimpa" CPK asli, simpan dengan nama lain lalu ganti manual; aplikasi menolak menulis ke file yang sedang dibuka.
