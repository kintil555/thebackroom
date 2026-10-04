#version 330

// Efek layar untuk portal Magnet, tiga fase dalam satu pass:
//   1. api energi hijau yang mengalir memutar seperti fluida selama pengisian energi (flame),
//   2. distorsi medan magnet melengkung yang permanen selama portal terbuka (strength),
//   3. animasi menutup: lengkungan menguat, putih kehijauan bercahaya, lalu mengecil ke tengah dan memudar (close).
// Parameter datang dari DataSampler (tekstur 16 x 11 (blok oklusi depth ikut di baris 5 dst), ditulis ulang tiap frame oleh PortalDistortRenderer), 8 bit per kanal,
// nilai 16 bit disusun dari dua kanal (hi, lo). Baris 0..3 = satu portal per baris (maksimal 4):
//   texel 0: r,g = x tengah   b,a = y tengah        layar ternormalisasi, asal kiri-atas, disimpan (nilai + 0.5) / 2
//   texel 1: r,g = x   b,a = y   vektor dari tengah ke titik setengah-lebar portal (R), disimpan (nilai + 2) / 4
//   texel 2: r,g = x   b,a = y   vektor dari tengah ke titik setengah-tinggi portal (U), disimpan (nilai + 2) / 4
//   texel 3: r,g = kekuatan distorsi 0..1 (16 bit)   b = seed 0..255
//   texel 4: r,g = kemajuan penutupan 0..1 (0 = tidak menutup)   b,a = jumlah api 0..1 (16 bit)
// Baris 4, texel 0: r,g = waktu (detik mod 64) / 64.
// Koordinat lokal (a, b) di bidang portal: tengah = (0,0), tepi portal pada |a| = 1 dan |b| = 1. Efek dibatasi pada
// kotak portal ditambah halo lembut di sekelilingnya, sehingga ruangan di luar portal tidak ikut terdistorsi.
// Oklusi per piksel: api, cahaya penutupan, dan distorsi hanya digambar di piksel yang tidak tertutup benda di depan bidang portal
// (API DepthOcclusion). Urutan SamplerInfo mengikuti urutan input chain: In, Data, Depth.
uniform sampler2D InSampler;
uniform sampler2D DataSampler;
uniform sampler2D DepthSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
    vec2 DataSize;
    vec2 DepthSize;
};

#moj_import <backrooms:depth_occlusion.glsl>

in vec2 texCoord;

out vec4 fragColor;

const int MAX_SOURCES = 4;
const int ROW_GLOBAL = 4;
// Baris pertama blok oklusi depth (lihat DepthOcclusion.java); harus sama dengan DepthOcclusion.at(...) di PortalDistortRenderer.
const int OCCLUSION_ROW = MAX_SOURCES + 1;
// Setelan distorsi. Ubah angka ini untuk menyetel kekuatannya (px pada layar tinggi 1080).
const float WOBBLE_PX = 7.0;      // goyangan bergelombang
const float RING_PX = 6.0;        // riak cincin medan yang bergerak keluar dari tengah
const float TEAR_PX = 30.0;       // geser horizontal per pita (scanline tear)
const float CHROMA_PX = 4.5;      // pemisahan warna merah/biru
const float HALO = 0.55;          // lebar halo di luar kotak portal (satuan setengah-lebar)
const float BAND_PX = 13.0;       // tinggi pita tear
// Lengkungan permanen portal terbuka (fraksi koordinat lokal) dan tambahan saat menutup.
const float CURVE = 0.14;
const float CLOSE_CURVE = 0.60;
const float CLOSE_TWIST = 2.2;    // putaran (radian) di tengah saat menutup
// Penutupan: 0..CLOSE_SPLIT membangun distorsi + cahaya, CLOSE_SPLIT..1 mengecil ke tengah (harus sama dengan PortalDistortRenderer).
const float CLOSE_SPLIT = 0.40;
const float CLOSE_GLOW_GAIN = 2.6;
// Api: setengah ukuran ruang portal (blok), untuk menjaga pusaran tetap bulat.
const vec2 HALF_SIZE = vec2(1.5, 2.5);
const float FLAME_GAIN = 1.9;

float decode16(vec2 hiLo) {
    return (floor(hiLo.x * 255.0 + 0.5) * 256.0 + floor(hiLo.y * 255.0 + 0.5)) / 65535.0;
}

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

mat2 rot(float a) {
    float c = cos(a);
    float s = sin(a);
    return mat2(c, -s, s, c);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    mat2 m = mat2(0.8, -0.6, 0.6, 0.8);
    for (int i = 0; i < 4; i++) {
        v += a * vnoise(p);
        p = m * p * 2.03 + vec2(7.1, 3.7);
        a *= 0.5;
    }
    return v;
}

// Api energi hijau: domain warping (fbm di dalam fbm) di ruang yang dipuntir pusaran, jadi alirannya melilit seperti
// fluida; pola naik pelan seperti api dan menumpuk di tepi bingkai. Hasilnya energi aditif.
vec3 flame(vec2 local, float time, float seed, float amount) {
    vec2 p = local * HALF_SIZE;
    float r = min(length(local), 1.3);
    float spin = time * (0.5 + 1.1 * amount);
    vec2 q = rot(spin + (1.3 - r) * (1.2 + 2.2 * amount)) * p;
    vec2 rise = vec2(0.0, -time * 0.8);
    float n1 = fbm(q * 1.1 + rise + seed);
    float n2 = fbm(q * 1.5 + 2.6 * vec2(n1, fbm(q * 0.9 - time * 0.35 + seed * 1.7)) + rise * 1.2);
    float dens = smoothstep(0.30, 0.85, n2);
    float box = max(abs(local.x), abs(local.y));
    dens *= mix(0.55, 1.0, smoothstep(0.25, 1.0, box));
    float cover = 1.0 - smoothstep(0.92, 1.12, box);
    float e = dens * cover * amount;
    vec3 deep = vec3(0.02, 0.40, 0.08);
    vec3 mid = vec3(0.20, 1.00, 0.22);
    vec3 hot = vec3(0.75, 1.00, 0.70);
    vec3 c = mix(deep, mid, smoothstep(0.10, 0.60, dens));
    c = mix(c, hot, smoothstep(0.65, 1.0, dens));
    return c * e * FLAME_GAIN;
}

// Cahaya putih kehijauan saat menutup: menguat sampai CLOSE_SPLIT, lalu ikut mengecil bersama portal dan memudar.
vec3 closeGlow(vec2 local, float t) {
    float e = clamp((t - CLOSE_SPLIT) / (1.0 - CLOSE_SPLIT), 0.0, 1.0);
    float s = max(1.0 - e * e, 0.0005);
    float build = smoothstep(0.0, CLOSE_SPLIT, t);
    float fade = 1.0 - smoothstep(0.50, 1.0, t);
    vec2 l = local / s;
    float box = max(abs(l.x), abs(l.y));
    float body = 1.0 - smoothstep(0.75, 1.05, box);
    float core = exp(-dot(l, l) * 1.2);
    float halo = exp(-dot(l, l) * 0.30);
    float g = (0.8 * body + 1.6 * core + 0.7 * halo) * build * fade;
    vec3 tint = mix(vec3(0.55, 1.0, 0.70), vec3(1.0, 1.0, 0.95), core);
    return tint * g * CLOSE_GLOW_GAIN;
}

void main() {
    vec2 size = OutSize;
    vec2 pix = vec2(texCoord.x, 1.0 - texCoord.y) * size;
    float scale = size.y / 1080.0;
    float time = decode16(texelFetch(DataSampler, ivec2(0, ROW_GLOBAL), 0).rg) * 64.0;

    vec2 disp = vec2(0.0);
    vec3 glow = vec3(0.0);
    float chroma = 0.0;
    float grain = 0.0;

    for (int i = 0; i < MAX_SOURCES; i++) {
        vec4 t3 = texelFetch(DataSampler, ivec2(3, i), 0);
        vec4 t4 = texelFetch(DataSampler, ivec2(4, i), 0);
        float strength = decode16(t3.rg);
        float close = decode16(t4.rg);
        float flameAmount = decode16(t4.ba);
        if (strength < 0.003 && flameAmount < 0.003) {
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

        // Jauh di luar kotak portal dan tidak sedang menutup: tidak ada kontribusi, lewati sebelum membaca depth.
        float box = max(abs(local.x), abs(local.y));
        if (box > 1.7 && close <= 0.001) {
            continue;
        }
        // Piksel yang permukaan terlihatnya menutupi bidang portal tidak ikut efek (tengah tertutup tidak mematikan sisanya).
        float open = depthOcclusionOpenness(DepthSampler, DepthSize, DataSampler, OCCLUSION_ROW, i, texCoord);
        if (open <= 0.001) {
            continue;
        }

        // Api pengisian energi (portal belum ada, hanya bingkai).
        if (flameAmount > 0.003 && box < 1.2) {
            glow += flame(local, time, seed, flameAmount) * open;
        }
        // Cahaya penutupan.
        if (close > 0.001) {
            glow += closeGlow(local, close) * open;
        }
        if (strength < 0.003) {
            continue;
        }

        // Saat menutup, daerah distorsi ikut mengecil bersama portal (rumus skala sama dengan closeGlow dan shrinkPortal).
        float shrinkE = clamp((close - CLOSE_SPLIT) / (1.0 - CLOSE_SPLIT), 0.0, 1.0);
        float s = close > 0.001 ? max(1.0 - shrinkE * shrinkE, 0.0005) : 1.0;
        float tailFade = 1.0 - smoothstep(0.85, 1.0, close);
        vec2 ld = local / s;

        vec2 overshoot = max(abs(ld) - 1.0, vec2(0.0));
        float mask = (1.0 - smoothstep(0.0, HALO, length(overshoot))) * tailFade;
        if (mask <= 0.001) {
            continue;
        }
        mask *= open;
        float amount = mask * strength;

        float radius = length(ld);
        float angle = atan(ld.y, ld.x);
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

        // Lengkungan: isi portal menggembung seperti lensa (permanen, berdenyut tipis), makin kuat dan berputar saat menutup.
        float closeA = close > 0.001 ? smoothstep(0.0, CLOSE_SPLIT, close) : 0.0;
        float pulse = 0.8 + 0.2 * sin(time * 2.0 + seed);
        float bend = CURVE * strength * pulse + CLOSE_CURVE * closeA;
        vec2 bent = ld * (1.0 + bend * (1.0 - min(dot(ld, ld), 2.0) * 0.35));
        bent = rot(closeA * CLOSE_TWIST * (1.0 - min(radius, 1.0))) * bent;
        vec2 bendDelta = (bent - ld) * s;

        disp += amount * scale * (wobble * WOBBLE_PX + vec2(ring, arcs) * RING_PX + vec2(tear * TEAR_PX, 0.0));
        disp += mask * (axisR * bendDelta.x + axisU * bendDelta.y);
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

    // Api dan cahaya penutupan: screen-blend lewat eksponensial, mendekati putih tanpa clipping keras.
    color = 1.0 - (1.0 - clamp(color, 0.0, 1.0)) * exp(-glow);
    fragColor = vec4(color, 1.0);
}
