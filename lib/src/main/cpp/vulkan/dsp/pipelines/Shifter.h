#pragma once

#include "vulkan/Buffer.h"
#include "vulkan/Pipeline.h"

namespace Vulkan::DSP::Pipelines {
    struct Shifter : Pipeline {
        static std::unique_ptr<Shifter> create(const Context *context, uint32_t workGroupSize, const Buffer *buffer, unsigned offset);

        Shifter(const Context *context, uint32_t workGroupSize, unsigned offset) : Pipeline(context, workGroupSize), pushConstants{offset, 0.0f, 0.0f} {}

        void recordComputeCommands(VkCommandBuffer commandBuffer, size_t numSamples, float phi, float omega);

    protected:
        bool createDescriptorSet();
        bool createComputePipeline(const char *shader);
        bool updateDescriptorSets(const Buffer *buffer);

        struct PushConstants {
            unsigned offset;
            float phi;
            float omega;
        } pushConstants [[gnu::packed]];
    };
}
