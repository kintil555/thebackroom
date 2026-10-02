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
- Middle click blok berlapis meng-clone item blok itu beserta lapisannya, bernama "Screw Piles Attached Wallpaper", "<Blok> Attached Carpet", dst. Dipasang kembali, blok + lapisannya muncul bersamaan
- Dihapus: HUD nama blok di bawah crosshair
- Fix: Carpet di blok selain Screw Piles tidak tergambar. Fabric mengalihkan render terrain dari ModelBlockRenderer, jadi mixin lama tidak pernah jalan; kini memakai pembungkus model Fabric (emitQuads)
- Fix culling: lapisan punya sisi belakang. Dilihat dari belakang (mis. lewat lubang Screw Piles): Wallpaper menampilkan oak planks, Carpet menampilkan carpet
- Blok baru: Ceiling. Item baru: Wallpapers (creative only, Screw Piles dengan 6 sisi berwallpaper)
- Texture baru untuk Carpet dan Ceiling
- F3 (Targeted Block), Jade, dan WTHIT mendeteksi Wallpaper/Carpet saat crosshair membidik sisi berlapis, bukan blok pemiliknya. Middle click tetap meng-clone blok + lapisannya
- Item hasil pick (middle click) blok berlapis: ikon inventaris, hotbar, dan tangan kini ikut menampilkan tekstur Wallpaper/Carpet di sisi yang berlapis. Screw Piles lewat item model JSON (composite + select block_state), blok lain lewat mixin ItemModelResolver
