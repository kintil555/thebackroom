#version 330

// Bloom Lamp berbasis layar (screen-space), mengikuti pendekatan Shine: glow dihitung dari jarak 2D di layar ke Lamp
// yang diproyeksikan, bukan dari jarak 3D ke kotak. Keterlihatan Lamp diukur per piksel dengan mengambil sampel depth
// buffer di sekitar Lamp: bagian yang tertutup blok (dinding, lantai, langit-langit) tidak dihitung, jadi Lamp yang
// sepenuhnya di balik blok tidak membloom sama sekali dan Lamp yang separuh tertutup membloom separuh.
//
// Tata letak DataSampler (16 x 19, float disimpan sebagai 4 byte IEEE per texel, byte rendah di kanal r):
//   baris 0: 16 texel = matriks invers (proyeksi * rotasi view), urutan kolom GLSL
//   baris 1: texel 0 = 1.0 jika depth zero-to-one, texel 1 = radius bloom (blok)
//   baris 2: 16 texel = matriks maju (proyeksi * rotasi view), urutan kolom GLSL
//   baris 3..18: satu Lamp per baris. Texel 0..2 = pusat relatif kamera, texel 3 = r,g,b warna dan a = kekuatan 0..1
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

const int MAX_LAMPS = 16;
const int FIRST_LAMP_ROW = 3;
const float HALF_SIZE = 0.5;
// Kecerahan halo tepat di luar tepi Lamp; menurun halus sampai 0 pada jarak RADIUS.
const float PEAK = 0.34;
// Di atas permukaan Lamp itu sendiri (sudah terang) hanya ditambah sebagian kecil agar teksturnya tidak terbakar putih.
const float CORE_SCALE = 0.35;
const float WHITE_CORE = 0.12;
// Jarak sampel keterlihatan dari pusat Lamp (blok, pada bidang tegak lurus pandangan).
const float SAMPLE_OFFSET = 0.4;

float fetchFloat(int x, int y) {
    vec4 c = texelFetch(DataSampler, ivec2(x, y), 0);
    uvec4 b = uvec4(c * 255.0 + 0.5);
    return uintBitsToFloat(b.r | (b.g << 8u) | (b.b << 16u) | (b.a << 24u));
}

mat4 fetchMatrix(int row) {
    return mat4(
        fetchFloat(0, row), fetchFloat(1, row), fetchFloat(2, row), fetchFloat(3, row),
        fetchFloat(4, row), fetchFloat(5, row), fetchFloat(6, row), fetchFloat(7, row),
        fetchFloat(8, row), fetchFloat(9, row), fetchFloat(10, row), fetchFloat(11, row),
        fetchFloat(12, row), fetchFloat(13, row), fetchFloat(14, row), fetchFloat(15, row));
}

// Jarak dari kamera ke permukaan terlihat pada koordinat layar uv (langit/tak terhingga = sangat jauh).
float sceneDistanceAt(vec2 uv, mat4 inverseMatrix, bool zeroToOne) {
    vec2 clamped = clamp(uv, vec2(0.0), vec2(0.9999));
    float depth = texelFetch(DepthSampler, ivec2(clamped * DepthSize), 0).r;
    if (depth >= 0.99999) {
        return 1.0e6;
    }
    vec2 ndc = clamped * 2.0 - 1.0;
    float ndcZ = zeroToOne ? depth : depth * 2.0 - 1.0;
    vec4 world = inverseMatrix * vec4(ndc, ndcZ, 1.0);
    return abs(world.w) > 1.0e-8 ? length(world.xyz / world.w) : 1.0e6;
}

void main() {
    vec3 scene = texture(InSampler, texCoord).rgb;

    bool zeroToOne = fetchFloat(0, 1) > 0.5;
    float radius = fetchFloat(1, 1);
    mat4 inverseMatrix = fetchMatrix(0);
    mat4 forwardMatrix = fetchMatrix(2);
    // Skala proyeksi (ndc per blok pada kedalaman 1): panjang baris x dan y bagian rotasi-proyeksi.
    float scaleX = length(vec3(forwardMatrix[0][0], forwardMatrix[1][0], forwardMatrix[2][0]));
    float scaleY = length(vec3(forwardMatrix[0][1], forwardMatrix[1][1], forwardMatrix[2][1]));
    vec2 ndc = texCoord * 2.0 - 1.0;

    vec3 additive = vec3(0.0);
    for (int i = 0; i < MAX_LAMPS; i++) {
        int row = FIRST_LAMP_ROW + i;
        vec4 colorData = texelFetch(DataSampler, ivec2(3, row), 0);
        if (colorData.a < 0.004) {
            continue;
        }
        vec3 center = vec3(fetchFloat(0, row), fetchFloat(1, row), fetchFloat(2, row));
        vec4 clip = forwardMatrix * vec4(center, 1.0);
        if (clip.w <= 0.1) {
            continue;
        }
        vec2 ndcCenter = clip.xy / clip.w;
        vec2 ndcPerBlock = vec2(scaleX, scaleY) / clip.w;
        // Jarak di layar dalam satuan blok pada kedalaman Lamp; kotak Lamp dianggap persegi setengah sisi 0,5.
        vec2 delta = abs((ndc - ndcCenter) / ndcPerBlock);
        float edge = length(max(delta - vec2(HALF_SIZE), 0.0));
        if (edge >= radius) {
            continue;
        }

        // Keterlihatan: sampel depth di tengah dan empat titik sekitar Lamp. Permukaan terlihat yang lebih dekat dari
        // permukaan depan Lamp (pusat dikurangi 0,75) berarti tertutup blok.
        float lampDistance = length(center);
        float visible = 0.0;
        vec2 uvCenter = ndcCenter * 0.5 + 0.5;
        vec2 offset = vec2(SAMPLE_OFFSET) * ndcPerBlock * 0.5;
        visible += step(lampDistance - 0.75, sceneDistanceAt(uvCenter, inverseMatrix, zeroToOne)) * 2.0;
        visible += step(lampDistance - 0.75, sceneDistanceAt(uvCenter + vec2(offset.x, 0.0), inverseMatrix, zeroToOne));
        visible += step(lampDistance - 0.75, sceneDistanceAt(uvCenter - vec2(offset.x, 0.0), inverseMatrix, zeroToOne));
        visible += step(lampDistance - 0.75, sceneDistanceAt(uvCenter + vec2(0.0, offset.y), inverseMatrix, zeroToOne));
        visible += step(lampDistance - 0.75, sceneDistanceAt(uvCenter - vec2(0.0, offset.y), inverseMatrix, zeroToOne));
        visible /= 6.0;
        if (visible <= 0.0) {
            continue;
        }

        float amount = 1.0 - smoothstep(0.0, radius, edge);
        amount *= amount;
        if (edge <= 0.001) {
            amount *= CORE_SCALE;
        }
        vec3 hot = mix(colorData.rgb, vec3(1.0), WHITE_CORE * amount);
        additive += hot * amount * PEAK * colorData.a * visible;
    }

    fragColor = vec4(scene + additive, 1.0);
}
