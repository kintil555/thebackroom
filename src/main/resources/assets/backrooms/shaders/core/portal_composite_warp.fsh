#version 330

// Komposit dunia seberang portal Magnet dengan distorsi di dalam render portalnya sendiri (menggantikan postfx layar penuh).
// Dipakai oleh PortalCompositeWarpMixin sebagai pengganti blit_screen.fsh milik Seamless Portals pada pass komposit yang di-stencil ke
// bentuk portal; jadi distorsi PASTI hanya ada di dalam portal dan tidak ada lingkaran/halo di luarnya.
// DataSampler = tekstur data yang sama dengan portal_distort.fsh (lihat komentar format di sana).
uniform sampler2D InSampler;
uniform sampler2D DataSampler;

in vec2 texCoord;

out vec4 fragColor;

const int MAX_SOURCES = 4;
const int ROW_GLOBAL = 4;
const float WOBBLE_PX = 7.0;
const float RING_PX = 6.0;
const float TEAR_PX = 30.0;
const float BAND_PX = 13.0;
const float CURVE = 0.14;

float decode16(vec2 hiLo) {
    return (hiLo.x * 255.0 * 256.0 + hiLo.y * 255.0) / 65535.0;
}

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

void main() {
    vec2 size = vec2(textureSize(InSampler, 0));
    vec2 pix = vec2(texCoord.x, 1.0 - texCoord.y) * size;
    float scale = size.y / 1080.0;
    float time = decode16(texelFetch(DataSampler, ivec2(0, ROW_GLOBAL), 0).rg) * 64.0;
    vec2 disp = vec2(0.0);

    for (int i = 0; i < MAX_SOURCES; i++) {
        vec4 t3 = texelFetch(DataSampler, ivec2(3, i), 0);
        vec4 t4 = texelFetch(DataSampler, ivec2(4, i), 0);
        float strength = decode16(t3.rg);
        float close = decode16(t4.rg);
        // Animasi menutup masih ditangani postfx; di sini hanya lengkungan permanen portal terbuka.
        if (strength < 0.003 || close > 0.001) {
            continue;
        }
        float seed = floor(t3.b * 255.0 + 0.5);
        vec4 t0 = texelFetch(DataSampler, ivec2(0, i), 0);
        vec4 t1 = texelFetch(DataSampler, ivec2(1, i), 0);
        vec4 t2 = texelFetch(DataSampler, ivec2(2, i), 0);
        vec2 center = (vec2(decode16(t0.rg), decode16(t0.ba)) * 2.0 - 0.5) * size;
        vec2 axisR = (vec2(decode16(t1.rg), decode16(t1.ba)) * 4.0 - 2.0) * size;
        vec2 axisU = (vec2(decode16(t2.rg), decode16(t2.ba)) * 4.0 - 2.0) * size;
        float det = axisR.x * axisU.y - axisR.y * axisU.x;
        if (abs(det) < 1.0) {
            continue;
        }
        vec2 d = pix - center;
        vec2 ld = vec2(d.x * axisU.y - d.y * axisU.x, axisR.x * d.y - axisR.y * d.x) / det;
        float box = max(abs(ld.x), abs(ld.y));
        // Efek menghilang mulus menuju tepi portal, jadi tidak pernah ada tepi tajam.
        float mask = 1.0 - smoothstep(0.55, 1.0, box);
        if (mask <= 0.001) {
            continue;
        }
        float amount = mask * strength;
        float radius = length(ld);
        float angle = atan(ld.y, ld.x);
        float ring = sin(radius * 9.0 - time * 5.0 + seed);
        float arcs = sin(angle * 3.0 + radius * 2.0 + time * 1.7 + seed * 1.3);
        vec2 wobble = vec2(sin(pix.y / scale * 0.045 + time * 6.0 + seed),
                           cos(pix.x / scale * 0.035 - time * 4.5 + seed * 2.0));
        float band = floor(pix.y / (BAND_PX * scale));
        float tick = floor(time * 14.0);
        float threshold = mix(0.93, 0.55, smoothstep(0.35, 1.0, strength));
        float tear = hash21(vec2(band + seed, tick)) > threshold ? (hash21(vec2(band + 17.0, tick + seed)) - 0.5) * 2.0 : 0.0;
        float pulse = 0.8 + 0.2 * sin(time * 2.0 + seed);
        float bend = CURVE * strength * pulse;
        vec2 bendDelta = ld * bend * (1.0 - min(dot(ld, ld), 2.0) * 0.35);

        disp += amount * scale * (wobble * WOBBLE_PX + vec2(ring, arcs) * RING_PX + vec2(tear * TEAR_PX, 0.0));
        disp += mask * (axisR * bendDelta.x + axisU * bendDelta.y);
    }

    vec2 p = clamp(pix + disp, vec2(0.5), size - 0.5);
    fragColor = texture(InSampler, vec2(p.x / size.x, 1.0 - p.y / size.y));
}
