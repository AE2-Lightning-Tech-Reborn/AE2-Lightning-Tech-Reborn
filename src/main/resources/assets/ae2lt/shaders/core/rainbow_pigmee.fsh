#version 150

uniform float AnimationTicks;

in vec2 surfaceUV;
in vec4 surfaceShade;

out vec4 fragColor;

void main() {
    // Quantize space, not time: crisp texels with a smoothly drifting rainbow.
    vec2 cell = floor(surfaceUV * 16.0);
    float phase = fract((dot(cell + 0.5, vec2(3.0, 8.0)) + AnimationTicks) / 160.0);
    vec3 hue = clamp(abs(fract(phase + vec3(0.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0) - 1.0, 0.0, 1.0);
    vec3 rainbow = mix(vec3(1.0), hue, 0.72);
    // A small, fixed ordered texture avoids a glossy finish or animated noise.
    float grain = mod(cell.x + cell.y * 2.0, 4.0);
    float textureShade = 0.94 + grain * 0.02;
    fragColor = vec4(rainbow * textureShade * surfaceShade.rgb, surfaceShade.a);
}
