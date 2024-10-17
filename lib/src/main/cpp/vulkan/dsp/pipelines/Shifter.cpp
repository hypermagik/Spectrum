#include "Shifter.h"

#include <vector>

namespace Vulkan::DSP::Pipelines {
    static constexpr const char *SHADER_FILE = "shaders/shifter.comp.spv";

    std::unique_ptr<Shifter> Shifter::create(const Context *context, uint32_t workGroupSize, const Buffer *paramsBuffer, const Buffer *inoutBuffer) {
        auto pipeline = std::make_unique<Shifter>(context, workGroupSize);
        const bool success = pipeline->createDescriptorSet() &&
                             pipeline->createComputePipeline(SHADER_FILE) &&
                             pipeline->updateDescriptorSets(paramsBuffer, inoutBuffer);
        return success ? std::move(pipeline) : nullptr;
    }

    bool Shifter::createDescriptorSet() {
        std::vector<VkDescriptorSetLayoutBinding> layoutBinding = {
                {
                        .binding = 0,
                        .descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
                        .descriptorCount = 1,
                        .stageFlags = VK_SHADER_STAGE_COMPUTE_BIT,
                        .pImmutableSamplers = nullptr,
                },
                {
                        .binding = 1,
                        .descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
                        .descriptorCount = 1,
                        .stageFlags = VK_SHADER_STAGE_COMPUTE_BIT,
                        .pImmutableSamplers = nullptr,
                },
        };
        VK_CHECK(Pipeline::createDescriptorSet(layoutBinding));
        return true;
    }

    bool Shifter::createComputePipeline(const char *shader) {
        VK_CHECK(Pipeline::createComputePipeline(shader, nullptr));
        return true;
    }

    bool Shifter::updateDescriptorSets(const Buffer *paramsBuffer, const Buffer *inoutBuffer) {
        std::vector<VkWriteDescriptorSet> descriptorSet = {
                {
                        .sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET,
                        .pNext = nullptr,
                        .dstSet = vkDescriptorSet,
                        .dstBinding = 0,
                        .dstArrayElement = 0,
                        .descriptorCount = 1,
                        .descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
                        .pImageInfo = nullptr,
                        .pBufferInfo = &paramsBuffer->descriptor(),
                        .pTexelBufferView = nullptr,
                },
                {
                        .sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET,
                        .pNext = nullptr,
                        .dstSet = vkDescriptorSet,
                        .dstBinding = 1,
                        .dstArrayElement = 0,
                        .descriptorCount = 1,
                        .descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
                        .pImageInfo = nullptr,
                        .pBufferInfo = &inoutBuffer->descriptor(),
                        .pTexelBufferView = nullptr,
                }
        };
        vkUpdateDescriptorSets(context->device(), (uint32_t) descriptorSet.size(), descriptorSet.data(), 0, nullptr);
        return true;
    }

    void Shifter::recordComputeCommands(VkCommandBuffer commandBuffer, size_t numSamples) {
        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, vkPipeline);
        vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, vkPipelineLayout, 0, 1, &vkDescriptorSet, 0, nullptr);
        vkCmdDispatch(commandBuffer, numSamples / workGroupSize, 1, 1);
    }
}
