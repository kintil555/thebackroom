# Changelog

## 1.0.0
- Blok baru: Screw Piles, Wallpaper, Carpet

## Unreleased
- Blok baru: Lamp (versi menyala/mati, light level 15 seperti Ochre Froglight). Random tick: peluang 67% berkedip mati-nyala beberapa kali lalu kembali normal
- Fix Lamp: tekstur 64x64 berformat cube net (top/bottom/east/north/west/south), model kini memakai UV per sisi, bukan cube_all
- Lamp: suara kedip (neon sputter, mono ogg + echo, 3 variasi pitch) di awal sesi kedip; maksimal 10 suara aktif dalam radius 16 blok
- Lamp: efek domino. Sesi kedip menyebar ke lampu menyala dalam radius 8 blok (peluang turun menurut jarak; sebagian bersamaan, sisanya tertunda seperti gelombang), cooldown 5 detik agar tidak berantai tanpa batas
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
- Fix: debu saat berlari di atas sisi atas berlapis melesat cepat ke atas. Kini memakai partikel BLOCK (model cover_display) agar kecepatannya dinormalkan seperti vanilla
- Portal: glow/bloom cerah berkedip di tengah bingkai Magnet selama 10 detik pengisian energi, sebelum portal terbuka. Postfx lewat PostChain: satu chain statis dengan tekstur data dinamis (posisi layar, radius, intensitas, warna) yang ditulis ulang tiap frame, karena uniform PostPass di 26.2 tidak bisa diubah setelah chain dibuat. Kedip: puncak tinggi, meredup sedikit, tinggi lagi sedikit lebih rendah; makin terang menjelang portal terbuka. Glow memudar halus bila tengah portal terhalang blok. Server mengirim PortalChargePayload saat Magnet mulai ACTIVE
- Portal burst: ruangan di luar bloom menggelap (efek exposure) saat bloom besar dan terang; bloom sendiri tetap terang
- Portal: pemain dalam 12 blok saat portal terbuka terkena efek flashbang (putih memudar, bayangan sisa di layar, dan jejak frame sebelumnya: kamera bergerak tetapi gambar lama bertahan dan memudar pelan seperti motion blur, lewat target history persisten)
- Portal: bloom memancarkan cahaya ke sekitar (post effect cahaya berwarna), jangkauan dan terangnya mengikuti ukuran bloom, tanpa perlu LambDynamicLights
- Partikel listrik: tipe baru electric_spark_burst, lebih besar dan lebih kuat, menyembur saat bloom state burst
- Portal: jejak frame sebelumnya (motion trail) lebih kuat dan lebih lama: penuh selama 5 detik setelah portal menyala, lalu memudar 2 detik; persistensi per frame 0,95 -> 0,98, kekuatan x1,3
- Portal: cahaya dinamis (LambDynamicLights) dari bloom dibatasi maksimal level 11 dan mengikuti intensitas bloom; bounding box cahaya dipersempit (radius 14 -> 10) sesuai jangkauan nyata
- Portal: alarm baru (portal_alarm) diputar saat portal terbuka, sampai habis, jeda 2 detik, lalu berulang selama blok portal masih ada
- Portal: jejak motion trail tetap penuh sampai jarak 10 blok dari portal, memudar sampai 16 blok (sebelumnya mengikuti falloff flash dan kekuatan tetap saat portal terbuka)
- Portal: semua efek layar (bloom, warp, exposure, flashbang, bayangan sisa, jejak) hanya digambar selagi portal terlihat dari kamera; berlindung di balik blok memudarkannya ke 0. Cek garis pandang ke tengah, atas, dan bawah ruang portal
- Lamp: bloom post effect untuk Lamp menyala. Bentuknya kotak 1 blok yang meluas 0,3 blok di luar tepinya, berwarna dari tekstur Lamp (rata-rata piksel terang), memakai depth buffer sehingga tidak tembus dinding. Maksimal 16 Lamp terdekat di pandangan, memudar antara 24 dan 40 blok. Ikut berkedip bersama Lamp
