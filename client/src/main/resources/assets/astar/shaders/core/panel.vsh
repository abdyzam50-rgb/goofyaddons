#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

// A rounded rectangle for the screens. UV0 is the pixel's place from the rectangle's middle,
// in GUI pixels; UV2 packs half its size with the corner radius (x: half width + 1024 * radius)
// and how soft its edge is (y: half height + 1024 * softness, 0 for a crisp edge).
layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in ivec2 UV2;
layout(location = 3) in vec4 Color;

layout(location = 0) out vec2 local;
layout(location = 1) out vec4 shape;
layout(location = 2) out vec4 vertexColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    local = UV0;
    shape = vec4(float(UV2.x % 1024), float(UV2.y % 1024), float(UV2.x / 1024), float(UV2.y / 1024));
    vertexColor = Color;
}
