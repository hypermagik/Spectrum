#pragma once

#include <utility>
#include <vulkan/vulkan_core.h>

template <typename THandle>
struct VulkanObjectBase {
    VulkanObjectBase() = default;

    VulkanObjectBase(const VulkanObjectBase&) = delete;
    VulkanObjectBase& operator=(const VulkanObjectBase&) = delete;

    VulkanObjectBase(VulkanObjectBase&& other) noexcept { *this = std::move(other); }

    VulkanObjectBase& operator=(VulkanObjectBase&& other) noexcept {
        handle = other.handle;
        other.handle = VK_NULL_HANDLE;
        return *this;
    }

    operator THandle() { return handle; }
    operator THandle() const { return handle; }

    operator THandle*() { return &handle; }
    operator THandle*() const { return &handle; }

protected:
    THandle handle = VK_NULL_HANDLE;
};

template <typename THandle, typename TDestroyer>
struct VulkanGlobalObject : VulkanObjectBase<THandle> {
    ~VulkanGlobalObject() {
        if (this->handle != VK_NULL_HANDLE) {
            TDestroyer::destroy(this->handle);
        }
    }
};

#define VULKAN_RAII_OBJECT(Type, destroyer)                                  \
    struct Vulkan##Type##Destroyer {                                         \
        static void destroy(Vk##Type handle) { destroyer(handle, nullptr); } \
    };                                                                       \
    using Vulkan##Type = VulkanGlobalObject<Vk##Type, Vulkan##Type##Destroyer>

VULKAN_RAII_OBJECT(Instance, vkDestroyInstance);
VULKAN_RAII_OBJECT(Device, vkDestroyDevice);

#undef VULKAN_RAII_OBJECT


template <typename THandle, typename TDestroyer>
struct VulkanObjectFromDevice : VulkanObjectBase<THandle> {
    explicit VulkanObjectFromDevice(VkDevice device) : device(device) {}

    VulkanObjectFromDevice(VulkanObjectFromDevice&& other) noexcept { *this = std::move(other); }

    VulkanObjectFromDevice& operator=(VulkanObjectFromDevice&& other) noexcept {
        VulkanObjectBase<THandle>::operator=(std::move(other));
        device = other.device;
        return *this;
    }

    ~VulkanObjectFromDevice() {
        if (this->handle != VK_NULL_HANDLE) {
            TDestroyer::destroy(device, this->handle);
        }
    }

protected:
    VkDevice device = VK_NULL_HANDLE;
};

#define VULKAN_RAII_OBJECT_FROM_DEVICE(Type, destroyer)         \
    struct Vulkan##Type##Destroyer {                            \
        static void destroy(VkDevice device, Vk##Type handle) { \
            destroyer(device, handle, nullptr);                 \
        }                                                       \
    };                                                          \
    using Vulkan##Type = VulkanObjectFromDevice<Vk##Type, Vulkan##Type##Destroyer>

VULKAN_RAII_OBJECT_FROM_DEVICE(Buffer, vkDestroyBuffer);
VULKAN_RAII_OBJECT_FROM_DEVICE(CommandPool, vkDestroyCommandPool);
VULKAN_RAII_OBJECT_FROM_DEVICE(DescriptorPool, vkDestroyDescriptorPool);
VULKAN_RAII_OBJECT_FROM_DEVICE(DescriptorSetLayout, vkDestroyDescriptorSetLayout);
VULKAN_RAII_OBJECT_FROM_DEVICE(DeviceMemory, vkFreeMemory);
VULKAN_RAII_OBJECT_FROM_DEVICE(Event, vkDestroyEvent);
VULKAN_RAII_OBJECT_FROM_DEVICE(Fence, vkDestroyFence);
VULKAN_RAII_OBJECT_FROM_DEVICE(Pipeline, vkDestroyPipeline);
VULKAN_RAII_OBJECT_FROM_DEVICE(PipelineLayout, vkDestroyPipelineLayout);
VULKAN_RAII_OBJECT_FROM_DEVICE(QueryPool, vkDestroyQueryPool);
VULKAN_RAII_OBJECT_FROM_DEVICE(Semaphore, vkDestroySemaphore);
VULKAN_RAII_OBJECT_FROM_DEVICE(ShaderModule, vkDestroyShaderModule);

#undef VULKAN_RAII_OBJECT_FROM_DEVICE


template <typename THandle, typename TPoolHandle, typename TDestroyer>
struct VulkanObjectFromPool : VulkanObjectBase<THandle> {
    VulkanObjectFromPool(VkDevice device, TPoolHandle pool) : device(device), pool(pool) {}

    VulkanObjectFromPool(VulkanObjectFromPool&& other) noexcept { *this = std::move(other); }

    VulkanObjectFromPool& operator=(VulkanObjectFromPool&& other) noexcept {
        VulkanObjectBase<THandle>::operator=(std::move(other));
        device = other.device;
        pool = other.pool;
        return *this;
    }

    ~VulkanObjectFromPool() {
        if (this->handle != VK_NULL_HANDLE) {
            TDestroyer::destroy(device, pool, this->handle);
        }
    }

protected:
    VkDevice device = VK_NULL_HANDLE;
    TPoolHandle pool = VK_NULL_HANDLE;
};

#define VULKAN_RAII_OBJECT_FROM_POOL(Type, VkPoolType, destroyer)                \
    struct Vulkan##Type##Destroyer {                                             \
        static void destroy(VkDevice device, VkPoolType pool, Vk##Type handle) { \
            destroyer(device, pool, 1, &handle);                                 \
        }                                                                        \
    };                                                                           \
    using Vulkan##Type = VulkanObjectFromPool<Vk##Type, VkPoolType, Vulkan##Type##Destroyer>

VULKAN_RAII_OBJECT_FROM_POOL(CommandBuffer, VkCommandPool, vkFreeCommandBuffers);
VULKAN_RAII_OBJECT_FROM_POOL(DescriptorSet, VkDescriptorPool, vkFreeDescriptorSets);

#undef VULKAN_RAII_OBJECT_FROM_POOL
