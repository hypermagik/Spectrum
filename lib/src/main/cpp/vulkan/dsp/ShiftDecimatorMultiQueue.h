#pragma once

#include "ShiftDecimator.h"
#include "vulkan/Context.h"
#include "vulkan/Buffer.h"
#include "pipelines/Shifter.h"
#include "pipelines/Decimator.h"

namespace Vulkan::DSP {
    struct ShiftDecimatorMultiQueue : ShiftDecimator {
        static std::unique_ptr<ShiftDecimatorMultiQueue> create(Context *, Taps &&);

        explicit ShiftDecimatorMultiQueue(Context *context) : context(context) {}
        ~ShiftDecimatorMultiQueue() override;

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

        std::unique_ptr<Buffer> paramsBuffers[numBuffers];
        std::unique_ptr<Buffer> stagingBuffers[numBuffers];
        std::unique_ptr<Buffer> outputBuffers[numBuffers];

        void *pParamsBuffers[numBuffers];
        void *pStagingBuffers[numBuffers];
        void *pOutputBuffers[numBuffers];

        std::unique_ptr<Pipelines::Shifter> shifters[numBuffers];
        unique_ptrs<Pipelines::Decimator> decimators[numBuffers];

        static constexpr size_t numStages = 3;

        std::unique_ptr<VulkanFence> fences[numBuffers];
        std::unique_ptr<VulkanSemaphore> semaphores[numBuffers][numStages - 1];
        std::unique_ptr<VulkanCommandBuffer> commandBuffers[numBuffers][numStages];

        bool firstSubmit = true;
        bool Submit();

        struct Counters {
            const bool enable = true;

            int counter = 0;

            struct Stage {
                float timeMin = MAXFLOAT;
                float timeMax = 0.0f;
                float timeSum = 0.0f;
            } stages[numStages];

            timespec waitTimestamp[2];
            unsigned long fenceWaitTimeMin = LONG_MAX;
            unsigned long fenceWaitTimeMax = 0;
            unsigned long fenceWaitTimeSum = 0;

            timespec totalTimestamp[2];
            unsigned long totalTimeMin = LONG_MAX;
            unsigned long totalTimeMax = 0;
            unsigned long totalTimeSum = 0;

            inline void getTime(timespec &ts) const {
                if (enable) {
                    clock_gettime(CLOCK_MONOTONIC, &ts);
                }
            }
        } counters;

        void updateCounters();
    };
}
