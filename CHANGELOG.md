# Changelog

## 1.0.0
- Blok baru: Screw Piles, Wallpaper, Carpet

## Unreleased
- Wallpaper dan Carpet jadi item. Keduanya menempel di face blok, bukan blok terpisah di luarnya
- Screw Piles: lapisan disimpan di blockstate (6 property per sisi)
- Carpet di blok lain: disimpan sebagai attachment chunk (tersimpan + tersinkron), digambar di mesh chunk lewat mixin
- Break hanya melepas lapisan; retak dan partikel hanya di sisi berlapis
- Dihapus: blok Wallpaper dan Carpet lama (panel multi-sisi)
- Fix cahaya: Screw Piles kini menahan cahaya penuh (sebelumnya bocor menembus dinding karena noOcclusion), sisi antar Screw Piles tidak digambar
- Fix tampilan: model lapisan memakai ambient occlusion yang sama dengan blok pemiliknya dan offset lebih tebal (anti z-fighting)
- Nama blok berlapis ditampilkan di bawah crosshair: "Screw Piles Attached Wallpaper", "<Blok> Attached Carpet"
- Blok baru: Ceiling. Item baru: Wallpapers (creative only, Screw Piles dengan 6 sisi berwallpaper)
- Texture baru untuk Carpet dan Ceiling
