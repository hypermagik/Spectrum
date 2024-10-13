#pragma once

#include "Context.h"

#include <vector>

namespace Vulkan {
    struct Pipeline {
    protected:
        Pipeline(const Context *context, uint32_t workGroupSize)
            : context(context)
            , workGroupSize(workGroupSize)
            , vkDescriptorSetLayout(context->device())
            , vkPipelineLayout(context->device())
            , vkPipeline(context->device()) {}
        ~Pipeline();

        bool createDescriptorSet(const std::vector<VkDescriptorSetLayoutBinding> &layoutBinding);
        bool createComputePipeline(const char *shader, const VkPushConstantRange *pushConstants);

        const Context * const context;
        const uint32_t workGroupSize;

        VulkanDescriptorSetLayout vkDescriptorSetLayout;
        VkDescriptorSet vkDescriptorSet = VK_NULL_HANDLE;

        VulkanPipelineLayout vkPipelineLayout;
        VulkanPipeline vkPipeline;
    };
}
