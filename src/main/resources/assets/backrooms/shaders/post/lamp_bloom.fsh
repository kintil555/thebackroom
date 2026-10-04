#version 330

// Bloom Lamp lewat post effect. Tiap Lamp menyala = kotak 1 blok (setengah sisi 0.5) yang bloomnya meluas RADIUS blok di
// luar tepinya. Untuk tiap piksel, sinar kamera dicari titik terdekatnya ke kotak (pencarian terner, sdBox cembung)
// hanya sampai permukaan yang terlihat di piksel itu, jadi bagian Lamp yang tertutup dinding tidak membloom, tetapi
// udara dan permukaan di sekitar Lamp yang terlihat ikut berpendar. Posisi dunia (relatif kamera) direkonstruksi dari
// depth buffer, sama seperti colored_light.fsh.
//
// Tata letak DataSampler (16 x 18, float disimpan sebagai 4 byte IEEE per texel, byte rendah di kanal r):
//   baris 0: 16 texel = matriks invers (proyeksi * rotasi view), urutan kolom GLSL
//   baris 1: texel 0 = 1.0 jika depth zero-to-one, texel 1 = radius bloom (blok)
//   baris 2..17: satu Lamp per baris. Texel 0..2 = pusat relatif kamera, texel 3 = r,g,b warna dan a = kekuatan 0..1 (8 bit)
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
const int FIRST_LAMP_ROW = 2;
const float HALF_SIZE = 0.5;
// Setengah diagonal kubus: sinar yang lebih jauh dari ini (ditambah radius) dari pusat tidak mungkin menyentuh bloom.
const float HALF_DIAGONAL = 0.8660254;
// Kecerahan halo tepat di luar tepi kotak; menurun halus sampai 0 pada jarak RADIUS.
const float PEAK = 0.28;
// Di dalam kotak (permukaan Lamp itu sendiri, yang sudah terang) hanya ditambah sebagian kecil agar teksturnya tidak terbakar putih.
const float CORE_SCALE = 0.3;
// Seberapa jauh halo memutih (0 = murni warna tekstur, 1 = putih).
const float WHITE_CORE = 0.12;
const int SEARCH_STEPS = 12;

float fetchFloat(int x, int y) {
    vec4 c = texelFetch(DataSampler, ivec2(x, y), 0);
    uvec4 b = uvec4(c * 255.0 + 0.5);
    return uintBitsToFloat(b.r | (b.g << 8u) | (b.b << 16u) | (b.a << 24u));
}

// Jarak bertanda ke kotak 1 blok berpusat di titik asal.
float sdBox(vec3 p) {
    vec3 q = abs(p) - vec3(HALF_SIZE);
    return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0);
}

void main() {
    vec3 scene = texture(InSampler, texCoord).rgb;

    float depth = texelFetch(DepthSampler, ivec2(texCoord * DepthSize), 0).r;
    bool zeroToOne = fetchFloat(0, 1) > 0.5;
    float radius = fetchFloat(1, 1);
    mat4 inverseMatrix = mat4(
        fetchFloat(0, 0), fetchFloat(1, 0), fetchFloat(2, 0), fetchFloat(3, 0),
        fetchFloat(4, 0), fetchFloat(5, 0), fetchFloat(6, 0), fetchFloat(7, 0),
        fetchFloat(8, 0), fetchFloat(9, 0), fetchFloat(10, 0), fetchFloat(11, 0),
        fetchFloat(12, 0), fetchFloat(13, 0), fetchFloat(14, 0), fetchFloat(15, 0));

    vec2 ndc = texCoord * 2.0 - 1.0;
    float ndcZ = zeroToOne ? depth : depth * 2.0 - 1.0;
    vec4 world = inverseMatrix * vec4(ndc, ndcZ, 1.0);
    vec3 scenePosition = abs(world.w) > 1.0e-8 ? world.xyz / world.w : vec3(1.0e6);
    float sceneDistance = length(scenePosition);
    bool hasSurface = depth < 0.9999;
    // Normal permukaan (menghadap kamera) dari turunan posisi; dipakai agar dinding yang menutupi Lamp tidak ikut berpendar.
    vec3 surfaceNormal = normalize(cross(dFdx(scenePosition), dFdy(scenePosition)));
    if (dot(surfaceNormal, scenePosition) > 0.0) {
        surfaceNormal = -surfaceNormal;
    }

    // Arah sinar dari titik di kedalaman tengah (aman juga untuk langit yang depth-nya di ujung).
    vec4 mid = inverseMatrix * vec4(ndc, zeroToOne ? 0.5 : 0.0, 1.0);
    vec3 direction = normalize(mid.xyz / mid.w);

    vec3 additive = vec3(0.0);
    for (int i = 0; i < MAX_LAMPS; i++) {
        int row = FIRST_LAMP_ROW + i;
        vec4 colorData = texelFetch(DataSampler, ivec2(3, row), 0);
        if (colorData.a < 0.004) {
            continue;
        }
        vec3 center = vec3(fetchFloat(0, row), fetchFloat(1, row), fetchFloat(2, row));
        float along = dot(center, direction);
        if (along < -1.0 || length(center - direction * along) > HALF_DIAGONAL + radius) {
            continue;
        }
        // Hanya bagian sinar sebelum permukaan yang terlihat; kotak di belakang dinding tidak dihitung.
        float low = max(0.0, along - 1.0);
        float high = min(along + 1.0, sceneDistance + 0.02);
        if (low > high) {
            continue;
        }
        for (int k = 0; k < SEARCH_STEPS; k++) {
            float third = (high - low) / 3.0;
            float a = low + third;
            float b = high - third;
            if (sdBox(direction * a - center) < sdBox(direction * b - center)) {
                high = b;
            } else {
                low = a;
            }
        }
        float tClosest = 0.5 * (low + high);
        float closest = max(sdBox(direction * tClosest - center), 0.0);
        // Titik pada permukaan terlihat: titik terdekat kotak Lamp tidak boleh berada di belakang bidang permukaan
        // (Lamp di balik dinding). Permukaan rata dengan Lamp (titik terdekat di bidang yang sama) tetap lolos.
        if (hasSurface && tClosest > sceneDistance - 0.05) {
            vec3 rel = scenePosition - center;
            vec3 boxPoint = center + clamp(rel, vec3(-HALF_SIZE), vec3(HALF_SIZE));
            if (dot(surfaceNormal, boxPoint - scenePosition) < -0.03) {
                continue;
            }
        }
        if (closest >= radius) {
            continue;
        }
        float amount = 1.0 - smoothstep(0.0, radius, closest);
        amount *= amount;
        if (closest <= 0.001) {
            amount *= CORE_SCALE;
        }
        vec3 hot = mix(colorData.rgb, vec3(1.0), WHITE_CORE * amount);
        additive += hot * amount * PEAK * colorData.a;
    }

    fragColor = vec4(scene + additive, 1.0);
}
