#include "Buffer.h"
#include "Context.h"
#include "Utils.h"

#include <vector>

namespace Vulkan {
    std::unique_ptr<Context> Context::create(bool enableDebug, AAssetManager *assetManager) {
        auto vk = std::make_unique<Context>(assetManager);
        const bool success = vk->checkInstanceVersion() &&
                             vk->createInstance(enableDebug) &&
                             vk->pickPhysicalDeviceAndQueueFamily() &&
                             vk->createDevice() &&
                             vk->createPools();
        return success ? std::move(vk) : nullptr;
    }

    bool Context::checkInstanceVersion() {
        VK_CALL(vkEnumerateInstanceVersion, &instanceVersion);

        if (VK_VERSION_MAJOR(instanceVersion) != 1) {
            LOGE("Incompatible Vulkan version %d.%d", VK_VERSION_MAJOR(instanceVersion), VK_VERSION_MINOR(instanceVersion));
            return false;
        }

        LOGD("Vulkan version %d.%d", VK_VERSION_MAJOR(instanceVersion), VK_VERSION_MINOR(instanceVersion));
        return true;
    }

    static VKAPI_PTR VkBool32 debugCallback(VkDebugUtilsMessageSeverityFlagBitsEXT messageSeverity,
                                            VkDebugUtilsMessageTypeFlagsEXT messageTypes,
                                            const VkDebugUtilsMessengerCallbackDataEXT *pCallbackData,
                                            void *pUserData) {
        while (pCallbackData != nullptr && pCallbackData->sType == VK_STRUCTURE_TYPE_DEBUG_UTILS_MESSENGER_CALLBACK_DATA_EXT) {
            LOGD("%s", pCallbackData->pMessage);
            pCallbackData = (VkDebugUtilsMessengerCallbackDataEXT *) pCallbackData->pNext;
        }
        return true;
    }

    bool Context::createInstance(bool enableDebug) {
        std::vector<const char *> instanceLayers = {};
        std::vector<const char *> instanceExtensions = {};

        if (enableDebug) {
            instanceLayers.push_back("VK_LAYER_KHRONOS_validation");
            instanceExtensions.push_back(VK_EXT_DEBUG_UTILS_EXTENSION_NAME);
        }

        const VkDebugUtilsMessengerCreateInfoEXT debugCreateInfo = {
                .sType = VK_STRUCTURE_TYPE_DEBUG_UTILS_MESSENGER_CREATE_INFO_EXT,
                .pNext = nullptr,
                .flags = 0,
                .messageSeverity = VkDebugUtilsMessageSeverityFlagBitsEXT::VK_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT |
                                   VkDebugUtilsMessageSeverityFlagBitsEXT::VK_DEBUG_UTILS_MESSAGE_SEVERITY_INFO_BIT_EXT |
                                   VkDebugUtilsMessageSeverityFlagBitsEXT::VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT |
                                   VkDebugUtilsMessageSeverityFlagBitsEXT::VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT,
                .messageType = VkDebugUtilsMessageTypeFlagBitsEXT::VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT |
                               VkDebugUtilsMessageTypeFlagBitsEXT::VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT |
                               VkDebugUtilsMessageTypeFlagBitsEXT::VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT,
                .pfnUserCallback = debugCallback,
                .pUserData = nullptr,
        };
        const VkApplicationInfo applicationInfo = {
                .sType = VK_STRUCTURE_TYPE_APPLICATION_INFO,
                .pNext = nullptr,
                .pApplicationName = "com.hypermagik.spectrum",
                .applicationVersion = VK_MAKE_VERSION(1, 0, 0),
                .pEngineName = "com.hypermagik.spectrum",
                .engineVersion = VK_MAKE_VERSION(1, 0, 0),
                .apiVersion = instanceVersion,
        };
        const VkInstanceCreateInfo createInfo = {
                .sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO,
                .pNext = &debugCreateInfo,
                .flags = 0,
                .pApplicationInfo = &applicationInfo,
                .enabledLayerCount = (uint32_t) instanceLayers.size(),
                .ppEnabledLayerNames = instanceLayers.data(),
                .enabledExtensionCount = (uint32_t) instanceExtensions.size(),
                .ppEnabledExtensionNames = instanceExtensions.data(),
        };
        VK_CALL(vkCreateInstance, &createInfo, nullptr, vkInstance);

        return true;
    }

    bool Context::pickPhysicalDeviceAndQueueFamily() {
        uint32_t numDevices = 0;
        VK_CALL(vkEnumeratePhysicalDevices, vkInstance, &numDevices, nullptr);

        std::vector<VkPhysicalDevice> devices(numDevices);
        VK_CALL(vkEnumeratePhysicalDevices, vkInstance, &numDevices, devices.data());

        for (auto device: devices) {
            uint32_t numQueueFamilies = 0;
            vkGetPhysicalDeviceQueueFamilyProperties(device, &numQueueFamilies, nullptr);

            std::vector<VkQueueFamilyProperties> queueFamilies(numQueueFamilies);
            vkGetPhysicalDeviceQueueFamilyProperties(device, &numQueueFamilies, queueFamilies.data());

            uint32_t computeQFI = 0;
            bool hasQFI = false;

            for (uint32_t i = 0; i < queueFamilies.size(); i++) {
                if (queueFamilies[i].queueFlags & VK_QUEUE_COMPUTE_BIT) {
                    computeQFI = i;
                    hasQFI = true;
                    break;
                }
            }

            if (!hasQFI) {
                continue;
            }

            vkPhysicalDevice = device;
            queueFamilyIndex = computeQFI;
            vkQueues.resize(std::min(maxQueueCount, queueFamilies[computeQFI].queueCount), VK_NULL_HANDLE);
            break;
        }

        VK_CHECK(vkPhysicalDevice != VK_NULL_HANDLE);
        vkGetPhysicalDeviceProperties(vkPhysicalDevice, &vkPhysicalDeviceProperties);
        vkGetPhysicalDeviceMemoryProperties(vkPhysicalDevice, &vkPhysicalDeviceMemoryProperties);

        LOGD("Using physical device '%s' with %zu queues", vkPhysicalDeviceProperties.deviceName, vkQueues.size());
        return true;
    }

    bool Context::createDevice() {
        std::vector<const char *> deviceLayers = {};
        std::vector<const char *> deviceExtensions = {};

        std::vector<float> queuePriorities(vkQueues.size(), 1.0f);

        const VkDeviceQueueCreateInfo queueCreateInfo = {
                .sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO,
                .pNext = nullptr,
                .flags = 0,
                .queueFamilyIndex = queueFamilyIndex,
                .queueCount = (uint32_t) vkQueues.size(),
                .pQueuePriorities = queuePriorities.data(),
        };
        const VkDeviceCreateInfo deviceCreateInfo = {
                .sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO,
                .pNext = nullptr,
                .flags = 0,
                .queueCreateInfoCount = 1,
                .pQueueCreateInfos = &queueCreateInfo,
                .enabledLayerCount = (uint32_t) deviceLayers.size(),
                .ppEnabledLayerNames = deviceLayers.data(),
                .enabledExtensionCount = (uint32_t) deviceExtensions.size(),
                .ppEnabledExtensionNames = deviceExtensions.data(),
                .pEnabledFeatures = nullptr,
        };

        VK_CALL(vkCreateDevice, vkPhysicalDevice, &deviceCreateInfo, nullptr, vkDevice);

        for (auto &vkQueue: vkQueues) {
            vkGetDeviceQueue(vkDevice, queueFamilyIndex, 0, &vkQueue);
        }

        if (vkPhysicalDeviceProperties.limits.timestampPeriod != 0 && vkPhysicalDeviceProperties.limits.timestampComputeAndGraphics) {
            queryTimestampPeriod = vkPhysicalDeviceProperties.limits.timestampPeriod;

            const VkQueryPoolCreateInfo queryPoolCreateInfo = {
                    .sType = VK_STRUCTURE_TYPE_QUERY_POOL_CREATE_INFO,
                    .pNext = nullptr,
                    .queryType = VK_QUERY_TYPE_TIMESTAMP,
                    .queryCount = 32,
            };
            vkCreateQueryPool(vkDevice, &queryPoolCreateInfo, nullptr, &vkQueryPool);
        }

        return true;
    }

    bool Context::createPools() {
        vkDescriptorPool = VulkanDescriptorPool(vkDevice);

        const std::vector<VkDescriptorPoolSize> poolSizes = {
                {
                        .type = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
                        .descriptorCount = 2 * 2 /* 2 x shifter x params+in/out */ +
                                           2 * 16 * 4 /* 2 x 16 decimators x params+taps+in+out */,
                },
        };
        const VkDescriptorPoolCreateInfo poolCreateInfo = {
                .sType = VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO,
                .pNext = nullptr,
                .flags = VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT,
                .maxSets = 2 /* shifter */ +
                           2 * 16 /* decimators */,
                .poolSizeCount = (uint32_t) poolSizes.size(),
                .pPoolSizes = poolSizes.data(),
        };
        VK_CALL(vkCreateDescriptorPool, vkDevice, &poolCreateInfo, nullptr, vkDescriptorPool);

        vkCommandPool = VulkanCommandPool(vkDevice);

        const VkCommandPoolCreateInfo commandPoolCreateInfo = {
                .sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO,
                .pNext = nullptr,
                .flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT,
                .queueFamilyIndex = queueFamilyIndex,
        };
        VK_CALL(vkCreateCommandPool, vkDevice, &commandPoolCreateInfo, nullptr, vkCommandPool);

        return true;
    }

    std::optional<uint32_t> Context::findMemoryType(uint32_t memoryTypeBits, VkFlags properties) const {
        for (uint32_t i = 0; i < vkPhysicalDeviceMemoryProperties.memoryTypeCount; i++) {
            if (memoryTypeBits & 1u) {
                if ((vkPhysicalDeviceMemoryProperties.memoryTypes[i].propertyFlags & properties) == properties) {
                    return i;
                }
            }
            memoryTypeBits >>= 1u;
        }
        return std::nullopt;
    }

    bool Context::createShaderModule(const char *shaderFilePath, VkShaderModule *shaderModule) const {
        AAsset *shaderFile = AAssetManager_open(assetManager, shaderFilePath, AASSET_MODE_BUFFER);
        VK_CHECK(shaderFile != nullptr);

        const size_t shaderSize = AAsset_getLength(shaderFile);

        std::vector<char> shader(shaderSize);
        const int status = AAsset_read(shaderFile, shader.data(), shaderSize);

        AAsset_close(shaderFile);
        VK_CHECK(status >= 0);

        const VkShaderModuleCreateInfo moduleCreateInfo = {
                .sType = VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO,
                .pNext = nullptr,
                .flags = 0,
                .codeSize = shaderSize,
                .pCode = reinterpret_cast<const uint32_t *>(shader.data()),
        };
        VK_CALL(vkCreateShaderModule, vkDevice, &moduleCreateInfo, nullptr, shaderModule);

        return true;
    }

    bool Context::createBuffer(size_t size, VkFlags bufferUsage, VkFlags memoryProperties, VkBuffer *buffer, VkDeviceMemory *memory) const {
        if (buffer == nullptr || memory == nullptr) {
            return false;
        }

        const VkBufferCreateInfo bufferCreateInfo = {
                .sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO,
                .pNext = nullptr,
                .flags = 0,
                .size = size,
                .usage = bufferUsage,
                .sharingMode = VK_SHARING_MODE_EXCLUSIVE,
                .queueFamilyIndexCount = 0,
                .pQueueFamilyIndices = nullptr,
        };
        VK_CALL(vkCreateBuffer, vkDevice, &bufferCreateInfo, nullptr, buffer);

        VkMemoryRequirements memoryRequirements;
        vkGetBufferMemoryRequirements(vkDevice, *buffer, &memoryRequirements);

        const auto memoryTypeIndex = findMemoryType(memoryRequirements.memoryTypeBits, memoryProperties);
        VK_CHECK(memoryTypeIndex.has_value());

        const VkMemoryAllocateInfo allocateInfo = {
                .sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO,
                .pNext = nullptr,
                .allocationSize = memoryRequirements.size,
                .memoryTypeIndex = memoryTypeIndex.value(),
        };
        VK_CALL(vkAllocateMemory, vkDevice, &allocateInfo, nullptr, memory);

        vkBindBufferMemory(vkDevice, *buffer, *memory, 0);

        return true;
    }

    bool Context::createFence(VkFence *fence) const {
        if (fence == nullptr) {
            return false;
        }

        const VkFenceCreateInfo fenceCreateInfo = {
                .sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO,
                .pNext = nullptr,
                .flags = VK_FENCE_CREATE_SIGNALED_BIT,
        };
        VK_CALL(vkCreateFence, vkDevice, &fenceCreateInfo, nullptr, fence);

        return true;
    }

    bool Context::createSemaphore(VkSemaphore *semaphore) const {
        if (semaphore == nullptr) {
            return false;
        }

        const VkSemaphoreCreateInfo semaphoreCcreateInfo = {
                .sType = VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO,
                .pNext = nullptr,
                .flags = 0,
        };
        VK_CALL(vkCreateSemaphore, vkDevice, &semaphoreCcreateInfo, nullptr, semaphore);

        return true;
    }

    bool Context::createCommandBuffer(VkCommandBuffer *commandBuffer) const {
        if (commandBuffer == nullptr) {
            return false;
        }

        const VkCommandBufferAllocateInfo commandBufferAllocateInfo = {
                .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO,
                .pNext = nullptr,
                .commandPool = vkCommandPool,
                .level = VK_COMMAND_BUFFER_LEVEL_PRIMARY,
                .commandBufferCount = 1,
        };
        VK_CALL(vkAllocateCommandBuffers, vkDevice, &commandBufferAllocateInfo, commandBuffer);

        return true;
    }

    void Context::addStageBarrier(VkCommandBuffer *commandBuffer, VkPipelineStageFlags stageFlags) {
        vkCmdPipelineBarrier(*commandBuffer, stageFlags, stageFlags, 0, 0, nullptr, 0, nullptr, 0, nullptr);
    }

    bool Context::beginCommandBuffer(VkCommandBuffer *commandBuffer) {
        if (commandBuffer == nullptr) {
            return false;
        }

        const VkCommandBufferBeginInfo beginInfo = {
                .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO,
                .pNext = nullptr,
                .flags = 0,
                .pInheritanceInfo = nullptr,
        };
        VK_CALL(vkBeginCommandBuffer, *commandBuffer, &beginInfo);

        return true;
    }

    bool Context::submitCommandBuffer(VkCommandBuffer commandBuffer, VkFence fence, size_t queueIndex) const {
        const VkSubmitInfo submitInfo = {
                .sType = VK_STRUCTURE_TYPE_SUBMIT_INFO,
                .pNext = nullptr,
                .waitSemaphoreCount = 0,
                .pWaitSemaphores = nullptr,
                .pWaitDstStageMask = nullptr,
                .commandBufferCount = 1,
                .pCommandBuffers = &commandBuffer,
                .signalSemaphoreCount = 0,
                .pSignalSemaphores = nullptr,
        };
        VK_CALL(vkQueueSubmit, vkQueues.at(queueIndex), 1, &submitInfo, fence);

        return true;
    }

    bool Context::queueWaitIdle(size_t queueIndex) const {
        VK_CALL(vkQueueWaitIdle, vkQueues.at(queueIndex));
        return true;
    }
}

