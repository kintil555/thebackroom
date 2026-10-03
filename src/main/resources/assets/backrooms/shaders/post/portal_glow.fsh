#version 330

// Bloom/glow di tengah portal yang sedang mengisi energi. Posisi, radius, intensitas, dan warna datang dari
// DataSampler (tekstur 3x4, ditulis ulang tiap frame oleh PortalGlowRenderer), sehingga satu chain statis cukup
// dan tidak perlu dibangun ulang saat kamera bergerak atau intensitas berkedip.
//
// Satu baris = satu sumber (maksimal 4), 8 bit per kanal:
//   texel 0: r,g = x (16 bit hi,lo)   b,a = y (16 bit hi,lo)   posisi layar ternormalisasi, asal kiri-atas
//   texel 1: r,g = radius (16 bit)    b,a = intensitas (16 bit)
//   texel 2: r,g,b = warna tint
// x,y disimpan sebagai (nilai + 0.5) / 2 agar pusat yang sedikit di luar layar tetap terwakili.
//
// Baris ke-4 (index 4) = efek kamera di dekat portal:
//   texel 0: r,g = kekuatan distorsi+blur 0..1 (16 bit)
//   texel 1: r,g = fase waktu 0..1 (16 bit, satu putaran = 4 detik)
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
// Penguat sebelum kurva saturasi: inti glow melewati 1.0 sehingga memutih seperti bloom sungguhan.
const float GAIN = 3.0;
// Glow sedikit memanjang ke atas-bawah, mengikuti bentuk portal 3x5.
const float VERTICAL_STRETCH = 1.35;
// Efek kamera. Ubah angka ini untuk menyetel kekuatannya.
const float LENS_STRENGTH = 0.9;   // kelengkungan barrel/pincushion yang berdenyut
const float WAVE_STRENGTH = 0.012; // riak melengkung (fraksi layar)
const float BLUR_MAX_PX = 18.0;    // radius blur maksimum pada layar tinggi 1080 px
const int BLUR_TAPS = 16;
const float TAU = 6.2831853;

float decode16(vec2 hiLo) {
    return (floor(hiLo.x * 255.0 + 0.5) * 256.0 + floor(hiLo.y * 255.0 + 0.5)) / 65535.0;
}

// Inti tajam + badan lembut + halo lebar, semuanya jatuh ke nol di tepi radius.
float glowShape(float d) {
    float core = exp(-d * d * 42.0);
    float body = exp(-d * d * 10.0);
    float halo = 1.0 / (1.0 + d * d * 16.0);
    float shape = 1.25 * core + 0.60 * body + 0.28 * halo;
    return shape * (1.0 - smoothstep(0.65, 1.0, d));
}

void main() {
    vec4 camData = texelFetch(DataSampler, ivec2(0, MAX_SOURCES), 0);
    vec4 phaseData = texelFetch(DataSampler, ivec2(1, MAX_SOURCES), 0);
    float warp = decode16(camData.rg);
    float phase = decode16(phaseData.rg) * TAU;

    vec3 sceneColor;
    if (warp > 0.001) {
        // Distorsi: lensa yang berdenyut antara cembung dan cekung ditambah riak halus (kelipatan bulat dari fase agar loop mulus).
        vec2 c = texCoord - 0.5;
        float aspect = OutSize.x / OutSize.y;
        vec2 ac = c * vec2(aspect, 1.0);
        float lens = LENS_STRENGTH * warp * sin(phase);
        c *= 1.0 + lens * dot(ac, ac);
        vec2 uv = 0.5 + c;
        uv += vec2(sin(uv.y * 14.0 + phase * 2.0), cos(uv.x * 11.0 + phase)) * WAVE_STRENGTH * warp;

        // Blur: sampling spiral di sekitar uv terdistorsi.
        float radiusPx = warp * BLUR_MAX_PX * (OutSize.y / 1080.0);
        vec2 texel = 1.0 / OutSize;
        vec3 acc = vec3(0.0);
        for (int k = 0; k < BLUR_TAPS; k++) {
            float angle = float(k) * 2.39996323;
            float r = sqrt((float(k) + 0.5) / float(BLUR_TAPS));
            acc += textureLod(InSampler, uv + vec2(cos(angle), sin(angle)) * r * radiusPx * texel, 0.0).rgb;
        }
        sceneColor = acc / float(BLUR_TAPS);
    } else {
        sceneColor = texture(InSampler, texCoord).rgb;
    }
    vec2 pixel = vec2(texCoord.x, 1.0 - texCoord.y) * OutSize;

    vec3 glow = vec3(0.0);
    for (int i = 0; i < MAX_SOURCES; i++) {
        vec4 posData = texelFetch(DataSampler, ivec2(0, i), 0);
        vec4 sizeData = texelFetch(DataSampler, ivec2(1, i), 0);
        float intensity = decode16(sizeData.ba);
        float radius = decode16(sizeData.rg) * OutSize.y;
        if (intensity <= 0.001 || radius <= 1.0) {
            continue;
        }
        vec2 center = (vec2(decode16(posData.rg), decode16(posData.ba)) * 2.0 - 0.5) * OutSize;
        vec2 offset = (pixel - center) / vec2(radius, radius * VERTICAL_STRETCH);
        float d = length(offset);
        if (d >= 1.0) {
            continue;
        }
        vec3 tint = texelFetch(DataSampler, ivec2(2, i), 0).rgb;
        glow += tint * glowShape(d) * intensity * GAIN;
    }

    // Screen-blend lewat eksponensial: menambah terang dengan mulus dan mendekati putih tanpa clipping keras.
    vec3 result = 1.0 - (1.0 - sceneColor) * exp(-glow);
    fragColor = vec4(result, 1.0);
}
