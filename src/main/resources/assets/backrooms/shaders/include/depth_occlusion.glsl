// API oklusi depth buffer per piksel untuk post effect sumber titik. Pasangan Java: DepthOcclusion.
// Pakai: #moj_import <backrooms:depth_occlusion.glsl>
//   float open = depthOcclusionOpenness(DepthSampler, DepthSize, DataSampler, FIRST_ROW, sourceIndex, texCoord);
// 0 = sumber tertutup penuh di piksel ini, 1 = terlihat penuh. Kalikan kontribusi sumber dengan nilai ini.
// DepthSampler = depth buffer main (TargetInput use_depth_buffer), DataSampler = tekstur data efek (tata letak di DepthOcclusion.java).

float depOccFetchFloat(sampler2D dataTex, int x, int y) {
    vec4 c = texelFetch(dataTex, ivec2(x, y), 0);
    uvec4 b = uvec4(c * 255.0 + 0.5);
    return uintBitsToFloat(b.r | (b.g << 8u) | (b.b << 16u) | (b.a << 24u));
}

mat4 depOccFetchMatrix(sampler2D dataTex, int row) {
    return mat4(
        depOccFetchFloat(dataTex, 0, row), depOccFetchFloat(dataTex, 1, row), depOccFetchFloat(dataTex, 2, row), depOccFetchFloat(dataTex, 3, row),
        depOccFetchFloat(dataTex, 4, row), depOccFetchFloat(dataTex, 5, row), depOccFetchFloat(dataTex, 6, row), depOccFetchFloat(dataTex, 7, row),
        depOccFetchFloat(dataTex, 8, row), depOccFetchFloat(dataTex, 9, row), depOccFetchFloat(dataTex, 10, row), depOccFetchFloat(dataTex, 11, row),
        depOccFetchFloat(dataTex, 12, row), depOccFetchFloat(dataTex, 13, row), depOccFetchFloat(dataTex, 14, row), depOccFetchFloat(dataTex, 15, row));
}

// Jarak kamera ke permukaan terlihat pada koordinat layar uv (langit = sangat jauh).
float depOccSceneDistance(sampler2D depthTex, vec2 depthSize, mat4 inverseMatrix, bool zeroToOne, vec2 uv) {
    vec2 clamped = clamp(uv, vec2(0.0), vec2(0.9999));
    float depth = texelFetch(depthTex, ivec2(clamped * depthSize), 0).r;
    if (depth >= 0.99999) {
        return 1.0e6;
    }
    vec2 ndc = clamped * 2.0 - 1.0;
    float ndcZ = zeroToOne ? depth : depth * 2.0 - 1.0;
    vec4 world = inverseMatrix * vec4(ndc, ndcZ, 1.0);
    return abs(world.w) > 1.0e-8 ? length(world.xyz / world.w) : 1.0e6;
}

float depOccOpenAt(sampler2D depthTex, vec2 depthSize, mat4 inverseMatrix, bool zeroToOne, float nearD, float farD, float sourceDistance, vec2 uv) {
    return smoothstep(sourceDistance - farD, sourceDistance - nearD, depOccSceneDistance(depthTex, depthSize, inverseMatrix, zeroToOne, uv));
}

// 5 sampel (tengah berbobot 2) menghaluskan tepi siluet blok.
float depthOcclusionOpenness(sampler2D depthTex, vec2 depthSize, sampler2D dataTex, int firstRow, int sourceIndex, vec2 uv) {
    mat4 inverseMatrix = depOccFetchMatrix(dataTex, firstRow);
    int row = firstRow + 1;
    bool zeroToOne = depOccFetchFloat(dataTex, 0, row) > 0.5;
    float nearD = depOccFetchFloat(dataTex, 1, row);
    float farD = depOccFetchFloat(dataTex, 2, row);
    float sourceDistance = depOccFetchFloat(dataTex, 3 + sourceIndex, row);
    vec2 texel = 1.5 / depthSize;
    float sum = 2.0 * depOccOpenAt(depthTex, depthSize, inverseMatrix, zeroToOne, nearD, farD, sourceDistance, uv);
    sum += depOccOpenAt(depthTex, depthSize, inverseMatrix, zeroToOne, nearD, farD, sourceDistance, uv + vec2(texel.x, 0.0));
    sum += depOccOpenAt(depthTex, depthSize, inverseMatrix, zeroToOne, nearD, farD, sourceDistance, uv - vec2(texel.x, 0.0));
    sum += depOccOpenAt(depthTex, depthSize, inverseMatrix, zeroToOne, nearD, farD, sourceDistance, uv + vec2(0.0, texel.y));
    sum += depOccOpenAt(depthTex, depthSize, inverseMatrix, zeroToOne, nearD, farD, sourceDistance, uv - vec2(0.0, texel.y));
    return sum / 6.0;
}
