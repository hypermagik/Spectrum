#include "Buffer.h"

namespace Vulkan {
    std::unique_ptr<Buffer> Buffer::create(const Context *context, uint32_t size, VkBufferUsageFlags usage, VkMemoryPropertyFlags properties) {
        auto buffer = std::make_unique<Buffer>(context, size);
        const bool success = buffer->initialize(usage, properties);
        return success ? std::move(buffer) : nullptr;
    }

    bool Buffer::initialize(VkBufferUsageFlags usage, VkMemoryPropertyFlags properties) {
        VK_CHECK(context->createBuffer(bufferSize, usage, properties, vkBuffer, vkMemory));
        vkBufferInfo = {vkBuffer, 0, bufferSize};
        return true;
    }

    bool Buffer::map(void **data, uint64_t offset, uint64_t size) const {
        VK_CALL(vkMapMemory, context->device(), vkMemory, offset, size, 0, data);
        return true;
    }

    bool Buffer::flush(uint64_t offset, uint64_t size) const {
        const VkMappedMemoryRange memoryRange = {
                .sType = VK_STRUCTURE_TYPE_MAPPED_MEMORY_RANGE,
                .pNext = nullptr,
                .memory = vkMemory,
                .offset = offset,
                .size = size,
        };
        VK_CALL(vkFlushMappedMemoryRanges, context->device(), 1, &memoryRange);
        return true;
    }

    bool Buffer::invalidate(uint64_t offset, uint64_t size) const {
        const VkMappedMemoryRange memoryRange = {
                .sType = VK_STRUCTURE_TYPE_MAPPED_MEMORY_RANGE,
                .pNext = nullptr,
                .memory = vkMemory,
                .offset = offset,
                .size = size,
        };
        VK_CALL(vkInvalidateMappedMemoryRanges, context->device(), 1, &memoryRange);
        return true;
    }

    bool Buffer::copyFrom(const void *data, uint64_t offset, uint64_t size) const {
        void *bufferData = nullptr;
        VK_CALL(vkMapMemory, context->device(), vkMemory, offset, size, 0, &bufferData);
        memcpy(bufferData, data, size);
        vkUnmapMemory(context->device(), vkMemory);
        return true;
    }

    bool Buffer::copyTo(void *data, uint64_t offset, uint64_t size) const {
        void *bufferData = nullptr;
        VK_CALL(vkMapMemory, context->device(), vkMemory, offset, size, 0, &bufferData);
        memcpy(data, bufferData, size);
        vkUnmapMemory(context->device(), vkMemory);
        return true;
    }
}