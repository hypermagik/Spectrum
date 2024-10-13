#include "Shifter.h"

#include <vector>

namespace Vulkan::DSP::Pipelines {
    static constexpr const char *SHADER_FILE = "shaders/shifter.comp.spv";

    std::unique_ptr<Shifter> Shifter::create(const Context *context, uint32_t workGroupSize, const Buffer *buffer, unsigned offset) {
        auto pipeline = std::make_unique<Shifter>(context, workGroupSize, offset);
        const bool success = pipeline->createDescriptorSet() &&
                             pipeline->createComputePipeline(SHADER_FILE) &&
                             pipeline->updateDescriptorSets(buffer);
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
        };
        VK_CHECK(Pipeline::createDescriptorSet(layoutBinding));
        return true;
    }

    bool Shifter::createComputePipeline(const char *shader) {
        const VkPushConstantRange pushConstantRange = {
                .stageFlags = VK_SHADER_STAGE_COMPUTE_BIT,
                .offset = 0,
                .size = sizeof(PushConstants),
        };
        VK_CHECK(Pipeline::createComputePipeline(shader, &pushConstantRange));
        return true;
    }

    bool Shifter::updateDescriptorSets(const Buffer *buffer) {
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
                        .pBufferInfo = &buffer->descriptor(),
                        .pTexelBufferView = nullptr,
                }
        };
        vkUpdateDescriptorSets(context->device(), (uint32_t) descriptorSet.size(), descriptorSet.data(), 0, nullptr);
        return true;
    }

    void Shifter::recordComputeCommands(VkCommandBuffer commandBuffer, size_t numSamples, float phi, float omega) {
        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, vkPipeline);

        pushConstants.phi = phi;
        pushConstants.omega = omega;
        vkCmdPushConstants(commandBuffer, vkPipelineLayout, VK_SHADER_STAGE_COMPUTE_BIT, 0, sizeof(PushConstants), &pushConstants);

        vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, vkPipelineLayout, 0, 1, &vkDescriptorSet, 0, nullptr);

        vkCmdDispatch(commandBuffer, numSamples / workGroupSize, 1, 1);
    }
}
