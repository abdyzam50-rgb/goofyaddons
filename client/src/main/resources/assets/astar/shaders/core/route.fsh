#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:oit.glsl>

// What each quad is comes in its texture coordinates (RouteShaders writes them):
//   x within -1..1: a ribbon. x is across it (edge to edge), y is along it in blocks, already
//     moved on by the time, so the arrows on it glide forward.
//   x = 10 + local x: a shape, y = 10 * kind + local y, local x and y within -1..1:
//     kind 1, a marker: a solid dot inside a ring, with a soft glow round it;
//     kind 2, the top of a block: a rounded square outline over a see-through fill;
//     kind 3, an arrowhead pointing to +y;
//     kind 4, a painted block top: a soft fill with a brighter rounded edge;
//     kind 5, a glowing orb;
//     kind 6, a chevron pointing to +y.
//   x within 19..21: a light tube. x - 20 is across it, y along it in blocks, moved on by the
//     time, so the light runs along it.
layout(location = 0) in vec2 shapeCoord;
layout(location = 1) in vec4 vertexColor;

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

const float ARROW_GAP = 1.6;

vec4 calculateFinalColor(vec4 color) {
    #ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
    #endif
    return color;
}

// Coverage of a shape edge at signed distance d (negative inside), smoothed over a pixel.
float cover(float d) {
    float w = max(fwidth(d), 1e-4);
    return clamp(0.5 - d / w, 0.0, 1.0);
}

float roundBox(vec2 p, vec2 size, float r) {
    vec2 q = abs(p) - size + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

vec4 ribbon(vec2 uv, vec3 base) {
    float x = abs(uv.x);
    float body = cover(x - 1.0);
    // Darker edges, and a little lighter down the middle.
    vec3 col = mix(base, base * 0.55, smoothstep(0.66, 0.74, x));
    col += (1.0 - x) * (1.0 - x) * 0.12;
    // Chevrons pointing the way to go: their tips lead, in the middle of the ribbon.
    float t = mod(uv.y, ARROW_GAP);
    float centre = 0.75 - 0.4 * x;
    float d = abs(t - centre) - 0.11;
    float chevron = cover(d) * (1.0 - smoothstep(0.5, 0.58, x));
    col = mix(col, vec3(1.0), chevron * 0.85);
    return vec4(col, body);
}

vec4 marker(vec2 p, vec3 base) {
    float r = length(p);
    float core = cover(r - 0.34);
    float ring = cover(abs(r - 0.62) - 0.08);
    float glow = exp(-r * r * 5.0) * 0.45 * (1.0 - cover(r - 0.7));
    vec3 col = mix(base, mix(base, vec3(1.0), 0.6), core);
    float a = max(max(core, ring), glow);
    return vec4(col, a * cover(r - 1.0));
}

vec4 blockTop(vec2 p, vec3 base) {
    float d = roundBox(p, vec2(0.94), 0.22);
    float outline = cover(abs(d) - 0.045);
    float fill = cover(d) * 0.3;
    return vec4(mix(base, mix(base, vec3(1.0), 0.35), outline), max(outline, fill));
}

vec4 arrowhead(vec2 p, vec3 base) {
    // A triangle with its point at (0, 1) and its base along y = -1.
    float side = (abs(p.x) * 2.0 + p.y - 1.0) / sqrt(5.0);
    float d = max(side, -p.y - 1.0);
    return vec4(base, cover(d));
}

vec4 paint(vec2 p, vec3 base) {
    float d = roundBox(p, vec2(0.96), 0.2);
    float edge = cover(abs(d + 0.06) - 0.05);
    float fill = cover(d) * 0.42;
    return vec4(mix(base, mix(base, vec3(1.0), 0.3), edge), max(edge * 0.85, fill));
}

vec4 orb(vec2 p, vec3 base) {
    float r = length(p);
    float core = cover(r - 0.42);
    float glow = exp(-r * r * 3.0) * (1.0 - cover(r - 1.0));
    vec3 col = mix(base, vec3(1.0), core * 0.55);
    return vec4(col, max(core, glow * 0.7));
}

vec4 chevron(vec2 p, vec3 base) {
    float d = abs(p.y - (0.35 - 0.75 * abs(p.x))) - 0.2;
    float a = cover(d) * cover(abs(p.x) - 0.85);
    return vec4(base, a);
}

vec4 tube(vec2 uv, vec3 base) {
    float x = abs(uv.x - 20.0);
    float core = exp(-x * x * 18.0);
    float glow = exp(-x * x * 3.5) * 0.55;
    // Light running along it: one bright stretch every 8 blocks.
    float t = mod(uv.y, 8.0);
    float run = exp(-(t - 4.0) * (t - 4.0) * 0.5);
    vec3 col = mix(base, vec3(1.0), clamp(core * 0.7 + run * 0.5, 0.0, 1.0));
    float a = clamp(max(core, glow) * (0.75 + 0.35 * run), 0.0, 1.0) * cover(x - 1.0);
    return vec4(col, a);
}

void main() {
    vec2 uv = shapeCoord;
    vec3 base = vertexColor.rgb;
    vec4 shape;
    if (uv.x < 5.0) {
        shape = ribbon(uv, base);
    } else if (uv.x > 15.0) {
        shape = tube(uv, base);
    } else {
        float kind = floor(uv.y / 10.0 + 0.5);
        vec2 p = vec2(uv.x - 10.0, uv.y - 10.0 * kind);
        if (kind < 1.5) {
            shape = marker(p, base);
        } else if (kind < 2.5) {
            shape = blockTop(p, base);
        } else if (kind < 3.5) {
            shape = arrowhead(p, base);
        } else if (kind < 4.5) {
            shape = paint(p, base);
        } else if (kind < 5.5) {
            shape = orb(p, base);
        } else {
            shape = chevron(p, base);
        }
    }
    vec4 color = vec4(shape.rgb, shape.a * vertexColor.a);
    if (color.a < 0.004) {
        discard;
    }

    color *= ColorModulator;

    #ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
    #else
    fragColor = calculateFinalColor(color);
    #endif
}
