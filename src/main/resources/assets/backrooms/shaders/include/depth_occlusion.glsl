// API oklusi depth buffer per piksel untuk post effect berbasis sumber. Pasangan Java: DepthOcclusion (tata letak data ada di sana).
// Pakai: #moj_import <backrooms:depth_occlusion.glsl>
//   float open = depthOcclusionOpenness(DepthSampler, DepthSize, DataSampler, FIRST_ROW, sourceIndex, texCoord);
// 0 = sumber tertutup penuh di piksel ini, 1 = terlihat penuh. Kalikan kontribusi sumber dengan nilai ini.
// DepthSampler = depth buffer main (TargetInput use_depth_buffer), DataSampler = tekstur data efek.

float depOccFetchFloat(sampler2D dataTex, int x, int y) {
    vec4 c = texelFetch(dataTex, ivec2(x, y), 0);
    uvec4 b = uvec4(c * 255.0 + 0.5);
    return uintBitsToFloat(b.r | (b.g << 8u) | (b.b << 16u) | (b.a << 24u));
}

vec3 depOccFetchVec3(sampler2D dataTex, int x, int y) {
    return vec3(depOccFetchFloat(dataTex, x, y), depOccFetchFloat(dataTex, x + 1, y), depOccFetchFloat(dataTex, x + 2, y));
}

mat4 depOccFetchMatrix(sampler2D dataTex, int row) {
    return mat4(
        depOccFetchFloat(dataTex, 0, row), depOccFetchFloat(dataTex, 1, row), depOccFetchFloat(dataTex, 2, row), depOccFetchFloat(dataTex, 3, row),
        depOccFetchFloat(dataTex, 4, row), depOccFetchFloat(dataTex, 5, row), depOccFetchFloat(dataTex, 6, row), depOccFetchFloat(dataTex, 7, row),
        depOccFetchFloat(dataTex, 8, row), depOccFetchFloat(dataTex, 9, row), depOccFetchFloat(dataTex, 10, row), depOccFetchFloat(dataTex, 11, row),
        depOccFetchFloat(dataTex, 12, row), depOccFetchFloat(dataTex, 13, row), depOccFetchFloat(dataTex, 14, row), depOccFetchFloat(dataTex, 15, row));
}

// 1 = piksel ini tidak tertutup (sumber terlihat), 0 = permukaan di piksel ini menutupi sumber. Ukuran/kecerahan efek tidak berubah.
// Tepi lunak bidang: lebar peralihan dari aturan bidang ke aturan titik. Harus lebar agar batasnya tidak terlihat sebagai cincin.
const float DEP_OCC_RECT_MARGIN = 1.8;

float depOccOpenAt(sampler2D depthTex, vec2 depthSize, mat4 inverseMatrix, bool zeroToOne, float nearD, float farD, float planeThickness,
                   vec3 center, vec3 normal, float radius, float halfHeight, vec2 uv) {
    vec2 clamped = clamp(uv, vec2(0.0), vec2(0.9999));
    float depth = texelFetch(depthTex, ivec2(clamped * depthSize), 0).r;
    if (depth <= 0.00001) {
        return 1.0; // langit (depth reverse-Z: langit = 0, dekat = 1): tidak ada yang menutupi
    }
    vec2 ndc = clamped * 2.0 - 1.0;
    float ndcZ = zeroToOne ? depth : depth * 2.0 - 1.0;
    vec4 world = inverseMatrix * vec4(ndc, ndcZ, 1.0);
    if (abs(world.w) <= 1.0e-8) {
        return 1.0;
    }
    vec3 surface = world.xyz / world.w; // permukaan terlihat, relatif kamera

    // Aturan titik: permukaan lebih dekat ke kamera daripada sumber = menutupi.
    float centerDistance = length(center);
    float openPoint = smoothstep(centerDistance - farD, centerDistance - nearD, length(surface));
    if (radius <= 0.0) {
        return openPoint;
    }

    // Aturan bidang: sinar piksel menembus bidang di dalam radius -> yang menutupi hanya permukaan di depan bidang (melebihi ketebalan).
    vec3 rayDir = normalize(surface);
    float facing = dot(normal, rayDir);
    if (abs(facing) < 1.0e-3) {
        return openPoint;
    }
    float t = dot(normal, center) / facing;
    if (t <= 0.0) {
        return openPoint;
    }
    vec3 hit = rayDir * t - center;
    float planeWeight;
    if (halfHeight > 0.0) {
        // Persegi panjang: lebar searah cross(Y, normal), tinggi searah Y. Bobot kontinu, tanpa tepi tajam.
        vec3 tangent = normalize(cross(vec3(0.0, 1.0, 0.0), normal));
        planeWeight = (1.0 - smoothstep(radius, radius + DEP_OCC_RECT_MARGIN, abs(dot(hit, tangent))))
            * (1.0 - smoothstep(halfHeight, halfHeight + DEP_OCC_RECT_MARGIN, abs(hit.y)));
    } else {
        planeWeight = 1.0 - smoothstep(radius, radius + max(0.5, radius * 0.8), length(hit));
    }
    float side = dot(normal, center) > 0.0 ? -1.0 : 1.0; // sisi kamera = positif
    float height = dot(surface - center, normal) * side;
    float openPlane = 1.0 - smoothstep(planeThickness, planeThickness + (farD - nearD), height);
    return mix(openPoint, openPlane, planeWeight);
}

// 5 sampel (tengah berbobot 2) menghaluskan tepi siluet blok.
float depthOcclusionOpenness(sampler2D depthTex, vec2 depthSize, sampler2D dataTex, int firstRow, int sourceIndex, vec2 uv) {
    mat4 inverseMatrix = depOccFetchMatrix(dataTex, firstRow);
    int header = firstRow + 1;
    bool zeroToOne = depOccFetchFloat(dataTex, 0, header) > 0.5;
    float nearD = depOccFetchFloat(dataTex, 1, header);
    float farD = depOccFetchFloat(dataTex, 2, header);
    float planeThickness = depOccFetchFloat(dataTex, 3, header);
    int row = firstRow + 2 + sourceIndex;
    vec3 center = depOccFetchVec3(dataTex, 0, row);
    vec3 normal = depOccFetchVec3(dataTex, 3, row);
    float radius = depOccFetchFloat(dataTex, 6, row);
    float halfHeight = depOccFetchFloat(dataTex, 7, row);
    vec2 texel = 1.5 / depthSize;
    float sum = 2.0 * depOccOpenAt(depthTex, depthSize, inverseMatrix, zeroToOne, nearD, farD, planeThickness, center, normal, radius, halfHeight, uv);
    sum += depOccOpenAt(depthTex, depthSize, inverseMatrix, zeroToOne, nearD, farD, planeThickness, center, normal, radius, halfHeight, uv + vec2(texel.x, 0.0));
    sum += depOccOpenAt(depthTex, depthSize, inverseMatrix, zeroToOne, nearD, farD, planeThickness, center, normal, radius, halfHeight, uv - vec2(texel.x, 0.0));
    sum += depOccOpenAt(depthTex, depthSize, inverseMatrix, zeroToOne, nearD, farD, planeThickness, center, normal, radius, halfHeight, uv + vec2(0.0, texel.y));
    sum += depOccOpenAt(depthTex, depthSize, inverseMatrix, zeroToOne, nearD, farD, planeThickness, center, normal, radius, halfHeight, uv - vec2(0.0, texel.y));
    return sum / 6.0;
}
