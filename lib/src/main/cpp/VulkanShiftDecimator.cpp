#include "vulkan/dsp/ShiftDecimatorSingleQueue.h"
#include "vulkan/dsp/ShiftDecimatorMultiQueue.h"

#include <jni.h>
#include <android/log.h>
#include <android/asset_manager_jni.h>

extern std::unique_ptr<Vulkan::Context> context;

static Vulkan::DSP::Taps getTaps(JNIEnv *env, jobject _taps);

extern "C"
JNIEXPORT jlong JNICALL
Java_com_hypermagik_spectrum_lib_gpu_VulkanShiftDecimator_00024Companion_create(JNIEnv *env, jobject, jobject taps, jboolean forceSingleQueue) {
    if (context == nullptr) {
        return 0;
    }
    return forceSingleQueue || context->queueCount() == 1
           ? (jlong) Vulkan::DSP::ShiftDecimatorSingleQueue::create(context.get(), getTaps(env, taps)).release()
           : (jlong) Vulkan::DSP::ShiftDecimatorMultiQueue::create(context.get(), getTaps(env, taps)).release();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_hypermagik_spectrum_lib_gpu_VulkanShiftDecimator_00024Companion_process(JNIEnv *env, jobject, jlong _instance, jobject samples, jint sampleCount, jfloat phi, jfloat omega) {
    auto *instance = (Vulkan::DSP::ShiftDecimator *) _instance;
    if (instance != nullptr) {
        auto *sampleBuffer = (float *) env->GetDirectBufferAddress(samples);
        instance->process(sampleBuffer, sampleCount, phi, omega);
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_hypermagik_spectrum_lib_gpu_VulkanShiftDecimator_00024Companion_delete(JNIEnv *env, jobject, jlong _instance) {
    auto *instance = (Vulkan::DSP::ShiftDecimator *) _instance;
    delete instance;
}

static Vulkan::DSP::Taps getTaps(JNIEnv *env, jobject taps) {
    auto *ptr = (const uint8_t *) env->GetDirectBufferAddress(taps);

    const int count = *(const int *) ptr;
    ptr += sizeof(int);

    Vulkan::DSP::Taps result(count);

    for (int i = 0; i < count; i++) {
        const int numTaps = *(const int *) ptr;
        ptr += sizeof(int);

        result[i].resize(numTaps);
        memcpy(result[i].data(), ptr, numTaps * sizeof(float));

        ptr += numTaps * sizeof(float);
    }

    return result;
}
