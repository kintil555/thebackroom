#version 330

// Bloom Lamp berbasis layar (screen-space), mengikuti pendekatan Shine: sumber bloom = piksel Lamp yang benar-benar
// terlihat. Area sekitar Lamp disampel pada grid layar; sampel dihitung sebagai piksel Lamp hanya jika posisi dunia
// hasil depth buffer-nya berada di dalam kotak Lamp. Tiap piksel menjumlahkan kontribusi sampel tersebut menurut jarak
// layar, jadi Lamp yang tertutup sebagian membloom sebanding bagian yang terlihat (stabil, tanpa lompatan) dan Lamp yang
// tertutup penuh tidak membloom sama sekali.
//
// Tata letak DataSampler (16 x 35, float disimpan sebagai 4 byte IEEE per texel, byte rendah di kanal r):
//   baris 0: 16 texel = matriks invers (proyeksi * rotasi view), urutan kolom GLSL
//   baris 1: texel 0 = 1.0 jika depth zero-to-one, texel 1 = radius bloom (blok)
//   baris 2: 16 texel = matriks maju (proyeksi * rotasi view), urutan kolom GLSL
//   baris 3..34: satu Lamp per baris. Texel 0..2 = pusat relatif kamera, texel 3 = r,g,b warna dan a = kekuatan 0..1
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

const int MAX_LAMPS = 32;
const int FIRST_LAMP_ROW = 3;
const float HALF_SIZE = 0.5;
// Kecerahan halo tepat di luar tepi Lamp; menurun halus sampai 0 pada jarak RADIUS.
const float PEAK = 0.4;
// Di atas permukaan Lamp itu sendiri (sudah terang) hanya ditambah sebagian kecil agar teksturnya tidak terbakar putih.
const float CORE_SCALE = 0.35;
const float WHITE_CORE = 0.12;
// Jarak sampel keterlihatan dari pusat Lamp (blok, pada bidang tegak lurus pandangan).
const float SAMPLE_OFFSET = 0.4;
// Grid sampel sumber di layar: (2*GRID_HALF+1)^2 titik berjarak GRID_STEP blok, menutupi proyeksi kubus (<= 0,87 blok).
const int GRID_HALF = 2;
const float GRID_STEP = 0.4;
const float GRID_EXTENT = 0.8;
// Jumlah bobot sampel yang dianggap bloom penuh.
const float NORMALIZE = 6.0;

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

// Posisi dunia (relatif kamera) permukaan terlihat pada koordinat layar uv; langit = sangat jauh.
vec3 sceneWorldAt(vec2 uv, mat4 inverseMatrix, bool zeroToOne) {
    vec2 clamped = clamp(uv, vec2(0.0), vec2(0.9999));
    float depth = texelFetch(DepthSampler, ivec2(clamped * DepthSize), 0).r;
    if (depth >= 0.99999) {
        return vec3(1.0e6);
    }
    vec2 ndc = clamped * 2.0 - 1.0;
    float ndcZ = zeroToOne ? depth : depth * 2.0 - 1.0;
    vec4 world = inverseMatrix * vec4(ndc, ndcZ, 1.0);
    return abs(world.w) > 1.0e-8 ? world.xyz / world.w : vec3(1.0e6);
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
    float pixelDistance = sceneDistanceAt(texCoord, inverseMatrix, zeroToOne);

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
        // Posisi piksel relatif pusat Lamp di layar, dalam satuan blok pada kedalaman Lamp.
        vec2 signedDelta = (ndc - ndcCenter) / ndcPerBlock;
        if (max(abs(signedDelta.x), abs(signedDelta.y)) > GRID_EXTENT + radius) {
            continue;
        }

        // Piksel yang jauh lebih dekat ke kamera daripada Lamp (dinding di depan Lamp) tidak ikut berpendar.
        float lampDistance = length(center);
        float behind = smoothstep(lampDistance - 1.6, lampDistance - 0.8, pixelDistance);
        if (behind <= 0.0) {
            continue;
        }

        // Jumlahkan kontribusi sampel yang benar-benar permukaan Lamp (posisi dunia di dalam kotak Lamp).
        float sum = 0.0;
        for (int gx = -GRID_HALF; gx <= GRID_HALF; gx++) {
            for (int gy = -GRID_HALF; gy <= GRID_HALF; gy++) {
                vec2 offsetBlocks = vec2(float(gx), float(gy)) * GRID_STEP;
                float d = length(signedDelta - offsetBlocks);
                if (d >= radius) {
                    continue;
                }
                vec2 sampleUv = (ndcCenter + offsetBlocks * ndcPerBlock) * 0.5 + 0.5;
                vec3 sampleWorld = sceneWorldAt(sampleUv, inverseMatrix, zeroToOne);
                vec3 inside = abs(sampleWorld - center);
                if (max(inside.x, max(inside.y, inside.z)) <= HALF_SIZE + 0.06) {
                    float w = 1.0 - smoothstep(0.0, radius, d);
                    sum += w * w;
                }
            }
        }
        float amount = min(sum / NORMALIZE, 1.0) * behind;
        if (amount <= 0.0) {
            continue;
        }
        vec3 hot = mix(colorData.rgb, vec3(1.0), WHITE_CORE * amount);
        additive += hot * amount * PEAK * colorData.a;
    }

    fragColor = vec4(scene + additive, 1.0);
}
