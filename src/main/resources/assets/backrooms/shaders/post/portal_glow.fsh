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
const float GAIN = 2.4;
// Glow sedikit memanjang ke atas-bawah, mengikuti bentuk portal 3x5.
const float VERTICAL_STRETCH = 1.35;

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
    vec4 scene = texture(InSampler, texCoord);
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
    vec3 result = 1.0 - (1.0 - scene.rgb) * exp(-glow);
    fragColor = vec4(result, 1.0);
}
