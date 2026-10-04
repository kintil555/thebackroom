#version 330

// Distorsi "gangguan medan magnet" pada portal yang terbuka. Parameter datang dari DataSampler (tekstur 4x5, ditulis
// ulang tiap frame oleh PortalDistortRenderer), 8 bit per kanal, nilai 16 bit disusun dari dua kanal (hi, lo).
// Baris 0..3 = satu portal per baris (maksimal 4):
//   texel 0: r,g = x tengah   b,a = y tengah        layar ternormalisasi, asal kiri-atas, disimpan (nilai + 0.5) / 2
//   texel 1: r,g = x   b,a = y   vektor dari tengah ke titik setengah-lebar portal (R), disimpan (nilai + 2) / 4
//   texel 2: r,g = x   b,a = y   vektor dari tengah ke titik setengah-tinggi portal (U), disimpan (nilai + 2) / 4
//   texel 3: r,g = kekuatan 0..1 (16 bit)   b = seed 0..255
// Baris 4, texel 0: r,g = waktu (detik mod 64) / 64.
// Koordinat lokal (a, b) di bidang portal: tengah = (0,0), tepi portal pada |a| = 1 dan |b| = 1. Efek dibatasi pada
// kotak portal ditambah halo lembut di sekelilingnya, sehingga ruangan di luar portal tidak ikut terdistorsi.
uniform sampler2D InSampler;
uniform sampler2D DataSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
    vec2 DataSize;
};

in vec2 texCoord;

out vec4 fragColor;

const int MAX_SOURCES = 4;
const int ROW_GLOBAL = 4;
// Setelan efek. Ubah angka ini untuk menyetel kekuatannya (px pada layar tinggi 1080).
const float WOBBLE_PX = 7.0;      // goyangan bergelombang
const float RING_PX = 6.0;        // riak cincin medan yang bergerak keluar dari tengah
const float TEAR_PX = 30.0;       // geser horizontal per pita (scanline tear)
const float CHROMA_PX = 4.5;      // pemisahan warna merah/biru
const float HALO = 0.55;          // lebar halo di luar kotak portal (satuan setengah-lebar)
const float BAND_PX = 13.0;       // tinggi pita tear
const float TAU = 6.2831853;

float decode16(vec2 hiLo) {
    return (floor(hiLo.x * 255.0 + 0.5) * 256.0 + floor(hiLo.y * 255.0 + 0.5)) / 65535.0;
}

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

void main() {
    vec2 size = OutSize;
    vec2 pix = vec2(texCoord.x, 1.0 - texCoord.y) * size;
    float scale = size.y / 1080.0;
    float time = decode16(texelFetch(DataSampler, ivec2(0, ROW_GLOBAL), 0).rg) * 64.0;

    vec2 disp = vec2(0.0);
    float chroma = 0.0;
    float grain = 0.0;

    for (int i = 0; i < MAX_SOURCES; i++) {
        vec4 t3 = texelFetch(DataSampler, ivec2(3, i), 0);
        float strength = decode16(t3.rg);
        if (strength < 0.003) {
            continue;
        }
        float seed = floor(t3.b * 255.0 + 0.5);
        vec4 t0 = texelFetch(DataSampler, ivec2(0, i), 0);
        vec4 t1 = texelFetch(DataSampler, ivec2(1, i), 0);
        vec4 t2 = texelFetch(DataSampler, ivec2(2, i), 0);
        vec2 center = (vec2(decode16(t0.rg), decode16(t0.ba)) * 2.0 - 0.5) * size;
        vec2 axisR = (vec2(decode16(t1.rg), decode16(t1.ba)) * 4.0 - 2.0) * size;
        vec2 axisU = (vec2(decode16(t2.rg), decode16(t2.ba)) * 4.0 - 2.0) * size;

        float det = axisR.x * axisU.y - axisR.y * axisU.x;
        if (abs(det) < 1.0) {
            continue;
        }
        vec2 d = pix - center;
        vec2 local = vec2(d.x * axisU.y - d.y * axisU.x, axisR.x * d.y - axisR.y * d.x) / det;

        vec2 overshoot = max(abs(local) - 1.0, vec2(0.0));
        float mask = 1.0 - smoothstep(0.0, HALO, length(overshoot));
        if (mask <= 0.001) {
            continue;
        }
        float amount = mask * strength;

        float radius = length(local);
        float angle = atan(local.y, local.x);
        // Cincin medan bergerak keluar, ditambah lengkung garis gaya yang berputar pelan.
        float ring = sin(radius * 9.0 - time * 5.0 + seed);
        float arcs = sin(angle * 3.0 + radius * 2.0 + time * 1.7 + seed * 1.3);
        vec2 wobble = vec2(sin(pix.y / scale * 0.045 + time * 6.0 + seed),
                           cos(pix.x / scale * 0.035 - time * 4.5 + seed * 2.0));

        // Pita horizontal yang sesekali tergeser tiba-tiba (gangguan sinyal), lebih sering saat kuat.
        float band = floor(pix.y / (BAND_PX * scale));
        float tick = floor(time * 14.0);
        float h = hash21(vec2(band + seed, tick));
        float threshold = mix(0.93, 0.55, smoothstep(0.35, 1.0, strength));
        float tear = h > threshold ? (hash21(vec2(band + 17.0, tick + seed)) - 0.5) * 2.0 : 0.0;

        disp += amount * scale * (wobble * WOBBLE_PX + vec2(ring, arcs) * RING_PX + vec2(tear * TEAR_PX, 0.0));
        chroma += amount;
        grain += amount * (0.4 + 0.6 * abs(arcs));
    }

    chroma = min(chroma, 1.0);
    grain = min(grain, 1.0);
    vec2 split = vec2(CHROMA_PX * scale * chroma, 0.0);

    vec2 pr = clamp(pix + disp + split, vec2(0.5), size - 0.5);
    vec2 pg = clamp(pix + disp, vec2(0.5), size - 0.5);
    vec2 pb = clamp(pix + disp - split, vec2(0.5), size - 0.5);
    vec3 color = vec3(
        texture(InSampler, vec2(pr.x / size.x, 1.0 - pr.y / size.y)).r,
        texture(InSampler, vec2(pg.x / size.x, 1.0 - pg.y / size.y)).g,
        texture(InSampler, vec2(pb.x / size.x, 1.0 - pb.y / size.y)).b);

    // Statik tipis kehijauan, kuat di bagian yang paling terganggu.
    float noise = hash21(floor(pix / max(1.0, 2.0 * scale)) + floor(time * 30.0));
    color += vec3(0.02, 0.10, 0.04) * grain * (noise - 0.35);
    fragColor = vec4(color, 1.0);
}
