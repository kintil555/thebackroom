// API volume berbasis depth buffer: efek yang benar-benar ada di ruang dunia (kabut, api, energi), bukan tempelan 2D di layar.
// Sinar tiap piksel dipotong oleh permukaan terlihat (depth buffer), jadi volume tertutup dinding secara fisik, punya parallax,
// dan menipis saat ada benda memotongnya. Wajib di-import SETELAH depth_occlusion.glsl (memakai fungsi depOccFetch*).
//   #moj_import <backrooms:depth_occlusion.glsl>
//   #moj_import <backrooms:depth_volume.glsl>
// Pakai:
//   vec3 rayDir; float sceneDist;
//   depVolBegin(DepthSampler, DepthSize, DataSampler, FIRST_ROW, texCoord, rayDir, sceneDist);
//   vec3 center, tangent, normal; float halfW, halfH;
//   depVolLoadBox(DataSampler, FIRST_ROW, i, center, tangent, normal, halfW, halfH);
//   float t0, t1;
//   if (depVolSegment(rayDir, sceneDist, center, tangent, normal, vec3(halfW, halfH, halfDepth), t0, t1)) {
//       // march t0..t1: titik = rayDir * t (relatif kamera), depVolLocal(...) memberi koordinat lokal kotak
//   }
// Data kotak diambil dari baris sumber DepthOcclusion (pusat, normal, setengah lebar, setengah tinggi; lihat Source.rect).

const float DEP_VOL_FAR = 1.0e6;

// Arah sinar piksel dan jarak (blok) ke permukaan terlihat sepanjang sinar itu; langit = DEP_VOL_FAR.
void depVolBegin(sampler2D depthTex, vec2 depthSize, sampler2D dataTex, int firstRow, vec2 uv, out vec3 rayDir, out float sceneDist) {
    mat4 inverseMatrix = depOccFetchMatrix(dataTex, firstRow);
    bool zeroToOne = depOccFetchFloat(dataTex, 0, firstRow + 1) > 0.5;
    vec2 clamped = clamp(uv, vec2(0.0), vec2(0.9999));
    vec2 ndc = clamped * 2.0 - 1.0;
    // z sembarang cukup untuk arah (semua titik pada sinar yang sama); 0.5 / 0.0 aman terhadap reverse-Z.
    vec4 dirPoint = inverseMatrix * vec4(ndc, zeroToOne ? 0.5 : 0.0, 1.0);
    rayDir = normalize(dirPoint.xyz / (abs(dirPoint.w) > 1.0e-8 ? dirPoint.w : 1.0e-8));
    float depth = texelFetch(depthTex, ivec2(clamped * depthSize), 0).r;
    sceneDist = DEP_VOL_FAR;
    if (depth > 0.00001) {
        vec4 world = inverseMatrix * vec4(ndc, zeroToOne ? depth : depth * 2.0 - 1.0, 1.0);
        if (abs(world.w) > 1.0e-8) {
            sceneDist = length(world.xyz / world.w);
        }
    }
}

// Kotak tegak sumber: tangent = cross(Y, normal), tinggi searah Y.
void depVolLoadBox(sampler2D dataTex, int firstRow, int sourceIndex, out vec3 center, out vec3 tangent, out vec3 normal, out float halfW, out float halfH) {
    int row = firstRow + 2 + sourceIndex;
    center = depOccFetchVec3(dataTex, 0, row);
    normal = depOccFetchVec3(dataTex, 3, row);
    halfW = depOccFetchFloat(dataTex, 6, row);
    halfH = depOccFetchFloat(dataTex, 7, row);
    tangent = normalize(cross(vec3(0.0, 1.0, 0.0), normal));
}

// Irisan sinar dengan kotak berorientasi (setengah ukuran halfExt = lebar, tinggi, kedalaman), dipotong di sceneDist. true jika ada isi.
bool depVolSegment(vec3 rayDir, float sceneDist, vec3 center, vec3 tangent, vec3 normal, vec3 halfExt, out float t0, out float t1) {
    vec3 o = vec3(dot(-center, tangent), -center.y, dot(-center, normal));
    vec3 d = vec3(dot(rayDir, tangent), rayDir.y, dot(rayDir, normal));
    d = vec3(abs(d.x) < 1.0e-5 ? 1.0e-5 : d.x, abs(d.y) < 1.0e-5 ? 1.0e-5 : d.y, abs(d.z) < 1.0e-5 ? 1.0e-5 : d.z);
    vec3 ta = (-halfExt - o) / d;
    vec3 tb = (halfExt - o) / d;
    vec3 lo = min(ta, tb);
    vec3 hi = max(ta, tb);
    t0 = max(max(max(lo.x, lo.y), lo.z), 0.0);
    t1 = min(min(min(hi.x, hi.y), hi.z), sceneDist);
    return t1 > t0;
}

// Koordinat lokal titik sinar pada jarak t: x = lebar, y = tinggi, z = kedalaman (blok, dari pusat).
vec3 depVolLocal(vec3 rayDir, float t, vec3 center, vec3 tangent, vec3 normal) {
    vec3 p = rayDir * t - center;
    return vec3(dot(p, tangent), p.y, dot(p, normal));
}
