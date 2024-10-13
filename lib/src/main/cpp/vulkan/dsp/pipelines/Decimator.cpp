#include "Decimator.h"

#include <vector>

namespace Vulkan::DSP::Pipelines {
    static constexpr const char *SHADER_FILE = "shaders/decimator.comp.spv";

    std::unique_ptr<Decimator> Decimator::create(const Context *context, uint32_t workGroupSize, const Buffer *taps, const Buffer *in) {
        auto pipeline = std::make_unique<Decimator>(context, workGroupSize);
        const bool success = pipeline->createDescriptorSet() &&
                             pipeline->createComputePipeline(SHADER_FILE) &&
                             pipeline->updateDescriptorSets(taps, in);
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

    bool Decimator::updateDescriptorSets(const Buffer *taps, const Buffer *in) {
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
                        .pBufferInfo = &taps->descriptor(),
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
                        .pBufferInfo = &in->descriptor(),
                        .pTexelBufferView = nullptr,
                }
        };
        vkUpdateDescriptorSets(context->device(), (uint32_t) descriptorSet.size(), descriptorSet.data(), 0, nullptr);

        return true;
    }

    void Decimator::setOutput(const Buffer *out, unsigned offset) {
        std::vector<VkWriteDescriptorSet> descriptorSet = {
                {
                        .sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET,
                        .pNext = nullptr,
                        .dstSet = vkDescriptorSet,
                        .dstBinding = 2,
                        .dstArrayElement = 0,
                        .descriptorCount = 1,
                        .descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
                        .pImageInfo = nullptr,
                        .pBufferInfo = &out->descriptor(),
                        .pTexelBufferView = nullptr,
                }
        };
        vkUpdateDescriptorSets(context->device(), (uint32_t) descriptorSet.size(), descriptorSet.data(), 0, nullptr);

        pushConstants.offset = offset;
    }

    void Decimator::recordComputeCommands(VkCommandBuffer commandBuffer, size_t numOutputSamples) {
        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, vkPipeline);

        vkCmdPushConstants(commandBuffer, vkPipelineLayout, VK_SHADER_STAGE_COMPUTE_BIT, 0, sizeof(PushConstants), &pushConstants);

        vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, vkPipelineLayout, 0, 1, &vkDescriptorSet, 0, nullptr);

        vkCmdDispatch(commandBuffer, numOutputSamples / workGroupSize, 1, 1);
    }
}
