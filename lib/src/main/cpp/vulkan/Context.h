#pragma once

#include "Wrappers.h"

#include <android/asset_manager.h>
#include <memory>
#include <optional>
#include <vector>
#include <vulkan/vulkan_core.h>

namespace Vulkan {
    struct Buffer;

    struct Context {
        static std::unique_ptr<Context> create(bool enableDebug, AAssetManager *assetManager);

        Context(AAssetManager *assetManager) : assetManager(assetManager) {}

        VkDevice device() const { return vkDevice; }
        VkQueryPool queryPool() const { return vkQueryPool; }
        VkCommandPool commandPool() const { return vkCommandPool; }
        VkDescriptorPool descriptorPool() const { return vkDescriptorPool; }

        size_t queueCount() const { return vkQueues.size(); }
        float timestampPeriod() const { return queryTimestampPeriod; }

        bool createShaderModule(const char *shaderFilePath, VkShaderModule *shaderModule) const;
        bool createBuffer(size_t size, VkFlags bufferUsage, VkFlags memoryProperties, VkBuffer *buffer, VkDeviceMemory *memory) const;
        bool createFence(VkFence *fence) const;
        bool createSemaphore(VkSemaphore *semaphore) const;
        bool createCommandBuffer(VkCommandBuffer *commandBuffer) const;

        static bool beginCommandBuffer(VkCommandBuffer *commandBuffer);
        bool endAndSubmitCommandBuffer(VkCommandBuffer commandBuffer, VkSemaphore *waitSemaphore, VkSemaphore *signalSemaphore, VkFence fence, size_t queueIndex) const;
        bool queueWaitIdle(size_t queueIndex) const;

        static void addStageBarrier(VkCommandBuffer *commandBuffer, VkPipelineStageFlags stageFlags);

    private:
        bool checkInstanceVersion();
        bool createInstance(bool enableDebug);
        bool pickPhysicalDeviceAndQueueFamily();
        bool createDevice();
        bool createPools();

        std::optional<uint32_t> findMemoryType(uint32_t memoryTypeBits, VkFlags properties) const;

        AAssetManager * const assetManager;

        uint32_t instanceVersion = 0;
        VulkanInstance vkInstance;

        VkPhysicalDevice vkPhysicalDevice = VK_NULL_HANDLE;
        VkPhysicalDeviceProperties vkPhysicalDeviceProperties;
        VkPhysicalDeviceMemoryProperties vkPhysicalDeviceMemoryProperties;

        uint32_t queueFamilyIndex = 0;
        float queryTimestampPeriod = 0.0f;
        static constexpr uint32_t maxQueueCount = 2;

        VulkanDevice vkDevice;
        std::vector<VkQueue> vkQueues;
        VkQueryPool vkQueryPool;
        VulkanDescriptorPool vkDescriptorPool { VK_NULL_HANDLE };
        VulkanCommandPool vkCommandPool { VK_NULL_HANDLE };
   };
}
