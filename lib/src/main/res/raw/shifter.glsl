#version 310 es

precision highp float;

layout (local_size_x = 128) in;
layout (std430) buffer;

layout (binding = 0) readonly buffer Taps { float taps[]; };
layout (binding = 1) buffer Input { float inBuffer[]; };

layout (location = 0) uniform int offset;
layout (location = 1) uniform float phi;
layout (location = 2) uniform float omega;

#define M_2PI 6.283185307179586

void main() {
    int i = int(gl_GlobalInvocationID.x);

    int reIndex = 2 * (offset + i) + 0;
    int imIndex = 2 * (offset + i) + 1;

    float re = inBuffer[reIndex];
    float im = inBuffer[imIndex];

    float rotation = mod(phi + omega * float(i), M_2PI);

    float cosA = cos(rotation);
    float sinA = sin(rotation);

    inBuffer[reIndex] = re * cosA - im * sinA;
    inBuffer[imIndex] = re * sinA + im * cosA;
}
