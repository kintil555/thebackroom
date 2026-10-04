#version 330

// Cahaya berwarna lewat post effect. Posisi dunia (relatif kamera) tiap piksel direkonstruksi dari depth buffer
// memakai invers matriks (proyeksi * rotasi view) dari DataSampler, lalu piksel diberi tint sesuai jarak ke tiap
// sumber. Normal permukaan dari turunan posisi layar: sisi yang membelakangi sumber (mis. sisi lain dinding) lebih redup.
//
// Tata letak DataSampler (16 x 10, float disimpan sebagai 4 byte IEEE per texel, byte rendah di kanal r):
//   baris 0: 16 texel = matriks invers, urutan kolom GLSL
//   baris 1, texel 0: 1.0 jika depth zero-to-one, 0.0 jika -1..1
//   baris 2..9: satu cahaya per baris.
//     texel 0..2 = posisi relatif kamera, texel 3 = radius (blok), texel 4 = r,g,b warna dan a = intensitas (8 bit)
// Urutan SamplerInfo mengikuti urutan input chain: In, Depth, Data.
uniform sampler2D InSampler;
uniform sampler2D DepthSampler;
uniform sampler2D DataSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
    vec2 DepthSize;
    vec2 DataSize;
};

in vec2 texCoord;

out vec4 fragColor;

const int MAX_LIGHTS = 8;
const int FIRST_LIGHT_ROW = 2;
// Seberapa kuat tint menggantikan warna asli di dekat sumber (0..1).
const float STRENGTH = 0.95;
// Pengali warna sumber: >1 mengangkat kanal dominan, sisanya ditekan sehingga hasilnya merah, bukan merah muda.
const float COLOR_GAIN = 1.7;

float fetchFloat(int x, int y) {
    vec4 c = texelFetch(DataSampler, ivec2(x, y), 0);
    uvec4 b = uvec4(c * 255.0 + 0.5);
    return uintBitsToFloat(b.r | (b.g << 8u) | (b.b << 16u) | (b.a << 24u));
}

void main() {
    vec3 scene = texture(InSampler, texCoord).rgb;

    // Diproses untuk semua piksel (sebelum percabangan) agar dFdx/dFdy aman.
    float depth = texelFetch(DepthSampler, ivec2(texCoord * DepthSize), 0).r;
    bool zeroToOne = fetchFloat(0, 1) > 0.5;
    float ndcZ = zeroToOne ? depth : depth * 2.0 - 1.0;
    mat4 inverseMatrix = mat4(
        fetchFloat(0, 0), fetchFloat(1, 0), fetchFloat(2, 0), fetchFloat(3, 0),
        fetchFloat(4, 0), fetchFloat(5, 0), fetchFloat(6, 0), fetchFloat(7, 0),
        fetchFloat(8, 0), fetchFloat(9, 0), fetchFloat(10, 0), fetchFloat(11, 0),
        fetchFloat(12, 0), fetchFloat(13, 0), fetchFloat(14, 0), fetchFloat(15, 0));
    vec4 world = inverseMatrix * vec4(texCoord * 2.0 - 1.0, ndcZ, 1.0);
    vec3 position = abs(world.w) > 1.0e-8 ? world.xyz / world.w : vec3(1.0e6);

    vec3 normal = cross(dFdx(position), dFdy(position));
    float normalLength = length(normal);
    bool hasNormal = normalLength > 1.0e-12 && normalLength < 1.0e12;
    if (hasNormal) {
        normal /= normalLength;
        if (dot(normal, position) > 0.0) {
            normal = -normal;
        }
    }

    vec3 multiplier = vec3(1.0);
    vec3 additive = vec3(0.0);
    for (int i = 0; i < MAX_LIGHTS; i++) {
        int row = FIRST_LIGHT_ROW + i;
        vec4 colorData = texelFetch(DataSampler, ivec2(4, row), 0);
        float intensity = colorData.a;
        if (intensity <= 0.003) {
            continue;
        }
        vec3 lightPosition = vec3(fetchFloat(0, row), fetchFloat(1, row), fetchFloat(2, row));
        float radius = fetchFloat(3, row);
        vec3 toLight = lightPosition - position;
        float distanceToLight = length(toLight);
        if (distanceToLight >= radius || radius <= 0.0) {
            continue;
        }
        float falloff = 1.0 - distanceToLight / radius;
        falloff *= falloff;
        float facing = hasNormal ? clamp(dot(normal, toLight / max(distanceToLight, 1.0e-4)) * 0.65 + 0.35, 0.0, 1.0) : 0.5;
        float amount = clamp(falloff * facing * intensity * STRENGTH, 0.0, 1.0);
        vec3 tint = colorData.rgb * COLOR_GAIN + 0.03;
        multiplier *= mix(vec3(1.0), tint, amount);
        additive += colorData.rgb * amount * 0.05;
    }

    fragColor = vec4(scene * multiplier + additive, 1.0);
}
