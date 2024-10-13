#pragma once

#include <cstddef>
#include <vector>

#define MAX_SAMPLE_ARRAY_SIZE (512 * 1024)

#define F2B(x) ((x) * sizeof(float))
#define S2B(x) ((x) * 2 * sizeof(float))
#define BOF(x) S2B(x->size() / sizeof(float) - 1)

namespace Vulkan::DSP {
    using Taps = std::vector<std::vector<float>>;

    struct ShiftDecimator {
        virtual ~ShiftDecimator() = default;
        virtual bool process(float *samples, size_t sampleCount, float phi, float omega) = 0;
    };
}
