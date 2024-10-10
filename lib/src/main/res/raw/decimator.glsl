#version 310 es

precision highp float;

layout (local_size_x = 128) in;
layout (std430) buffer;

layout (binding = 0) readonly buffer Taps { float taps[]; };
layout (binding = 1) readonly buffer Input { float inBuffer[]; };
layout (binding = 2) writeonly buffer Output { float outBuffer[]; };

layout (location = 0) uniform int offset;

void main() {
    int i = int(gl_GlobalInvocationID.x) * 2;

    int middle = taps.length() / 2;

    float re = inBuffer[2 * (i + middle) + 0] * taps[middle];
    float im = inBuffer[2 * (i + middle) + 1] * taps[middle];

    int j = 1;

    while (j < middle) {
        re += inBuffer[2 * (i + middle + j) + 0] * taps[middle + j] +
              inBuffer[2 * (i + middle - j) + 0] * taps[middle - j];
        im += inBuffer[2 * (i + middle + j) + 1] * taps[middle + j] +
              inBuffer[2 * (i + middle - j) + 1] * taps[middle - j];

        j += 2;
    }

    outBuffer[i + 2 * offset + 0] = re;
    outBuffer[i + 2 * offset + 1] = im;
}
