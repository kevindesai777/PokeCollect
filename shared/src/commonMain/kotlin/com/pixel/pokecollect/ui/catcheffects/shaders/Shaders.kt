package com.pixel.pokecollect.ui.catcheffects.shaders

/*
 * The effect shaders, one source each for Android (AGSL) and iOS (SkSL). They keep to what both
 * languages share:
 * - `half4 main(float2 p)`, where p is in dp with the origin at the card's top left and y down;
 * - float2/float3/float4 (no vec*);
 * - loops with constant bounds;
 * - no derivatives. There is no fwidth, so an ink outline needs the distance to an edge, which is
 *   the field divided by its gradient, and the gradient is measured with one-pixel differences.
 *
 * They are ports of the WebGL mockup in docs/research/effects. Two differences: they work in dp
 * rather than device pixels, and they flip y, because GLSL's gl_FragCoord starts at the bottom left.
 * Psychic's content filter is the exception: a filter gets the layer's pixels, so it converts.
 */

/** Value noise, shared by the painting shaders and the content filter. */
private const val NOISE = """
float hash1(float2 p) {
    float2 q = fract(p * float2(123.34, 456.21));
    q += dot(q, q + 45.32);
    return fract(q.x * q.y);
}

float vnoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash1(i), hash1(i + float2(1.0, 0.0)), u.x),
               mix(hash1(i + float2(0.0, 1.0)), hash1(i + float2(1.0, 1.0)), u.x), u.y);
}
"""

/** Uniforms and helpers every painting shader starts with (ShaderLayer sets the uniforms). */
private const val COMMON = """
uniform float2 u_size;   // the card, dp
uniform float u_px;      // dp per device pixel: one pixel of antialiasing
uniform float u_t;       // progress through the effect, 0..1
uniform float u_time;    // seconds, offset per catch so two cards never match
uniform float u_sec;     // seconds since the tap
uniform float u_focus;   // x of the dex circle's centre, dp
uniform float u_tau;     // seconds since the circle filled, negative before

const float3 INK = float3(0.0);
""" + NOISE + """
float fbm(float2 p) {
    float v = 0.0;
    float a = 0.5;
    float2 q = p;
    for (int i = 0; i < 4; i++) {
        v += a * vnoise(q);
        q = q * 2.03 + float2(17.0, 9.0);
        a *= 0.5;
    }
    return v;
}

// How much of this pixel a shape covers, given the distance d (dp) to its edge, positive inside.
float cover(float d) { return clamp(d / u_px + 0.5, 0.0, 1.0); }

float4 over(float4 top, float4 under) { return top + under * (1.0 - top.a); }
"""

/** Fire: flowing flame tongues in three flat layers (red, orange, yellow) with an ink edge, and embers. */
internal const val FireShader = COMMON + """
const float3 RED = float3(0.992, 0.455, 0.400);     // fighting #FD7466
const float3 ORANGE = float3(0.996, 0.565, 0.322);  // fire #FE9052
const float3 YELLOW = float3(1.0, 0.847, 0.169);    // electric #FFD82B

float envelope(float t) { return smoothstep(0.0, 0.3, t) * (1.0 - smoothstep(0.55, 1.0, t)); }

// 1 at the base, 0 on the flame's edge, negative outside. q is y-up, dp.
float field(float2 q) {
    float H = u_size.y;
    float2 n = q / H;
    float fx = (q.x - u_focus) / (H * 1.6);
    float h = envelope(u_t) * (0.75 + 0.6 * exp(-fx * fx));
    // The higher a tongue reaches, the more it sways: the tips flow instead of standing still.
    float sway = 0.2 * n.y * sin(n.y * 3.2 - u_time * 5.0 + n.x * 1.3);
    float x = n.x + sway;
    float rough = 0.65 * vnoise(float2(x * 2.2, n.y * 0.8 - u_time * 2.2)) + 0.35 * vnoise(float2(x * 4.6, n.y * 1.7 - u_time * 3.1));
    float wave = 0.5 + 0.5 * sin(x * 5.2 + (rough - 0.5) * 3.0 + u_time * 1.4);
    // Rounded tips, pinched notches between them.
    float tongue = 1.0 - (1.0 - wave) * (1.0 - wave);
    float top = 0.55 + 0.65 * tongue + (rough - 0.5) * 0.35;
    return 1.0 - n.y / (max(h, 0.02) * top);
}

half4 main(float2 p) {
    float H = u_size.y;
    float2 q = float2(p.x, H - p.y);
    float f = field(q);
    // No fwidth: measure the gradient with one-pixel differences.
    float2 g = float2(field(q + float2(u_px, 0.0)) - f, field(q + float2(0.0, u_px)) - f) / u_px;
    float gm = max(length(g), 0.0001);
    float d0 = f / gm;
    float d1 = (f - 0.42) / gm;
    float d2 = (f - 0.74) / gm;
    float a = cover(d0) * smoothstep(0.0, 0.05, envelope(u_t));
    float3 c = mix(INK, RED, cover(d0 - 2.0));
    c = mix(c, ORANGE, cover(d1));
    c = mix(c, YELLOW, cover(d2));
    float4 col = float4(c * a, a);
    for (int i = 0; i < 8; i++) {
        float fi = float(i);
        float born = 0.12 + 0.4 * hash1(float2(fi, 3.0));
        float life = 0.35 + 0.3 * hash1(float2(fi, 4.0));
        float age = u_sec - born;
        if (age > 0.0 && age < life) {
            float k = 1.0 - age / life;
            float2 e = float2(u_focus + (hash1(float2(fi, 5.0)) - 0.5) * H * 3.0 + sin(age * 9.0 + fi) * 5.0, H * (0.45 + 2.8 * age));
            float r = (2.0 + 2.0 * hash1(float2(fi, 6.0))) * (0.4 + 0.6 * k);
            float dd = r - length(q - e);
            float ea = cover(dd);
            float3 ec = mix(INK, YELLOW, cover(dd - 1.5));
            col = over(float4(ec * ea, ea), col);
        }
    }
    return half4(col);
}
"""

/** Ghost: purple wisps with cream hearts drift up through the card. */
internal const val GhostShader = COMMON + """
const float3 GHOST = float3(0.690, 0.561, 0.949);  // ghost #B08FF2
const float3 GLOW = float3(1.0, 0.992, 0.949);     // cream #FFFDF2

// The wisps live in a band that drifts up through the card; positive inside. q is y-up, dp.
float field(float2 q) {
    float H = u_size.y;
    float2 n = q / H;
    float z = (n.y - mix(-0.7, 2.0, u_t)) / 0.85;
    float band = exp(-z * z);
    float2 w = float2(n.x * 1.5 + 0.4 * sin(n.y * 2.5 + u_time * 2.2), n.y * 0.8 - u_time * 0.7);
    return fbm(w) * (0.5 + band) - 0.6;
}

half4 main(float2 p) {
    float2 q = float2(p.x, u_size.y - p.y);
    float f = field(q);
    float2 g = float2(field(q + float2(u_px, 0.0)) - f, field(q + float2(0.0, u_px)) - f) / u_px;
    float gm = max(length(g), 0.0001);
    float d0 = f / gm;
    float d1 = (f - 0.2) / gm;
    float a = cover(d0);
    float3 c = mix(INK, GHOST, cover(d0 - 2.0));
    c = mix(c, GLOW, cover(d1));
    return half4(float4(c * a, a));
}
"""

/** Water: the water line rises past the circle with bubbles, splashes as the circle fills, and drains. */
internal const val WaterShader = COMMON + """
const float3 BODY = float3(0.388, 0.725, 0.996);  // water #63B9FE
const float3 FOAM = float3(0.478, 0.922, 0.996);  // ice #7AEBFE

float level(float t) { return mix(-0.1, 0.78, smoothstep(0.0, 0.42, t)) - smoothstep(0.6, 1.0, t); }

// Height of the water line at x, dp up from the card's bottom.
float surface(float x) {
    float H = u_size.y;
    float k = x / H;
    float a = 0.05 + 0.07 * sin(clamp(u_t, 0.0, 1.0) * 3.14159);
    return (level(u_t) + a * sin(k * 3.4 - u_time * 6.0) + a * 0.5 * sin(k * 7.3 + u_time * 4.1 + 1.7)) * H;
}

half4 main(float2 p) {
    float H = u_size.y;
    float2 q = float2(p.x, H - p.y);
    float s = surface(q.x);
    float slope = (surface(q.x + u_px) - s) / u_px;
    float d = (s - q.y) / sqrt(1.0 + slope * slope);
    // The water stays inside the card; only the droplets may leave it.
    float a = cover(d) * cover(H - q.y);
    float3 c = mix(INK, FOAM, cover(d - 2.0));
    c = mix(c, BODY, cover(d - 8.0));
    for (int i = 0; i < 8; i++) {
        float fi = float(i);
        float r = 2.5 + 2.5 * hash1(float2(fi, 5.5));
        float speed = (0.6 + hash1(float2(fi, 7.7))) * H * 0.8;
        float by = mod(u_time * speed + hash1(float2(fi, 1.3)) * H, H * 1.2) - 0.1 * H;
        float bx = hash1(float2(fi, 3.1)) * u_size.x + sin(u_time * 4.0 + fi) * 3.0;
        float bd = r - length(q - float2(bx, by));
        float under = step(by + r + 4.0, s);
        c = mix(c, INK, cover(bd) * under);
        c = mix(c, FOAM, cover(bd - 1.5) * under);
    }
    float4 col = float4(c * a, a);
    if (u_tau > 0.0) {
        for (int i = 0; i < 9; i++) {
            float fi = float(i);
            float vx = (hash1(float2(fi, 4.4)) - 0.5) * H * 2.2;
            float vy = (1.4 + hash1(float2(fi, 6.6))) * H * 1.5;
            float x = u_focus + (hash1(float2(fi, 2.2)) - 0.5) * H * 1.2 + vx * u_tau;
            float y = 0.7 * H + vy * u_tau - 5.0 * H * u_tau * u_tau;
            float r = 3.0 + 2.0 * hash1(float2(fi, 8.8));
            float dd = r - length(q - float2(x, y));
            float da = cover(dd) * step(surface(x), y);
            float3 dc = mix(INK, FOAM, cover(dd - 1.5));
            col = over(float4(dc * da, da), col);
        }
    }
    return half4(col);
}
"""

/** Ice: frost grows in from the card's edges as ice shards, then every shard shrinks away. */
internal const val IceShader = COMMON + """
const float3 ICE = float3(0.478, 0.922, 0.996);    // ice #7AEBFE
const float3 STEEL = float3(0.675, 0.820, 0.894);  // steel #ACD1E4
const float3 SHINE = float3(1.0);
const float CELL = 20.0;                            // shard size, dp

float2 hash2(float2 p) { return float2(hash1(p), hash1(p + 19.19)); }

half4 main(float2 p) {
    float H = u_size.y;
    float2 q = float2(p.x, H - p.y);
    float2 gp = q / CELL;
    float2 ip = floor(gp);
    float2 fp = fract(gp);
    // Voronoi shards: the nearest site, then the distance to that shard's border.
    float best = 8.0;
    float2 site = float2(0.0);
    float2 id = float2(0.0);
    for (int j = -1; j <= 1; j++) {
        for (int i = -1; i <= 1; i++) {
            float2 o = float2(float(i), float(j));
            float2 s = o + hash2(ip + o);
            float d = length(s - fp);
            if (d < best) { best = d; site = s; id = ip + o; }
        }
    }
    float border = 8.0;
    for (int j = -2; j <= 2; j++) {
        for (int i = -2; i <= 2; i++) {
            float2 o = float2(float(i), float(j));
            float2 s = o + hash2(ip + o);
            float2 dv = s - site;
            if (dot(dv, dv) > 0.0001) { border = min(border, dot(0.5 * (site + s) - fp, normalize(dv))); }
        }
    }
    border *= CELL;
    // A shard freezes once the frost line from the nearest card edge reaches its site.
    float2 sp = (id + hash2(id)) * CELL;
    float edge = min(min(sp.x, u_size.x - sp.x), min(sp.y, H - sp.y));
    float h = hash1(id + 7.0);
    float frontier = smoothstep(0.0, 0.42, u_t) * H * 0.75 * (0.7 + 0.6 * h);
    float frozen = step(edge, frontier);
    // After the circle fills, every shard shrinks to nothing at its own pace.
    float shrink = smoothstep(0.5, 1.0, u_t) * CELL * (1.0 + 0.6 * h);
    float d = border - shrink;
    float a = cover(d) * frozen * cover(H - q.y);
    float3 fill = h < 0.55 ? ICE : (h < 0.88 ? STEEL : SHINE);
    float3 c = mix(INK, fill, cover(d - 1.5));
    return half4(float4(c * a, a));
}
"""

/**
 * Psychic: bends the card's content, strongest halfway through. A port of the mockup's SVG filter
 * (feTurbulence, fractal noise, two octaves, into feDisplacementMap): two noise fields push each
 * pixel sideways and up or down by up to half of 18 dp. The bands are wide and flat (0.012 per dp
 * across, 0.05 down), and the vertical frequency wobbles, so the bend shimmers.
 *
 * A content filter, not a painting shader: `p` is in the layer's pixels, and `content` is the card's
 * content as drawn. ContentWarp sets the uniforms.
 */
internal const val PsychicShader = """
uniform shader content;  // the card's content, as drawn
uniform float u_dp;      // device pixels per dp
uniform float u_t;       // progress through the effect, 0..1
uniform float u_sec;     // seconds since the tap
uniform float u_seed;    // moves the noise per catch
""" + NOISE + """
// Two octaves of value noise, about 0..1, like feTurbulence's fractal noise.
float turbulence(float2 n) {
    return (2.0 * vnoise(n) + vnoise(n * 2.0 + float2(17.0, 9.0))) / 3.0;
}

half4 main(float2 p) {
    float2 q = p / u_dp;
    float2 n = q * float2(0.012, 0.05 + 0.015 * sin(u_sec * 9.09)) + u_seed;
    float2 c = float2(turbulence(n), turbulence(n + float2(41.0, 13.0)));
    float amount = 18.0 * sin(3.14159265 * u_t);
    return content.eval(p + amount * (c - 0.5) * u_dp);
}
"""

/** The shaders that paint the card (through ShaderLayer), for the compile tests. */
internal val BrushShaders = mapOf("fire" to FireShader, "ghost" to GhostShader, "water" to WaterShader, "ice" to IceShader)

/** The shaders that filter the card's content (through ContentWarp), for the compile tests. */
internal val ContentShaders = mapOf("psychic" to PsychicShader)
