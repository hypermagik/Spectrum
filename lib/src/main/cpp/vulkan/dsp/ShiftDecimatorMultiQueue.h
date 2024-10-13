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

        std::unique_ptr<Buffer> stagingBuffers[numBuffers];
        std::unique_ptr<Buffer> outputBuffers[numBuffers];

        void *pStagingBuffers[numBuffers];
        void *pOutputBuffers[numBuffers];

        std::unique_ptr<Pipelines::Shifter> shifters[numBuffers];
        unique_ptrs<Pipelines::Decimator> decimators[numBuffers];

        std::unique_ptr<VulkanFence> fences[numBuffers];
        std::unique_ptr<VulkanSemaphore> semaphores[numBuffers][2];
        std::unique_ptr<VulkanCommandBuffer> commandBuffers[numBuffers][3];

        bool firstSubmit = true;

        struct Counters {
            const bool enable = true;

            int counter = 0;

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
