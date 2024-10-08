package com.hypermagik.spectrum.lib.gpu

import android.opengl.GLES31
import android.opengl.GLU
import android.util.Log

var GL_DEBUG = true

inline fun <R> checkGLError(block: () -> R): R {
    val v = block()

    if (GL_DEBUG) {
        var error: Int
        var hasError = false

        while (run { error = GLES31.glGetError(); error } != GLES31.GL_NO_ERROR) {
            val stackTrace = Thread.currentThread().stackTrace
            Log.e("GL ERROR", "Error: " + error + " (" + GLU.gluErrorString(error) + "): " + stackTrace[2].toString())
            hasError = true
        }

        check(!hasError)
    }

    return v
}
