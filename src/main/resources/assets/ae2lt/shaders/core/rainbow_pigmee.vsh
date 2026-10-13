#version 330
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;



out vec2 surfaceUV;
out vec4 surfaceShade;
out vec2 modelDepthHeight;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    // UV0 is a model-local face projection, independent of camera/world transforms.
    surfaceUV = UV0;
    surfaceShade = Color;
    modelDepthHeight = vec2(UV2) / 4096.0;
}
