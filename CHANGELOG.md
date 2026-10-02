# Changelog

## 1.0.0
- Blok baru: Screw Piles, Wallpaper, Carpet

## Unreleased
- Wallpaper dan Carpet jadi item. Keduanya menempel di face blok, bukan blok terpisah di luarnya
- Screw Piles: lapisan disimpan di blockstate (6 property per sisi)
- Carpet di blok lain: disimpan sebagai attachment chunk (tersimpan + tersinkron), digambar di mesh chunk lewat mixin
- Break hanya melepas lapisan; retak dan partikel hanya di sisi berlapis
- Dihapus: blok Wallpaper dan Carpet lama (panel multi-sisi)
