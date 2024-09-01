package com.hypermagik.spectrum.benchmark

import android.opengl.EGL14
import android.opengl.EGL14.EGL_CONTEXT_CLIENT_VERSION
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext
import javax.microedition.khronos.egl.EGLDisplay
import javax.microedition.khronos.egl.EGLSurface

@RunWith(AndroidJUnit4::class)
class GL {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private lateinit var gl: EGL10
    private var glDisplay: EGLDisplay? = null
    private var glContext: EGLContext? = null
    private var glSurface: EGLSurface? = null

    private fun initializeGL() {
        gl = EGLContext.getEGL() as EGL10

        try {
            glDisplay = gl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY)
            assert(glDisplay != null && glDisplay != EGL10.EGL_NO_DISPLAY)

            val version = IntArray(2)
            assert(gl.eglInitialize(glDisplay, version))

            val configSpec = intArrayOf(
                EGL10.EGL_RED_SIZE, 8,
                EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE, 8,
                EGL10.EGL_ALPHA_SIZE, 8,
                EGL10.EGL_DEPTH_SIZE, 8,
                EGL10.EGL_STENCIL_SIZE, 8,
                EGL10.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL10.EGL_NONE
            )

            val numConfigArray = IntArray(1)
            assert(gl.eglChooseConfig(glDisplay, configSpec, null, 0, numConfigArray))

            val numConfigs = numConfigArray[0]
            assert(numConfigs > 0)

            val configs = arrayOfNulls<EGLConfig>(numConfigs)
            assert(gl.eglChooseConfig(glDisplay, configSpec, configs, numConfigs, numConfigArray))

            val glConfig = configs[0]

            val contextAttribs = intArrayOf(EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE)
            glContext = gl.eglCreateContext(glDisplay, glConfig, EGL10.EGL_NO_CONTEXT, contextAttribs)
            assert(glContext != null && glContext != EGL10.EGL_NO_CONTEXT)

            val surfaceAttribs = intArrayOf(EGL10.EGL_WIDTH, 1, EGL10.EGL_HEIGHT, 1, EGL10.EGL_NONE)
            glSurface = gl.eglCreatePbufferSurface(glDisplay, glConfig, surfaceAttribs)
            assert(glSurface != null && glSurface != EGL10.EGL_NO_SURFACE)
        } catch (e: Exception) {
            if (glSurface != null) {
                gl.eglDestroySurface(glDisplay, glSurface)
            }
            if (glContext != null) {
                gl.eglDestroyContext(glDisplay, glContext)
            }
            if (glDisplay != null) {
                gl.eglTerminate(glDisplay)
            }
            throw e
        }
    }

    @Test
    fun gl() {
        initializeGL()

        benchmarkRule.measureRepeated {
            // TODO
        }
    }
}
