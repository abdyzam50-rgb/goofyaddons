#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>

layout(location = 0) in vec2 local;
layout(location = 1) in vec4 shape;
layout(location = 2) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

void main() {
    vec2 size = shape.xy;
    float radius = min(shape.z, min(size.x, size.y));
    float soft = shape.w;
    vec2 q = abs(local) - size + radius;
    float d = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
    float a;
    if (soft > 0.0) {
        // A shadow: fades out over the softness, outside the edge.
        a = 1.0 - smoothstep(-soft * 0.3, soft, d);
        a *= a;
    } else {
        float w = max(fwidth(d), 1e-4);
        a = clamp(0.5 - d / w, 0.0, 1.0);
    }
    vec4 color = vec4(vertexColor.rgb, vertexColor.a * a);
    if (color.a < 0.004) {
        discard;
    }
    fragColor = color * ColorModulator;
}
