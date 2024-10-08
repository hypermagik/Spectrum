package com.hypermagik.spectrum.lib.gpu

import android.content.Context
import android.opengl.EGL14
import android.opengl.EGL15
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES31
import android.os.Build
import android.util.Log

class GLES private constructor() {
    companion object {
        val INSTANCE: GLES by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { GLES() }
    }

    private var context: Context? = null

    private var glDisplay: EGLDisplay? = null
    private var glContext: EGLContext? = null
    private var glSurface: EGLSurface? = null

    private var programs = HashMap<Int, Int>()

    private external fun enableGLDebugMessages()

    init {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                throw NotImplementedError("GLES 3.2 not supported on API < 29")
            }

            glDisplay = checkGLError { EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY) }
            check(glDisplay != null && glDisplay != EGL14.EGL_NO_DISPLAY)

            val version = intArrayOf(3, 1)
            check(checkGLError { EGL14.eglInitialize(glDisplay, version, 0, version, 1) })

            val configSpec = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_CONFIG_CAVEAT, EGL14.EGL_NONE,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE
            )

            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            check(checkGLError { EGL14.eglChooseConfig(glDisplay, configSpec, 0, configs, 0, 1, numConfigs, 0) })
            check(numConfigs[0] > 0)
            check(configs[0] != null)

            val glConfig = configs[0]
            val contextAttribs = intArrayOf(
                EGL15.EGL_CONTEXT_MAJOR_VERSION, 3,
                EGL15.EGL_CONTEXT_MINOR_VERSION, 2,
                EGL15.EGL_CONTEXT_OPENGL_DEBUG, EGL14.EGL_TRUE,
                EGLExt.EGL_CONTEXT_FLAGS_KHR, EGL14.EGL_TRUE,
                EGL14.EGL_NONE
            )
            glContext = checkGLError { EGL14.eglCreateContext(glDisplay, glConfig, EGL14.EGL_NO_CONTEXT, contextAttribs, 0) }

            if (glContext == null || glContext == EGL14.EGL_NO_CONTEXT) {
                val contextAttribsFallback = intArrayOf(
                    EGL15.EGL_CONTEXT_MAJOR_VERSION, 3,
                    EGL15.EGL_CONTEXT_MINOR_VERSION, 2,
                    EGL14.EGL_NONE
                )
                glContext = checkGLError { EGL14.eglCreateContext(glDisplay, glConfig, EGL14.EGL_NO_CONTEXT, contextAttribsFallback, 0) }
            }

            check(glContext != null && glContext != EGL14.EGL_NO_CONTEXT)

            val surfaceAttribs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
            glSurface = checkGLError { EGL14.eglCreatePbufferSurface(glDisplay, glConfig, surfaceAttribs, 0) }
            check(glSurface != null && glSurface != EGL14.EGL_NO_SURFACE)

            if (GL_DEBUG) {
                checkGLError { EGL14.eglMakeCurrent(glDisplay, glSurface, glSurface, glContext) }
                enableGLDebugMessages()
            }
        } catch (e: Exception) {
            Log.e("GLES", "Failed to initialize GLES 3.2", e)
            close()
        }
    }

    fun close() {
        if (glSurface != null && glSurface != EGL14.EGL_NO_SURFACE) {
            EGL14.eglDestroySurface(glDisplay, glSurface)
        }
        if (glContext != null && glContext != EGL14.EGL_NO_CONTEXT) {
            EGL14.eglDestroyContext(glDisplay, glContext)
        }
        if (glDisplay != null && glDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglTerminate(glDisplay)
        }

        glSurface = null
        glContext = null
        glDisplay = null
    }

    fun isAvailable(): Boolean {
        return glSurface != null
    }

    fun makeCurrent(context: Context) {
        this.context = context

        checkGLError { EGL14.eglMakeCurrent(glDisplay, glSurface, glSurface, glContext) }
    }

    fun getProgram(id: Int): Int {
        if (programs.containsKey(id)) {
            return programs[id]!!
        }

        val text = context!!.resources.openRawResource(id).bufferedReader().readText()

        val shader = checkGLError { GLES31.glCreateShader(GLES31.GL_COMPUTE_SHADER) }
        checkGLError { GLES31.glShaderSource(shader, text) }
        checkGLError { GLES31.glCompileShader(shader) }

        val program = checkGLError { GLES31.glCreateProgram() }
        checkGLError { GLES31.glAttachShader(program, shader) }
        checkGLError { GLES31.glLinkProgram(program) }

        val status = IntArray(1)
        checkGLError { GLES31.glGetProgramiv(program, GLES31.GL_LINK_STATUS, status, 0) }
        check(status[0] == GLES31.GL_TRUE)

        programs[id] = program

        return program
    }
}