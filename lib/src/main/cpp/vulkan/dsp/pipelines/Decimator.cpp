#include "Decimator.h"

#include <vector>

namespace Vulkan::DSP::Pipelines {
    static constexpr const char *SHADER_FILE = "shaders/decimator.comp.spv";

    std::unique_ptr<Decimator> Decimator::create(const Context *context, uint32_t workGroupSize, unsigned index,
                                                 const Buffer *paramsBuffer, const Buffer *tapsBuffer, const Buffer *inBuffer, const Buffer *outBuffer) {
        auto pipeline = std::make_unique<Decimator>(context, workGroupSize, index);
        const bool success = pipeline->createDescriptorSet() &&
                             pipeline->createComputePipeline(SHADER_FILE) &&
                             pipeline->updateDescriptorSets(paramsBuffer, tapsBuffer, inBuffer, outBuffer);
        return success ? std::move(pipeline) : nullptr;
    }

    bool Decimator::createDescriptorSet() {
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
                {
                        .binding = 2,
                        .descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
                        .descriptorCount = 1,
                        .stageFlags = VK_SHADER_STAGE_COMPUTE_BIT,
                        .pImmutableSamplers = nullptr,
                },
                {
                        .binding = 3,
                        .descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
                        .descriptorCount = 1,
                        .stageFlags = VK_SHADER_STAGE_COMPUTE_BIT,
                        .pImmutableSamplers = nullptr,
                },
        };
        VK_CHECK(Pipeline::createDescriptorSet(layoutBinding));
        return true;
    }

    bool Decimator::createComputePipeline(const char *shader) {
        const VkPushConstantRange pushConstantRange = {
                .stageFlags = VK_SHADER_STAGE_COMPUTE_BIT,
                .offset = 0,
                .size = sizeof(PushConstants),
        };
        VK_CHECK(Pipeline::createComputePipeline(shader, &pushConstantRange));
        return true;
    }

    bool Decimator::updateDescriptorSets(const Buffer *paramsBuffer, const Buffer *tapsBuffer, const Buffer *inBuffer, const Buffer *outBuffer) {
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
                        .pBufferInfo = &tapsBuffer->descriptor(),
                        .pTexelBufferView = nullptr,
                },
                {
                        .sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET,
                        .pNext = nullptr,
                        .dstSet = vkDescriptorSet,
                        .dstBinding = 2,
                        .dstArrayElement = 0,
                        .descriptorCount = 1,
                        .descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
                        .pImageInfo = nullptr,
                        .pBufferInfo = &inBuffer->descriptor(),
                        .pTexelBufferView = nullptr,
                },
                {
                        .sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET,
                        .pNext = nullptr,
                        .dstSet = vkDescriptorSet,
                        .dstBinding = 3,
                        .dstArrayElement = 0,
                        .descriptorCount = 1,
                        .descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
                        .pImageInfo = nullptr,
                        .pBufferInfo = &outBuffer->descriptor(),
                        .pTexelBufferView = nullptr,
                }
        };
        vkUpdateDescriptorSets(context->device(), (uint32_t) descriptorSet.size(), descriptorSet.data(), 0, nullptr);

        return true;
    }

    void Decimator::recordComputeCommands(VkCommandBuffer commandBuffer, size_t numOutputSamples) {
        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, vkPipeline);
        vkCmdPushConstants(commandBuffer, vkPipelineLayout, VK_SHADER_STAGE_COMPUTE_BIT, 0, sizeof(PushConstants), &pushConstants);
        vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, vkPipelineLayout, 0, 1, &vkDescriptorSet, 0, nullptr);
        vkCmdDispatch(commandBuffer, numOutputSamples / workGroupSize, 1, 1);
    }
}
