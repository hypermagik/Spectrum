#include <jni.h>
#include <android/log.h>

#define GL_GLES_PROTOTYPES 1
#define GL_GLEXT_PROTOTYPES 1

#include <EGL/egl.h>
#include <GLES3/gl32.h>
#include <GLES2/gl2ext.h>

#include <map>

static std::map<GLenum, const char *> glSourceToString = {
        {GL_DEBUG_SOURCE_API,               "API"},
        {GL_DEBUG_SOURCE_WINDOW_SYSTEM,     "Window System"},
        {GL_DEBUG_SOURCE_SHADER_COMPILER,   "Shader Compiler"},
        {GL_DEBUG_SOURCE_THIRD_PARTY,       "Third Party"},
        {GL_DEBUG_SOURCE_APPLICATION,       "Application"},
        {GL_DEBUG_SOURCE_OTHER,             "Other"},
};

static std::map<GLenum, const char *> glTypeToString = {
        {GL_DEBUG_TYPE_ERROR,               "Error"},
        {GL_DEBUG_TYPE_DEPRECATED_BEHAVIOR, "Deprecated Behavior"},
        {GL_DEBUG_TYPE_UNDEFINED_BEHAVIOR,  "Undefined Behavior"},
        {GL_DEBUG_TYPE_PORTABILITY,         "Portability"},
        {GL_DEBUG_TYPE_PERFORMANCE,         "Performance"},
        {GL_DEBUG_TYPE_OTHER,               "Other"},
        {GL_DEBUG_TYPE_MARKER,              "Marker"},
        {GL_DEBUG_TYPE_PUSH_GROUP,          "Push Group"},
        {GL_DEBUG_TYPE_POP_GROUP,           "Pop Group"},
};

static std::map<GLenum, const char *> glSeverityToString = {
        {GL_DEBUG_SEVERITY_HIGH,            "High"},
        {GL_DEBUG_SEVERITY_MEDIUM,          "Medium"},
        {GL_DEBUG_SEVERITY_LOW,             "Low"},
};

static void openGLMessageCallback(GLenum source, GLenum type, GLuint id, GLenum severity, GLsizei, const GLchar *message, const void *) {
    __android_log_print(ANDROID_LOG_ERROR, "GL DEBUG", "[%s/%s/%s] %s", glSourceToString[source], glTypeToString[type], glSeverityToString[severity], message);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_hypermagik_spectrum_lib_gpu_GLES_enableGLDebugMessages(JNIEnv *env, jobject) {
    glEnable(GL_DEBUG_OUTPUT);
    glEnable(GL_DEBUG_OUTPUT_SYNCHRONOUS);
    glEnable(GL_DEBUG_OUTPUT_SYNCHRONOUS_KHR);
    glDebugMessageCallback(openGLMessageCallback, nullptr);
}
