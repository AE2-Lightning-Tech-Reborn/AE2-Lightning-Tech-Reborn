#version 330

in vec3 Position;
in vec4 Color;
in vec3 Normal;

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:globals.glsl>
#define EffectTime (GameTime * 1200.0)

out vec4 vertexColor;
out vec3 effectNormal;
out vec3 effectPosition;
out vec3 viewNormal;
out float pulse;

void main() {
    vec3 normal = normalize(Normal);
    float phase = dot(normal, vec3(5.3, 7.1, 9.7)) + EffectTime * 2.2;
    pulse = 0.5 + 0.5 * sin(phase);

    float displacement = sin(phase * 1.7) * 0.012 * Color.a;
    vec3 displacedPosition = Position + normal * displacement;
    gl_Position = ProjMat * ModelViewMat * vec4(displacedPosition, 1.0);

    vertexColor = Color;
    effectNormal = normal;
    effectPosition = Position;
    viewNormal = normalize(mat3(ModelViewMat) * normal);
}
