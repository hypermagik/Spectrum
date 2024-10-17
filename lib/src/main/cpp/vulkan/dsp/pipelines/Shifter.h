#pragma once

#include "vulkan/Buffer.h"
#include "vulkan/Pipeline.h"

namespace Vulkan::DSP::Pipelines {
    struct Shifter : Pipeline {
        static std::unique_ptr<Shifter> create(const Context *context, uint32_t workGroupSize,
                                               const Buffer *paramsBuffer, const Buffer *inoutBuffer);

        Shifter(const Context *context, uint32_t workGroupSize) : Pipeline(context, workGroupSize) {}

        void recordComputeCommands(VkCommandBuffer commandBuffer, size_t numSamples);

    protected:
        bool createDescriptorSet();
        bool createComputePipeline(const char *shader);
        bool updateDescriptorSets(const Buffer *paramsBuffer, const Buffer *inoutBuffer);
    };
}
