package com.hypermagik.spectrum.analyzer

import android.content.Context
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.opengl.GLES20
import android.os.Bundle
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import com.hypermagik.spectrum.R
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FFT(private val context: Context, private var fftSize: Int) {
    private var vPosition: Int = 0
    private var vColor: Int = 0

    private var uFFTSize: Int = 0
    private var uFFTXScale: Int = 0
    private var uFFTXTranslate: Int = 0
    private var uFFTMinY: Int = 0
    private var uFFTMaxY: Int = 0
    private var uFFTMinDB: Int = 0
    private var uFFTMaxDB: Int = 0

    private var coordsPerVertex = 3

    private var fftColor: FloatArray
    private var fftFillColor: FloatArray
    private lateinit var fftVertexBuffer: ByteBuffer
    private lateinit var fftDrawOrderBuffer: ByteBuffer
    private lateinit var fftFillDrawOrderBuffer: ByteBuffer

    private val isLandscape = context.resources.configuration.orientation == ORIENTATION_LANDSCAPE

    private var height = 0
    private var xScale = 1.0f
    private var xTranslate = 0.0f
    private var minY = 0.0f
    private var maxY = 1.0f
    private var minDB = 0.0f
    private var maxDB = 100.0f
    private var sizeChanged = true
    private var scaleChanged = true
    private var restoredState: FloatArray? = null

    init {
        val color = context.resources.getColor(R.color.fft, null)
        fftColor = floatArrayOf(color.red / 255f, color.green / 255f, color.blue / 255f, 1.0f)
        fftFillColor = floatArrayOf(color.red / 255f, color.green / 255f, color.blue / 255f, 0.05f)
    }

    private fun createBuffers() {
        if (restoredState != null) {
            fftSize = restoredState!!.size / coordsPerVertex
        }

        val vboCapacity = fftSize * coordsPerVertex * 2 /* (n, 0) for fill */ * Float.SIZE_BYTES
        fftVertexBuffer = ByteBuffer.allocateDirect(vboCapacity).order(ByteOrder.nativeOrder())

        if (restoredState != null) {
            // Restore from saved state.
            fftVertexBuffer.asFloatBuffer().put(restoredState)
            fftVertexBuffer.position(fftSize * coordsPerVertex * Float.SIZE_BYTES)
            restoredState = null
        } else for (i in 0 until fftSize) {
            // Initialize to zero samples.
            fftVertexBuffer.putFloat(i.toFloat())
            fftVertexBuffer.putFloat(0.0f)
            fftVertexBuffer.putFloat(1.0f)
        }

        // Extra vertices at (i, yMin) for drawing fill area.
        for (i in 0 until fftSize) {
            fftVertexBuffer.putFloat(1.0f * i / (fftSize - 1))
            fftVertexBuffer.putFloat(minY)
            fftVertexBuffer.putFloat(2.0f)
        }

        fftVertexBuffer.rewind()

        var vdoCapacity = (fftSize - 1) * 2 /* vertices */ * Int.SIZE_BYTES
        fftDrawOrderBuffer = ByteBuffer.allocateDirect(vdoCapacity).order(ByteOrder.nativeOrder())

        // Line segments.
        for (i in 0 until fftSize - 1) {
            fftDrawOrderBuffer.putInt(i)
            fftDrawOrderBuffer.putInt(i + 1)
        }

        fftDrawOrderBuffer.rewind()

        vdoCapacity = (fftSize - 1) * 6 /* vertices */ * Int.SIZE_BYTES
        fftFillDrawOrderBuffer = ByteBuffer.allocateDirect(vdoCapacity).order(ByteOrder.nativeOrder())

        // Fill triangles.
        for (i in 0 until fftSize - 1) {
            fftFillDrawOrderBuffer.putInt(i)
            fftFillDrawOrderBuffer.putInt(i + fftSize)
            fftFillDrawOrderBuffer.putInt(i + 1 + fftSize)
            fftFillDrawOrderBuffer.putInt(i)
            fftFillDrawOrderBuffer.putInt(i + 1 + fftSize)
            fftFillDrawOrderBuffer.putInt(i + 1)
        }

        fftFillDrawOrderBuffer.rewind()
    }

    fun onSurfaceCreated(program: Int) {
        vPosition = GLES20.glGetAttribLocation(program, "vPosition")
        vColor = GLES20.glGetUniformLocation(program, "vColor")

        uFFTSize = GLES20.glGetUniformLocation(program, "fftSize")
        uFFTXScale = GLES20.glGetUniformLocation(program, "fftXscale")
        uFFTXTranslate = GLES20.glGetUniformLocation(program, "fftXtranslate")
        uFFTMinY = GLES20.glGetUniformLocation(program, "fftMinY")
        uFFTMaxY = GLES20.glGetUniformLocation(program, "fftMaxY")
        uFFTMinDB = GLES20.glGetUniformLocation(program, "fftMinDB")
        uFFTMaxDB = GLES20.glGetUniformLocation(program, "fftMaxDB")
    }

    fun onSurfaceChanged(height: Int) {
        if (this.height != height) {
            this.height = height

            // FFT is set to full height in landscape mode
            // and to half height in portrait mode.
            minY = if (isLandscape) 0.0f else 0.5f
            maxY = 1.0f - Info.HEIGHT * context.resources.displayMetrics.density / height

            createBuffers()
        }
    }

    fun saveInstanceState(bundle: Bundle) {
        val vertices = FloatArray(fftSize * coordsPerVertex)
        fftVertexBuffer.asFloatBuffer().get(vertices)
        bundle.putFloatArray("vertices", vertices)
    }

    fun restoreInstanceState(bundle: Bundle) {
        restoredState = bundle.getFloatArray("vertices") ?: return
    }

    fun update(magnitudes: FloatArray) {
        if (fftSize != magnitudes.size) {
            fftSize = magnitudes.size
            createBuffers()
            sizeChanged = true
        }

        // TODO: compute entire FFT on the GPU.
        // - https://community.khronos.org/t/spectrogram-and-fft-using-opengl/76933/13
        // - https://github.com/bane9/OpenGLFFT/tree/main/OpenGLFFT

        // Scaling is done in the vertex shader.
        for (i in magnitudes.indices) {
            fftVertexBuffer.putFloat(i.toFloat())
            fftVertexBuffer.putFloat(magnitudes[i])
            fftVertexBuffer.putFloat(1.0f)
        }

        fftVertexBuffer.rewind()
    }

    fun updateX(scale: Float, translate: Float) {
        this.xScale = scale
        this.xTranslate = translate
        scaleChanged = true
    }

    fun updateY(minDB: Float, maxDB: Float) {
        this.minDB = minDB
        this.maxDB = maxDB
        scaleChanged = true
    }

    fun draw() {
        if (sizeChanged) {
            sizeChanged = false
            GLES20.glUniform1i(uFFTSize, fftSize)
        }

        if (scaleChanged) {
            scaleChanged = false
            GLES20.glUniform1f(uFFTXScale, xScale)
            GLES20.glUniform1f(uFFTXTranslate, xTranslate)
            GLES20.glUniform1f(uFFTMinY, minY)
            GLES20.glUniform1f(uFFTMaxY, maxY)
            GLES20.glUniform1f(uFFTMinDB, minDB)
            GLES20.glUniform1f(uFFTMaxDB, maxDB)
        }

        GLES20.glEnableVertexAttribArray(vPosition)
        GLES20.glVertexAttribPointer(
            vPosition, coordsPerVertex, GLES20.GL_FLOAT, false, Float.SIZE_BYTES * coordsPerVertex, fftVertexBuffer
        )

        // Draw fill area below the lines.
        GLES20.glUniform4fv(vColor, 1, fftFillColor, 0)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, (fftSize - 1) * 6, GLES20.GL_UNSIGNED_INT, fftFillDrawOrderBuffer)

        // Draw lines.
        GLES20.glUniform4fv(vColor, 1, fftColor, 0)
        GLES20.glDrawElements(GLES20.GL_LINES, (fftSize - 1) * 2, GLES20.GL_UNSIGNED_INT, fftDrawOrderBuffer)

        GLES20.glDisableVertexAttribArray(vPosition)
    }
}
