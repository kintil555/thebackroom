#version 330

// Overlay flashbang di atas hasil portal_glow.fsh (input In). Dipisah dari pass utama agar putih dan bayangan sisa
// tidak ikut tersimpan ke history (kalau ikut, tiap frame akan menumpuk dan layar menghitam/memutih terus).
// Data dari DataSampler (tekstur yang sama dengan portal_glow.fsh):
//   baris 4, texel 2: b = lapisan putih 0..1   a = kekuatan bayangan sisa 0..1
//   baris 5: texel 0 r,g = x   b,a = y (posisi layar tetap, 16 bit, (nilai + 0.5) / 2)   texel 1 r,g = radius/2 (fraksi tinggi layar)
uniform sampler2D InSampler;
uniform sampler2D DataSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
    vec2 DataSize;
};

in vec2 texCoord;

out vec4 fragColor;

const int ROW_CAMERA = 4;
const int ROW_FLASH = 5;
// Bayangan sisa gelap keunguan di tempat bloom tadi terlihat.
const vec3 AFTERIMAGE_TINT = vec3(0.22, 0.10, 0.28);

float decode16(vec2 hiLo) {
    return (floor(hiLo.x * 255.0 + 0.5) * 256.0 + floor(hiLo.y * 255.0 + 0.5)) / 65535.0;
}

void main() {
    vec3 result = texture(InSampler, texCoord).rgb;
    vec4 expData = texelFetch(DataSampler, ivec2(2, ROW_CAMERA), 0);
    float flash = expData.b;
    float ghost = expData.a;

    if (ghost > 0.001) {
        vec2 pixel = vec2(texCoord.x, 1.0 - texCoord.y) * OutSize;
        vec4 ghostPos = texelFetch(DataSampler, ivec2(0, ROW_FLASH), 0);
        vec4 ghostSize = texelFetch(DataSampler, ivec2(1, ROW_FLASH), 0);
        float ghostRadius = decode16(ghostSize.rg) * 2.0 * OutSize.y;
        if (ghostRadius > 1.0) {
            vec2 ghostCenter = (vec2(decode16(ghostPos.rg), decode16(ghostPos.ba)) * 2.0 - 0.5) * OutSize;
            float blob = 1.0 - smoothstep(0.0, 1.0, length((pixel - ghostCenter) / ghostRadius));
            result = mix(result, result * AFTERIMAGE_TINT, clamp(ghost * blob * 0.9, 0.0, 1.0));
        }
        result *= 1.0 - 0.25 * ghost;
    }
    fragColor = vec4(mix(result, vec3(1.0), flash), 1.0);
}
