#include "Pipeline.h"
#include "Utils.h"

namespace Vulkan {
    bool Pipeline::createDescriptorSet(const std::vector<VkDescriptorSetLayoutBinding> &layoutBinding) {
        const VkDescriptorSetLayoutCreateInfo createInfo = {
                .sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO,
                .pNext = nullptr,
                .flags = 0,
                .bindingCount = (uint32_t) layoutBinding.size(),
                .pBindings = layoutBinding.data(),
        };
        VK_CALL(vkCreateDescriptorSetLayout, context->device(), &createInfo, nullptr, vkDescriptorSetLayout);

        const VkDescriptorSetAllocateInfo allocateInfo = {
                .sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO,
                .pNext = nullptr,
                .descriptorPool = context->descriptorPool(),
                .descriptorSetCount = 1,
                .pSetLayouts = vkDescriptorSetLayout,
        };
        VK_CALL(vkAllocateDescriptorSets, context->device(), &allocateInfo, &vkDescriptorSet);

        return true;
    }

    bool Pipeline::createComputePipeline(const char *shader, const VkPushConstantRange *pushConstants) {
        VulkanShaderModule shaderModule(context->device());
        VK_CHECK(context->createShaderModule(shader, shaderModule));

        const VkPipelineLayoutCreateInfo layoutCreateInfo = {
                .sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO,
                .pNext = nullptr,
                .flags = 0,
                .setLayoutCount = 1,
                .pSetLayouts = vkDescriptorSetLayout,
                .pushConstantRangeCount = pushConstants == nullptr ? 0 : 1u,
                .pPushConstantRanges = pushConstants
        };
        VK_CALL(vkCreatePipelineLayout, context->device(), &layoutCreateInfo, nullptr, vkPipelineLayout);

        const uint32_t specializationData[] = {workGroupSize};
        const std::vector<VkSpecializationMapEntry> specializationMap = {{0, 0, sizeof(uint32_t)}};

        const VkSpecializationInfo specializationInfo = {
                .mapEntryCount = (uint32_t) specializationMap.size(),
                .pMapEntries = specializationMap.data(),
                .dataSize = sizeof(specializationData),
                .pData = specializationData,
        };
        const VkComputePipelineCreateInfo pipelineCreateInfo = {
                .sType = VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO,
                .pNext = nullptr,
                .flags = 0,
                .stage = {
                        .sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO,
                        .pNext = nullptr,
                        .flags = 0,
                        .stage = VK_SHADER_STAGE_COMPUTE_BIT,
                        .module = shaderModule,
                        .pName = "main",
                        .pSpecializationInfo = &specializationInfo,
                },
                .layout = vkPipelineLayout,
                .basePipelineHandle = 0,
                .basePipelineIndex = 0,
        };
        VK_CALL(vkCreateComputePipelines, context->device(), VK_NULL_HANDLE, 1, &pipelineCreateInfo, nullptr, vkPipeline);

        return true;
    }

    Pipeline::~Pipeline() {
        const auto result = vkFreeDescriptorSets(context->device(), context->descriptorPool(), 1, &vkDescriptorSet);
        if (result != VK_SUCCESS) {
            LOGE("vkFreeDescriptorSets failed with %s at %s:%u", vkResultToString(result), __FILE__, __LINE__);
        }
    }
}
