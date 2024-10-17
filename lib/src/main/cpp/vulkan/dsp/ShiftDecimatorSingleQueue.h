#pragma once

#include "ShiftDecimator.h"
#include "vulkan/Context.h"
#include "vulkan/Buffer.h"
#include "pipelines/Shifter.h"
#include "pipelines/Decimator.h"

namespace Vulkan::DSP {
    struct ShiftDecimatorSingleQueue : ShiftDecimator {
        static std::unique_ptr<ShiftDecimatorSingleQueue> create(Context *, Taps &&);

        explicit ShiftDecimatorSingleQueue(Context *context) : context(context) {}
        ~ShiftDecimatorSingleQueue() override;

        bool process(float *samples, size_t sampleCount, float phi, float omega) override;

    private:
        bool initialize(Taps &);

        template <typename T>
        using unique_ptrs = std::vector<std::unique_ptr<T>>;

        Context * const context;

        static constexpr size_t groupSize = 64;

        static constexpr size_t numBuffers = 2;
        size_t bufferIndex = 0;

        unique_ptrs<Buffer> tapBuffers;
        unique_ptrs<Buffer> inputBuffers;

        static constexpr size_t paramsBufferSize = F2B(2 + 1 + 16);

        std::unique_ptr<Buffer> paramsBuffer;
        std::unique_ptr<Buffer> outputBuffer;

        void *pParamsBuffer = nullptr;
        void *pInputBuffer = nullptr;
        void *pOutputBuffer = nullptr;

        std::unique_ptr<Pipelines::Shifter> shifter;
        unique_ptrs<Pipelines::Decimator> decimators;

        static constexpr size_t numStages = 2;

        std::unique_ptr<VulkanFence> fences[numBuffers][numStages];
        std::unique_ptr<VulkanCommandBuffer> commandBuffers[numBuffers][numStages];

        struct Counters {
            const bool enable = true;

            int counter = 0;

            timespec totalTimestamp[2];
            unsigned long totalTimeMin = LONG_MAX;
            unsigned long totalTimeMax = 0;
            unsigned long totalTimeSum = 0;

            struct Stages {
                float timeMin = MAXFLOAT;
                float timeMax = 0.0f;
                float timeSum = 0.0f;
                timespec fenceWaitTimestamp[2];
                unsigned long fenceWaitTimeMin = LONG_MAX;
                unsigned long fenceWaitTimeMax = 0;
                unsigned long fenceWaitTimeSum = 0;
            } stages[numStages];

            inline void getTime(timespec &ts) const {
                if (enable) {
                    clock_gettime(CLOCK_MONOTONIC, &ts);
                }
            }
        } counters;

        void updateCounters();
    };
}
