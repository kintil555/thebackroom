# Backrooms (Fabric, Minecraft 26.2)

Mod bertema Backrooms. Tahap ini: blok dekorasi saja (dimensi menyusul).

| Item/Blok | Fungsi |
|---|---|
| Screw Piles | Pondasi besi. Tiap sisinya bisa dilapisi Wallpaper/Carpet (disimpan di blockstate-nya sendiri) |
| Wallpaper | Item. Klik kanan sisi Screw Piles -> tekstur menempel di face itu. Break (bidik sisi berlapis) butuh 4 detik, hanya lapisan yang lepas |
| Carpet | Item. Klik kanan sisi penuh blok mana pun -> menempel di face itu (disimpan di data chunk, bukan blok terpisah). Break hanya melepas carpet, blok tetap |

Middle click blok berlapis meng-clone blok itu beserta lapisannya (contoh: "Stone Attached Carpet").

Retak (crack) dan partikel saat break hanya muncul di sisi yang berlapis. Jika blok pemilik carpet dihancurkan/diganti, carpet ikut jatuh.

Item ada di creative tab **Building Blocks**.

## Efek glow portal

Saat bingkai Magnet lengkap dialiri redstone, selama 10 detik pengisian energi muncul bloom cerah berkedip di tengah ruang portal 3x5, lalu hilang ketika portal terbuka. Setelan (radius, warna, kecerahan awal) ada di konstanta atas `PortalGlowRenderer`; pola kedip di `PortalGlowFlicker`; bentuk glow di `assets/backrooms/shaders/post/portal_glow.fsh`.

## Build
Butuh JDK 25.

    ./gradlew build        # jar di build/libs/
    ./gradlew runClient    # jalankan Minecraft dev

Dependensi: Fabric Loader >= 0.19.5, Fabric API 0.157.0+26.2.
