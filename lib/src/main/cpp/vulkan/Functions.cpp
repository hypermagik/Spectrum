#include "Functions.h"
#include "Utils.h"

#include <dlfcn.h>

namespace Vulkan {
    bool initializePFN(void *libVulkanHandle) {
        VK_CHECK(vkGetInstanceProcAddr = (PFN_vkGetInstanceProcAddr) dlsym(libVulkanHandle, "vkGetInstanceProcAddr"));
        VK_CHECK(vkEnumerateInstanceVersion = (PFN_vkEnumerateInstanceVersion) vkGetInstanceProcAddr(nullptr, "vkEnumerateInstanceVersion"));
        VK_CHECK(vkCreateInstance = (PFN_vkCreateInstance) vkGetInstanceProcAddr(nullptr, "vkCreateInstance"));
        return true;
    }

    bool initializePFN(VkInstance vkInstance) {
        VK_CHECK(vkAllocateCommandBuffers = (PFN_vkAllocateCommandBuffers) vkGetInstanceProcAddr(vkInstance, "vkAllocateCommandBuffers"));
        VK_CHECK(vkAllocateDescriptorSets = (PFN_vkAllocateDescriptorSets) vkGetInstanceProcAddr(vkInstance, "vkAllocateDescriptorSets"));
        VK_CHECK(vkAllocateMemory = (PFN_vkAllocateMemory) vkGetInstanceProcAddr(vkInstance, "vkAllocateMemory"));
        VK_CHECK(vkBeginCommandBuffer = (PFN_vkBeginCommandBuffer) vkGetInstanceProcAddr(vkInstance, "vkBeginCommandBuffer"));
        VK_CHECK(vkBindBufferMemory = (PFN_vkBindBufferMemory) vkGetInstanceProcAddr(vkInstance, "vkBindBufferMemory"));
        VK_CHECK(vkCmdBindDescriptorSets = (PFN_vkCmdBindDescriptorSets) vkGetInstanceProcAddr(vkInstance, "vkCmdBindDescriptorSets"));
        VK_CHECK(vkCmdBindPipeline = (PFN_vkCmdBindPipeline) vkGetInstanceProcAddr(vkInstance, "vkCmdBindPipeline"));
        VK_CHECK(vkCmdCopyBuffer = (PFN_vkCmdCopyBuffer) vkGetInstanceProcAddr(vkInstance, "vkCmdCopyBuffer"));
        VK_CHECK(vkCmdDispatch = (PFN_vkCmdDispatch) vkGetInstanceProcAddr(vkInstance, "vkCmdDispatch"));
        VK_CHECK(vkCmdPipelineBarrier = (PFN_vkCmdPipelineBarrier) vkGetInstanceProcAddr(vkInstance, "vkCmdPipelineBarrier"));
        VK_CHECK(vkCmdPushConstants = (PFN_vkCmdPushConstants) vkGetInstanceProcAddr(vkInstance, "vkCmdPushConstants"));
        VK_CHECK(vkCmdResetQueryPool = (PFN_vkCmdResetQueryPool) vkGetInstanceProcAddr(vkInstance, "vkCmdResetQueryPool"));
        VK_CHECK(vkCmdWriteTimestamp = (PFN_vkCmdWriteTimestamp) vkGetInstanceProcAddr(vkInstance, "vkCmdWriteTimestamp"));
        VK_CHECK(vkCreateBuffer = (PFN_vkCreateBuffer) vkGetInstanceProcAddr(vkInstance, "vkCreateBuffer"));
        VK_CHECK(vkCreateCommandPool = (PFN_vkCreateCommandPool) vkGetInstanceProcAddr(vkInstance, "vkCreateCommandPool"));
        VK_CHECK(vkCreateComputePipelines = (PFN_vkCreateComputePipelines) vkGetInstanceProcAddr(vkInstance, "vkCreateComputePipelines"));
        VK_CHECK(vkCreateDescriptorPool = (PFN_vkCreateDescriptorPool) vkGetInstanceProcAddr(vkInstance, "vkCreateDescriptorPool"));
        VK_CHECK(vkCreateDescriptorSetLayout = (PFN_vkCreateDescriptorSetLayout) vkGetInstanceProcAddr(vkInstance, "vkCreateDescriptorSetLayout"));
        VK_CHECK(vkCreateDevice = (PFN_vkCreateDevice) vkGetInstanceProcAddr(vkInstance, "vkCreateDevice"));
        VK_CHECK(vkCreateFence = (PFN_vkCreateFence) vkGetInstanceProcAddr(vkInstance, "vkCreateFence"));
        VK_CHECK(vkCreatePipelineLayout = (PFN_vkCreatePipelineLayout) vkGetInstanceProcAddr(vkInstance, "vkCreatePipelineLayout"));
        VK_CHECK(vkCreateQueryPool = (PFN_vkCreateQueryPool) vkGetInstanceProcAddr(vkInstance, "vkCreateQueryPool"));
        VK_CHECK(vkCreateSemaphore = (PFN_vkCreateSemaphore) vkGetInstanceProcAddr(vkInstance, "vkCreateSemaphore"));
        VK_CHECK(vkCreateShaderModule = (PFN_vkCreateShaderModule) vkGetInstanceProcAddr(vkInstance, "vkCreateShaderModule"));
        VK_CHECK(vkDestroyBuffer = (PFN_vkDestroyBuffer) vkGetInstanceProcAddr(vkInstance, "vkDestroyBuffer"));
        VK_CHECK(vkDestroyCommandPool = (PFN_vkDestroyCommandPool) vkGetInstanceProcAddr(vkInstance, "vkDestroyCommandPool"));
        VK_CHECK(vkDestroyDescriptorPool = (PFN_vkDestroyDescriptorPool) vkGetInstanceProcAddr(vkInstance, "vkDestroyDescriptorPool"));
        VK_CHECK(vkDestroyDescriptorSetLayout = (PFN_vkDestroyDescriptorSetLayout) vkGetInstanceProcAddr(vkInstance, "vkDestroyDescriptorSetLayout"));
        VK_CHECK(vkDestroyDevice = (PFN_vkDestroyDevice) vkGetInstanceProcAddr(vkInstance, "vkDestroyDevice"));
        VK_CHECK(vkDestroyEvent = (PFN_vkDestroyEvent) vkGetInstanceProcAddr(vkInstance, "vkDestroyEvent"));
        VK_CHECK(vkDestroyFence = (PFN_vkDestroyFence) vkGetInstanceProcAddr(vkInstance, "vkDestroyFence"));
        VK_CHECK(vkDestroyInstance = (PFN_vkDestroyInstance) vkGetInstanceProcAddr(vkInstance, "vkDestroyInstance"));
        VK_CHECK(vkDestroyPipeline = (PFN_vkDestroyPipeline) vkGetInstanceProcAddr(vkInstance, "vkDestroyPipeline"));
        VK_CHECK(vkDestroyPipelineLayout = (PFN_vkDestroyPipelineLayout) vkGetInstanceProcAddr(vkInstance, "vkDestroyPipelineLayout"));
        VK_CHECK(vkDestroyQueryPool = (PFN_vkDestroyQueryPool) vkGetInstanceProcAddr(vkInstance, "vkDestroyQueryPool"));
        VK_CHECK(vkDestroySemaphore = (PFN_vkDestroySemaphore) vkGetInstanceProcAddr(vkInstance, "vkDestroySemaphore"));
        VK_CHECK(vkDestroyShaderModule = (PFN_vkDestroyShaderModule) vkGetInstanceProcAddr(vkInstance, "vkDestroyShaderModule"));
        VK_CHECK(vkEndCommandBuffer = (PFN_vkEndCommandBuffer) vkGetInstanceProcAddr(vkInstance, "vkEndCommandBuffer"));
        VK_CHECK(vkEnumeratePhysicalDevices = (PFN_vkEnumeratePhysicalDevices) vkGetInstanceProcAddr(vkInstance, "vkEnumeratePhysicalDevices"));
        VK_CHECK(vkFlushMappedMemoryRanges = (PFN_vkFlushMappedMemoryRanges) vkGetInstanceProcAddr(vkInstance, "vkFlushMappedMemoryRanges"));
        VK_CHECK(vkFreeCommandBuffers = (PFN_vkFreeCommandBuffers) vkGetInstanceProcAddr(vkInstance, "vkFreeCommandBuffers"));
        VK_CHECK(vkFreeDescriptorSets = (PFN_vkFreeDescriptorSets) vkGetInstanceProcAddr(vkInstance, "vkFreeDescriptorSets"));
        VK_CHECK(vkFreeMemory = (PFN_vkFreeMemory) vkGetInstanceProcAddr(vkInstance, "vkFreeMemory"));
        VK_CHECK(vkGetBufferMemoryRequirements = (PFN_vkGetBufferMemoryRequirements) vkGetInstanceProcAddr(vkInstance, "vkGetBufferMemoryRequirements"));
        VK_CHECK(vkGetDeviceQueue = (PFN_vkGetDeviceQueue) vkGetInstanceProcAddr(vkInstance, "vkGetDeviceQueue"));
        VK_CHECK(vkGetPhysicalDeviceMemoryProperties = (PFN_vkGetPhysicalDeviceMemoryProperties) vkGetInstanceProcAddr(vkInstance, "vkGetPhysicalDeviceMemoryProperties"));
        VK_CHECK(vkGetPhysicalDeviceProperties = (PFN_vkGetPhysicalDeviceProperties) vkGetInstanceProcAddr(vkInstance, "vkGetPhysicalDeviceProperties"));
        VK_CHECK(vkGetPhysicalDeviceQueueFamilyProperties = (PFN_vkGetPhysicalDeviceQueueFamilyProperties) vkGetInstanceProcAddr(vkInstance, "vkGetPhysicalDeviceQueueFamilyProperties"));
        VK_CHECK(vkGetQueryPoolResults = (PFN_vkGetQueryPoolResults) vkGetInstanceProcAddr(vkInstance, "vkGetQueryPoolResults"));
        VK_CHECK(vkInvalidateMappedMemoryRanges = (PFN_vkInvalidateMappedMemoryRanges) vkGetInstanceProcAddr(vkInstance, "vkInvalidateMappedMemoryRanges"));
        VK_CHECK(vkMapMemory = (PFN_vkMapMemory) vkGetInstanceProcAddr(vkInstance, "vkMapMemory"));
        VK_CHECK(vkQueueSubmit = (PFN_vkQueueSubmit) vkGetInstanceProcAddr(vkInstance, "vkQueueSubmit"));
        VK_CHECK(vkQueueWaitIdle = (PFN_vkQueueWaitIdle) vkGetInstanceProcAddr(vkInstance, "vkQueueWaitIdle"));
        VK_CHECK(vkResetFences = (PFN_vkResetFences) vkGetInstanceProcAddr(vkInstance, "vkResetFences"));
        VK_CHECK(vkUnmapMemory = (PFN_vkUnmapMemory) vkGetInstanceProcAddr(vkInstance, "vkUnmapMemory"));
        VK_CHECK(vkUpdateDescriptorSets = (PFN_vkUpdateDescriptorSets) vkGetInstanceProcAddr(vkInstance, "vkUpdateDescriptorSets"));
        VK_CHECK(vkWaitForFences = (PFN_vkWaitForFences) vkGetInstanceProcAddr(vkInstance, "vkWaitForFences"));
        return true;
    }
}

PFN_vkAllocateCommandBuffers vkAllocateCommandBuffers;
PFN_vkAllocateDescriptorSets vkAllocateDescriptorSets;
PFN_vkAllocateMemory vkAllocateMemory;
PFN_vkBeginCommandBuffer vkBeginCommandBuffer;
PFN_vkBindBufferMemory vkBindBufferMemory;
PFN_vkCmdBindDescriptorSets vkCmdBindDescriptorSets;
PFN_vkCmdBindPipeline vkCmdBindPipeline;
PFN_vkCmdCopyBuffer vkCmdCopyBuffer;
PFN_vkCmdDispatch vkCmdDispatch;
PFN_vkCmdPipelineBarrier vkCmdPipelineBarrier;
PFN_vkCmdPushConstants vkCmdPushConstants;
PFN_vkCmdResetQueryPool vkCmdResetQueryPool;
PFN_vkCmdWriteTimestamp vkCmdWriteTimestamp;
PFN_vkCreateBuffer vkCreateBuffer;
PFN_vkCreateCommandPool vkCreateCommandPool;
PFN_vkCreateComputePipelines vkCreateComputePipelines;
PFN_vkCreateDescriptorPool vkCreateDescriptorPool;
PFN_vkCreateDescriptorSetLayout vkCreateDescriptorSetLayout;
PFN_vkCreateDevice vkCreateDevice;
PFN_vkCreateFence vkCreateFence;
PFN_vkCreateInstance vkCreateInstance;
PFN_vkCreatePipelineLayout vkCreatePipelineLayout;
PFN_vkCreateQueryPool vkCreateQueryPool;
PFN_vkCreateSemaphore vkCreateSemaphore;
PFN_vkCreateShaderModule vkCreateShaderModule;
PFN_vkDestroyBuffer vkDestroyBuffer;
PFN_vkDestroyCommandPool vkDestroyCommandPool;
PFN_vkDestroyDescriptorPool vkDestroyDescriptorPool;
PFN_vkDestroyDescriptorSetLayout vkDestroyDescriptorSetLayout;
PFN_vkDestroyDevice vkDestroyDevice;
PFN_vkDestroyEvent vkDestroyEvent;
PFN_vkDestroyFence vkDestroyFence;
PFN_vkDestroyInstance vkDestroyInstance;
PFN_vkDestroyPipeline vkDestroyPipeline;
PFN_vkDestroyPipelineLayout vkDestroyPipelineLayout;
PFN_vkDestroyQueryPool vkDestroyQueryPool;
PFN_vkDestroySemaphore vkDestroySemaphore;
PFN_vkDestroyShaderModule vkDestroyShaderModule;
PFN_vkEndCommandBuffer vkEndCommandBuffer;
PFN_vkEnumerateInstanceVersion vkEnumerateInstanceVersion;
PFN_vkEnumeratePhysicalDevices vkEnumeratePhysicalDevices;
PFN_vkFlushMappedMemoryRanges vkFlushMappedMemoryRanges;
PFN_vkFreeCommandBuffers vkFreeCommandBuffers;
PFN_vkFreeDescriptorSets vkFreeDescriptorSets;
PFN_vkFreeMemory vkFreeMemory;
PFN_vkGetBufferMemoryRequirements vkGetBufferMemoryRequirements;
PFN_vkGetDeviceQueue vkGetDeviceQueue;
PFN_vkGetInstanceProcAddr vkGetInstanceProcAddr;
PFN_vkGetPhysicalDeviceMemoryProperties vkGetPhysicalDeviceMemoryProperties;
PFN_vkGetPhysicalDeviceProperties vkGetPhysicalDeviceProperties;
PFN_vkGetPhysicalDeviceQueueFamilyProperties vkGetPhysicalDeviceQueueFamilyProperties;
PFN_vkGetQueryPoolResults vkGetQueryPoolResults;
PFN_vkInvalidateMappedMemoryRanges vkInvalidateMappedMemoryRanges;
PFN_vkMapMemory vkMapMemory;
PFN_vkQueueSubmit vkQueueSubmit;
PFN_vkQueueWaitIdle vkQueueWaitIdle;
PFN_vkResetFences vkResetFences;
PFN_vkUnmapMemory vkUnmapMemory;
PFN_vkUpdateDescriptorSets vkUpdateDescriptorSets;
PFN_vkWaitForFences vkWaitForFences;
