#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 surfaceUV;
out vec4 surfaceShade;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    // UV0 is a model-local face projection, independent of camera/world transforms.
    surfaceUV = UV0;
    surfaceShade = Color;
}
