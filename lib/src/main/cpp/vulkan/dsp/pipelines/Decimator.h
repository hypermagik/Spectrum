#pragma once

#include "vulkan/Buffer.h"
#include "vulkan/Pipeline.h"

namespace Vulkan::DSP::Pipelines {
    struct Decimator : Pipeline {
        static std::unique_ptr<Decimator> create(const Context *context, uint32_t workGroupSize, unsigned index,
                                                 const Buffer *paramsBuffer, const Buffer *tapsBuffer, const Buffer *inBuffer, const Buffer *outBuffer);

        Decimator(const Context *context, uint32_t workGroupSize, unsigned index) : Pipeline(context, workGroupSize), pushConstants{index} {}

        void recordComputeCommands(VkCommandBuffer commandBuffer, size_t numOutputSamples);

    protected:
        bool createDescriptorSet();
        bool createComputePipeline(const char *shader);
        bool updateDescriptorSets(const Buffer *paramsBuffer, const Buffer *tapsBuffer, const Buffer *inBuffer, const Buffer *outBuffer);

        struct PushConstants {
            unsigned index;
        } pushConstants [[gnu::packed]];
    };
}
