package com.hypermagik.spectrum.analyzer

import android.content.Context
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.opengl.GLES20
import android.os.Bundle
import androidx.core.graphics.alpha
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import com.hypermagik.spectrum.Preferences
import com.hypermagik.spectrum.R
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

class FFT(private val context: Context, private val preferences: Preferences) {
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

    private var color: FloatArray
    private var fillColor: FloatArray
    private var peakHoldColor: FloatArray
    private lateinit var vertexBuffer: ByteBuffer
    private lateinit var peakHoldVertexBuffer: ByteBuffer
    private lateinit var drawOrderBuffer: ByteBuffer
    private lateinit var fillDrawOrderBuffer: ByteBuffer

    private val isLandscape = context.resources.configuration.orientation == ORIENTATION_LANDSCAPE

    private var fftSize = preferences.fftSize
    private var viewHeight = 0
    private var xScale = 1.0f
    private var xTranslate = 0.0f
    private var minY = 0.0f
    private var maxY = 1.0f
    private var minDB = 0.0f
    private var maxDB = 100.0f
    private var isPeakHoldEnabled = true
    private var sizeChanged = true
    private var scaleChanged = true

    init {
        val color = context.resources.getColor(R.color.fft, null)
        this.color = floatArrayOf(color.red / 255f, color.green / 255f, color.blue / 255f, color.alpha / 255f)
        fillColor = floatArrayOf(color.red / 255f, color.green / 255f, color.blue / 255f, 0.05f * color.alpha / 255f)

        val peakHoldColor = context.resources.getColor(R.color.fft_peak_hold, null)
        this.peakHoldColor = floatArrayOf(peakHoldColor.red / 255f, peakHoldColor.green / 255f, peakHoldColor.blue / 255f, peakHoldColor.alpha / 255f)

        createBuffers()
    }

    private fun createBuffers() {
        val vboCapacity = preferences.fftSize * coordsPerVertex * 2 /* (n, 0) for fill */ * Float.SIZE_BYTES
        vertexBuffer = ByteBuffer.allocateDirect(vboCapacity).order(ByteOrder.nativeOrder())

        for (i in 0 until fftSize) {
            // Initialize to zero samples.
            vertexBuffer.putFloat(i.toFloat())
            vertexBuffer.putFloat(0.0f)
            vertexBuffer.putFloat(1.0f)
        }

        // Extra vertices at (i, yMin) for drawing fill area.
        for (i in 0 until fftSize) {
            vertexBuffer.putFloat(1.0f * i / (fftSize - 1))
            vertexBuffer.putFloat(minY)
            vertexBuffer.putFloat(2.0f)
        }

        vertexBuffer.rewind()

        if (isPeakHoldEnabled) {
            peakHoldVertexBuffer = ByteBuffer.allocateDirect(vboCapacity / 2).order(ByteOrder.nativeOrder())

            for (i in 0 until fftSize) {
                peakHoldVertexBuffer.putFloat(i.toFloat())
                peakHoldVertexBuffer.putFloat(0.0f)
                peakHoldVertexBuffer.putFloat(1.0f)
            }

            peakHoldVertexBuffer.rewind()
        }

        var vdoCapacity = (fftSize - 1) * 2 /* vertices */ * Int.SIZE_BYTES
        drawOrderBuffer = ByteBuffer.allocateDirect(vdoCapacity).order(ByteOrder.nativeOrder())

        // Line segments.
        for (i in 0 until fftSize - 1) {
            drawOrderBuffer.putInt(i)
            drawOrderBuffer.putInt(i + 1)
        }

        drawOrderBuffer.rewind()

        vdoCapacity = (fftSize - 1) * 6 /* vertices */ * Int.SIZE_BYTES
        fillDrawOrderBuffer = ByteBuffer.allocateDirect(vdoCapacity).order(ByteOrder.nativeOrder())

        // Fill triangles.
        for (i in 0 until fftSize - 1) {
            fillDrawOrderBuffer.putInt(i)
            fillDrawOrderBuffer.putInt(i + fftSize)
            fillDrawOrderBuffer.putInt(i + 1 + fftSize)
            fillDrawOrderBuffer.putInt(i)
            fillDrawOrderBuffer.putInt(i + 1 + fftSize)
            fillDrawOrderBuffer.putInt(i + 1)
        }

        fillDrawOrderBuffer.rewind()
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
        if (viewHeight != height) {
            viewHeight = height

            // FFT is set to full height in landscape mode
            // and to half height in portrait mode.
            minY = if (isLandscape) 0.0f else 0.5f
            maxY = 1.0f - Info.HEIGHT * context.resources.displayMetrics.density / height
        }

        updateFFTSize(fftSize)
    }

    fun saveInstanceState(bundle: Bundle) {
        val vertices = FloatArray(fftSize * coordsPerVertex)
        vertexBuffer.asFloatBuffer().get(vertices)
        bundle.putFloatArray("vertices", vertices)

        val peakHoldVertices = FloatArray(fftSize * coordsPerVertex)
        peakHoldVertexBuffer.asFloatBuffer().get(peakHoldVertices)
        bundle.putFloatArray("peakHoldVertices", peakHoldVertices)
    }

    fun restoreInstanceState(bundle: Bundle) {
        val restoredVertices = bundle.getFloatArray("vertices")
        val restoredPeakHoldVertices = bundle.getFloatArray("peakHoldVertices")

        if (restoredVertices != null) {
            updateFFTSize(restoredVertices.size / coordsPerVertex)

            // Restore from saved state.
            vertexBuffer.asFloatBuffer().put(restoredVertices)

            restoredPeakHoldVertices?.also {
                peakHoldVertexBuffer.asFloatBuffer().put(it)
            }
        }
    }

    private fun updateFFTSize(size: Int) {
        val bufferSize = vertexBuffer.capacity() / coordsPerVertex / 2 / Float.SIZE_BYTES
        if (bufferSize != size) {
            fftSize = size
            createBuffers()
            sizeChanged = true
        }
    }

    fun update(magnitudes: FloatArray) {
        updateFFTSize(magnitudes.size)

        // TODO: compute entire FFT on the GPU.
        // - https://community.khronos.org/t/spectrogram-and-fft-using-opengl/76933/13
        // - https://github.com/bane9/OpenGLFFT/tree/main/OpenGLFFT

        val peakHoldDecay = 1.0f - preferences.getPeakHoldDecayFactor()

        // Scaling is done in the vertex shader.
        for (i in magnitudes.indices) {
            val bufferIndex = (i * coordsPerVertex + 1) * Float.SIZE_BYTES
            val magnitude = magnitudes[i]

            vertexBuffer.putFloat(bufferIndex, magnitude)

            if (isPeakHoldEnabled) {
                var peak = peakHoldVertexBuffer.getFloat(bufferIndex)
                peakHoldVertexBuffer.putFloat(bufferIndex, max(peak * peakHoldDecay, magnitude))
            }
        }

        vertexBuffer.rewind()

        if (isPeakHoldEnabled) {
            peakHoldVertexBuffer.rewind()
        }
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
            vPosition, coordsPerVertex, GLES20.GL_FLOAT, false, Float.SIZE_BYTES * coordsPerVertex, vertexBuffer
        )

        // Draw fill area below the lines.
        GLES20.glUniform4fv(vColor, 1, fillColor, 0)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, (fftSize - 1) * 6, GLES20.GL_UNSIGNED_INT, fillDrawOrderBuffer)

        // Draw the FFT lines.
        GLES20.glUniform4fv(vColor, 1, color, 0)
        GLES20.glDrawElements(GLES20.GL_LINES, (fftSize - 1) * 2, GLES20.GL_UNSIGNED_INT, drawOrderBuffer)

        // Draw the FFT hold peaks.
        if (isPeakHoldEnabled) {
            GLES20.glEnableVertexAttribArray(vPosition)
            GLES20.glVertexAttribPointer(
                vPosition, coordsPerVertex, GLES20.GL_FLOAT, false, Float.SIZE_BYTES * coordsPerVertex, peakHoldVertexBuffer
            )

            GLES20.glUniform4fv(vColor, 1, peakHoldColor, 0)
            GLES20.glDrawElements(GLES20.GL_LINES, (fftSize - 1) * 2, GLES20.GL_UNSIGNED_INT, drawOrderBuffer)
        }

        GLES20.glDisableVertexAttribArray(vPosition)
    }
}
