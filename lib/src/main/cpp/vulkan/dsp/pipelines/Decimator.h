#pragma once

#include "vulkan/Buffer.h"
#include "vulkan/Pipeline.h"

namespace Vulkan::DSP::Pipelines {
    struct Decimator : Pipeline {
        static std::unique_ptr<Decimator> create(const Context *context, uint32_t workGroupSize, const Buffer *taps, const Buffer *in);

        Decimator(const Context *context, uint32_t workGroupSize) : Pipeline(context, workGroupSize), pushConstants{0} {}

        void setOutput(const Buffer *out, unsigned offset);
        void recordComputeCommands(VkCommandBuffer commandBuffer, size_t numOutputSamples);

    protected:
        bool createDescriptorSet();
        bool createComputePipeline(const char *shader);
        bool updateDescriptorSets(const Buffer *taps, const Buffer *in);

        struct PushConstants {
            unsigned offset;
        } pushConstants [[gnu::packed]];
    };
}
