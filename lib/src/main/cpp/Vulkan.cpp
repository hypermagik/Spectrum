#include "vulkan/Context.h"
#include "vulkan/Utils.h"

#include <jni.h>
#include <android/asset_manager_jni.h>
#include <dlfcn.h>

std::unique_ptr<Vulkan::Context> context;

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_hypermagik_spectrum_lib_gpu_Vulkan_00024Companion_createContext(JNIEnv *env, jobject thiz, jboolean debug, jobject _assetManager) {
    void *libVulkanHandle = dlopen("libvulkan.so", RTLD_NOW);
    VK_CHECK(libVulkanHandle != nullptr);
    VK_CHECK(Vulkan::initializePFN(libVulkanHandle));

    context = Vulkan::Context::create(debug, AAssetManager_fromJava(env, _assetManager));
    return context != nullptr;
}
