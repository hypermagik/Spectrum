#pragma once

#include "Context.h"
#include "Utils.h"

#include <memory>

namespace Vulkan {
    struct Buffer {
        static std::unique_ptr<Buffer> create(const Context *context, uint32_t size, VkBufferUsageFlags usage, VkMemoryPropertyFlags properties);

        Buffer(const Context *context, uint32_t bufferSize)
            : context(context)
            , bufferSize(bufferSize)
            , vkBuffer(context->device())
            , vkMemory(context->device()) {}

        uint32_t size() const { return bufferSize; }

        const VkBuffer handle() const { return vkBuffer; }
        const VkDescriptorBufferInfo &descriptor() const { return vkBufferInfo; }

        bool map(void **data, uint64_t offset, uint64_t size) const;
        bool flush(uint64_t offset, uint64_t size) const;
        bool invalidate(uint64_t offset, uint64_t size) const;

        bool copyFrom(const void *data, uint64_t offset, uint64_t size) const;
        bool copyTo(void *data, uint64_t offset, uint64_t size) const;

    private:
        bool initialize(VkBufferUsageFlags usage, VkMemoryPropertyFlags properties);

        const Context *context;
        const uint32_t bufferSize;

        VulkanBuffer vkBuffer;
        VulkanDeviceMemory vkMemory;
        VkDescriptorBufferInfo vkBufferInfo;
    };
}
