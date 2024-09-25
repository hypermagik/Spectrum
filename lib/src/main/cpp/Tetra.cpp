#include <jni.h>
#include <string.h>

extern "C" {
#include "osmo-tetra/tetra_common.h"
#include "osmo-tetra/crypto/tetra_crypto.h"
#include "osmo-tetra/phy/tetra_burst_sync.h"
}

static tetra_mac_state mac_state;
static tetra_crypto_state crypto_state;
static tetra_rx_state rx_state;

extern "C"
JNIEXPORT void JNICALL
Java_com_hypermagik_spectrum_lib_digital_Tetra_start(JNIEnv *env, jobject thiz) {
    memset(&mac_state, 0, sizeof(mac_state));
    memset(&crypto_state, 0, sizeof(crypto_state));
    memset(&rx_state, 0, sizeof(rx_state));

    tetra_mac_state_init(&mac_state);
    tetra_crypto_state_init(&crypto_state);

    mac_state.tcs = &crypto_state;
    rx_state.burst_cb_priv = &mac_state;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_hypermagik_spectrum_lib_digital_Tetra_stop(JNIEnv *env, jobject thiz) {
}

extern "C"
JNIEXPORT void JNICALL
Java_com_hypermagik_spectrum_lib_digital_Tetra_process(JNIEnv *env, jobject thiz, jobject buffer, jint length) {
    tetra_burst_sync_in(&rx_state, (uint8_t *) env->GetDirectBufferAddress(buffer), length);
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_hypermagik_spectrum_lib_digital_Tetra_isLocked(JNIEnv *env, jobject thiz) {
    return rx_state.state == RX_S_LOCKED;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_hypermagik_spectrum_lib_digital_Tetra_getCC(JNIEnv *env, jobject thiz) {
    return mac_state.cc;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_hypermagik_spectrum_lib_digital_Tetra_getMCC(JNIEnv *env, jobject thiz) {
    return mac_state.mcc;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_hypermagik_spectrum_lib_digital_Tetra_getMNC(JNIEnv *env, jobject thiz) {
    return mac_state.mnc;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_hypermagik_spectrum_lib_digital_Tetra_getDLFrequency(JNIEnv *env, jobject thiz) {
    return mac_state.dl_freq;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_hypermagik_spectrum_lib_digital_Tetra_getULFrequency(JNIEnv *env, jobject thiz) {
    return mac_state.ul_freq;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_hypermagik_spectrum_lib_digital_Tetra_getTimeslotContent(JNIEnv *env, jobject thiz) {
    return (mac_state.timeslot_content[0] << 0) |
           (mac_state.timeslot_content[1] << 8) |
           (mac_state.timeslot_content[2] << 16) |
           (mac_state.timeslot_content[3] << 24);
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_hypermagik_spectrum_lib_digital_Tetra_getServiceDetails(JNIEnv *env, jobject thiz) {
    return mac_state.service_details;
}
