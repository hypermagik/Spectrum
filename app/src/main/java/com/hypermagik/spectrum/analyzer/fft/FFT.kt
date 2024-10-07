package com.hypermagik.spectrum.analyzer.fft

import android.content.Context
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

class FFT(context: Context, private val preferences: Preferences) {
    val grid = Grid(context, this)
    private val peaks = Peaks(context, this)

    private var vPosition = 0
    private var vColor = 0

    private var uFFTSize = 0
    private var uFFTXScale = 0
    private var uFFTXTranslate = 0
    private var uFFTMinY = 0
    private var uFFTMaxY = 0
    private var uFFTMinDB = 0
    private var uFFTMaxDB = 0

    val coordsPerVertex = 3

    private var color: FloatArray
    private var fillColor: FloatArray
    private var peakHoldColor: FloatArray

    private lateinit var vertexArray: FloatArray
    private lateinit var vertexBuffer: ByteBuffer
    private lateinit var peakHoldVertexArray: FloatArray
    private lateinit var peakHoldVertexBuffer: ByteBuffer

    private lateinit var fillDrawOrderBuffer: ByteBuffer

    var fftSize = preferences.fftSize
        private set
    var viewWidth = 0
        private set
    var viewHeight = 0
        private set
    var xScale = 1.0f
        private set

    private var xTranslate = 0.0f

    var minY = 0.0f
        private set
    var maxY = 1.0f
        private set
    var minDB = 0.0f
        private set
    var maxDB = 100.0f
        private set

    init {
        val color = context.resources.getColor(R.color.fft, null)
        this.color = floatArrayOf(color.red / 255f, color.green / 255f, color.blue / 255f, color.alpha / 255f)
        fillColor = floatArrayOf(color.red / 255f, color.green / 255f, color.blue / 255f, 0.05f * color.alpha / 255f)

        val peakHoldColor = context.resources.getColor(R.color.fft_peak_hold, null)
        this.peakHoldColor = floatArrayOf(peakHoldColor.red / 255f, peakHoldColor.green / 255f, peakHoldColor.blue / 255f, peakHoldColor.alpha / 255f)

        createBuffers()
    }

    private fun createBuffers() {
        val vboCapacity = fftSize * coordsPerVertex * 2 /* (n, 0) for fill */
        vertexArray = FloatArray(vboCapacity)
        vertexBuffer = ByteBuffer.allocateDirect(vboCapacity * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())

        for (i in 0 until fftSize) {
            // Initialize to zero samples.
            vertexArray[3 * i + 0] = i.toFloat()
            vertexArray[3 * i + 1] = 0.0f
            vertexArray[3 * i + 2] = 1.0f
        }

        // Extra vertices at (i, yMin) for drawing fill area.
        for (i in 0 until fftSize) {
            vertexArray[3 * (fftSize + i) + 0] = 1.0f * i / (fftSize - 1)
            vertexArray[3 * (fftSize + i) + 1] = minY
            vertexArray[3 * (fftSize + i) + 2] = 2.0f
        }

        vertexBuffer.asFloatBuffer().put(vertexArray)

        peakHoldVertexArray = FloatArray(vboCapacity / 2)
        peakHoldVertexBuffer = ByteBuffer.allocateDirect(vboCapacity / 2 * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())

        for (i in 0 until fftSize) {
            peakHoldVertexArray[3 * i + 0] = i.toFloat()
            peakHoldVertexArray[3 * i + 1] = 0.0f
            peakHoldVertexArray[3 * i + 2] = 1.0f
        }

        peakHoldVertexBuffer.asFloatBuffer().put(peakHoldVertexArray)

        val vdoCapacity = fftSize * 2 /* vertices */ * Int.SIZE_BYTES
        fillDrawOrderBuffer = ByteBuffer.allocateDirect(vdoCapacity).order(ByteOrder.nativeOrder())

        // Fill triangles.
        for (i in 0 until fftSize) {
            fillDrawOrderBuffer.putInt(i + fftSize)
            fillDrawOrderBuffer.putInt(i)
        }

        fillDrawOrderBuffer.rewind()
    }

    fun getBufferForPeaks(): ByteBuffer {
        return if (preferences.peakHoldEnabled) peakHoldVertexBuffer else vertexBuffer
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

        grid.onSurfaceCreated(program)
        peaks.onSurfaceCreated(program)
    }

    fun onSurfaceChanged(width: Int, height: Int, top: Float, bottom: Float) {
        this.viewWidth = width
        this.viewHeight = height
        this.minY = bottom
        this.maxY = top

        updateFFTSize(fftSize)

        grid.onSurfaceChanged(width, height, top, bottom)
    }

    fun saveInstanceState(bundle: Bundle) {
        bundle.putFloatArray("vertexArray", vertexArray)
        bundle.putFloatArray("peakHoldVertexArray", peakHoldVertexArray)
    }

    fun restoreInstanceState(bundle: Bundle) {
        val restoredVertexArray = bundle.getFloatArray("vertexArray")
        val restoredPeakHoldVertexArray = bundle.getFloatArray("peakHoldVertexArray")

        if (restoredVertexArray != null) {
            updateFFTSize(restoredVertexArray.size / coordsPerVertex / 2)

            // Restore from saved state.
            vertexArray = restoredVertexArray
            vertexBuffer.asFloatBuffer().put(vertexArray)

            restoredPeakHoldVertexArray?.also {
                peakHoldVertexArray = restoredPeakHoldVertexArray
                peakHoldVertexBuffer.asFloatBuffer().put(peakHoldVertexArray)
            }
        }
    }

    fun reset() {
        for (i in 0 until fftSize) {
            val bufferIndex = i * coordsPerVertex + 1

            vertexArray[bufferIndex] = 0.0f
            peakHoldVertexArray[bufferIndex] = 0.0f

            vertexBuffer.asFloatBuffer().put(vertexArray)
            peakHoldVertexBuffer.asFloatBuffer().put(peakHoldVertexArray)
        }
    }

    private fun updateFFTSize(size: Int) {
        if (fftSize != size) {
            fftSize = size
            createBuffers()
        }
    }

    fun update(magnitudes: FloatArray, size: Int) {
        updateFFTSize(size)

        val peakHoldEnabled = preferences.peakHoldEnabled

        var peakHoldDecay = 0.0f
        if (peakHoldEnabled) {
            peakHoldDecay = 1.0f - preferences.getPeakHoldDecayFactor()
        }

        // TODO: compute entire FFT on the GPU.
        // - https://community.khronos.org/t/spectrogram-and-fft-using-opengl/76933/13
        // - https://github.com/bane9/OpenGLFFT/tree/main/OpenGLFFT

        // Scaling is done in the vertex shader.
        for (i in 0 until size) {
            val bufferIndex = i * coordsPerVertex + 1
            val magnitude = magnitudes[i]

            vertexArray[bufferIndex] = magnitude

            if (peakHoldEnabled) {
                var peak = peakHoldVertexArray[bufferIndex]
                if (peak.isNaN() || peak.isInfinite()) {
                    peak = magnitude
                }
                peakHoldVertexArray[bufferIndex] = max(peak * peakHoldDecay, magnitude)
            }
        }

        vertexBuffer.asFloatBuffer().put(vertexArray)

        if (peakHoldEnabled) {
            peakHoldVertexBuffer.asFloatBuffer().put(peakHoldVertexArray)
        }

        if (preferences.peakIndicatorEnabled) {
            peaks.update()
        }
    }

    fun updateX(scale: Float, translate: Float, sourceMinFrequency: Double, sourceMaxFrequency: Double, viewMinFrequency: Double, viewMaxFrequency: Double) {
        xScale = scale
        xTranslate = translate

        grid.setFrequencyRange(viewMinFrequency, viewMaxFrequency)
        peaks.setFrequencyRange(sourceMinFrequency, sourceMaxFrequency, viewMinFrequency, viewMaxFrequency)
    }

    fun updateY(minDB: Float, maxDB: Float) {
        this.minDB = minDB
        this.maxDB = maxDB

        grid.updateY()
        peaks.forceUpdate()
    }

    fun draw() {
        grid.drawBackground()

        GLES20.glUniform1i(uFFTSize, fftSize)
        GLES20.glUniform1f(uFFTXScale, xScale)
        GLES20.glUniform1f(uFFTXTranslate, xTranslate)
        GLES20.glUniform1f(uFFTMinY, minY)
        GLES20.glUniform1f(uFFTMaxY, maxY)
        GLES20.glUniform1f(uFFTMinDB, minDB)
        GLES20.glUniform1f(uFFTMaxDB, maxDB)

        GLES20.glEnableVertexAttribArray(vPosition)
        GLES20.glVertexAttribPointer(vPosition, coordsPerVertex, GLES20.GL_FLOAT, false, Float.SIZE_BYTES * coordsPerVertex, vertexBuffer)

        // Draw fill area below the lines.
        GLES20.glUniform4fv(vColor, 1, fillColor, 0)
        GLES20.glDrawElements(GLES20.GL_TRIANGLE_STRIP, fftSize * 2, GLES20.GL_UNSIGNED_INT, fillDrawOrderBuffer)

        // Draw the FFT lines.
        GLES20.glUniform4fv(vColor, 1, color, 0)
        GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, fftSize)

        // Draw the FFT hold peaks.
        if (preferences.peakHoldEnabled) {
            GLES20.glEnableVertexAttribArray(vPosition)
            GLES20.glVertexAttribPointer(vPosition, coordsPerVertex, GLES20.GL_FLOAT, false, Float.SIZE_BYTES * coordsPerVertex, peakHoldVertexBuffer)

            GLES20.glLineWidth(2.0f)
            GLES20.glUniform4fv(vColor, 1, peakHoldColor, 0)
            GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, fftSize)
            GLES20.glLineWidth(1.0f)
        }

        GLES20.glDisableVertexAttribArray(vPosition)

        if (preferences.peakIndicatorEnabled) {
            peaks.draw()
        }

        grid.drawLabels()
    }
}
