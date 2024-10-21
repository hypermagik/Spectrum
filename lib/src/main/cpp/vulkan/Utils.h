#pragma once

#include <android/log.h>
#include <vulkan/vulkan_core.h>

#define LOG_TAG "VK"
#define LOG(severity, ...) ((void)__android_log_print(ANDROID_LOG_##severity, LOG_TAG, __VA_ARGS__))
#define LOGE(...) LOG(ERROR, __VA_ARGS__)
#define LOGD(...) LOG(DEBUG, __VA_ARGS__)

#define VK_CHECK(condition)                                                     \
    do {                                                                        \
        if (!(condition)) {                                                     \
            LOGE("Check failed at %s:%u - %s", __FILE__, __LINE__, #condition); \
            return false;                                                       \
        }                                                                       \
    } while (0)

#define VK_CHECK_NULL(condition)                                                \
    do {                                                                        \
        if (!(condition)) {                                                     \
            LOGE("Check failed at %s:%u - %s", __FILE__, __LINE__, #condition); \
            return nullptr;                                                     \
        }                                                                       \
    } while (0)

#define VK_CALL(vkMethod, ...)                                                                            \
    do {                                                                                                  \
        auto _result = vkMethod(__VA_ARGS__);                                                             \
        if (_result != VK_SUCCESS) {                                                                      \
            LOGE("%s failed with %s at %s:%u", #vkMethod, vkResultToString(_result), __FILE__, __LINE__); \
            return false;                                                                                 \
        }                                                                                                 \
    } while (0)

const char *vkResultToString(VkResult result);
