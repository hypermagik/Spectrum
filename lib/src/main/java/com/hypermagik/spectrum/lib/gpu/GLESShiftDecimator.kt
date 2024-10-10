package com.hypermagik.spectrum.lib.gpu

import android.opengl.GLES20
import android.opengl.GLES31
import android.util.Log
import com.hypermagik.spectrum.lib.R
import com.hypermagik.spectrum.lib.data.Complex32
import com.hypermagik.spectrum.lib.data.Complex32Array
import com.hypermagik.spectrum.lib.dsp.Taps
import com.hypermagik.spectrum.lib.dsp.Utils.Companion.toRadians
import com.hypermagik.spectrum.lib.utils.fromArray
import com.hypermagik.spectrum.lib.utils.toArray
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.log2
import kotlin.math.min
import kotlin.math.pow

class GLESShiftDecimator(private val sampleRate: Int, private val ratio: Int) {
    private val n = log2(ratio.toDouble()).toInt()

    private var shifterProgram = GLES31.GL_NONE
    private var decimatorProgram = GLES31.GL_NONE

    private val computeGroupSize = 128
    private val numBuffers = 2
    private var bufferIndex = 0

    private var offsetLocation = 0
    private var phiLocation = 1
    private var omegaLocation = 2

    private var phi = 0.0f
    private var omega = 0.0f

    private var ssboTaps = IntArray(0)
    private var ssboInput = IntArray(0)
    private val ssboOutput = IntArray(2)

    private var tapsCount = IntArray(0)
    private var floatArray = FloatArray(Complex32.MAX_ARRAY_SIZE * 2)

    private val counters = Counters()

    companion object {
        fun isAvailable(ratio: Int): Boolean {
            return ratio and (ratio - 1) == 0 && ratio > 1 &&
                    GLES.INSTANCE.isAvailable() &&
                    GLES.INSTANCE.getProgram(R.raw.shifter) != GLES31.GL_NONE &&
                    GLES.INSTANCE.getProgram(R.raw.decimator) != GLES31.GL_NONE
        }

        private fun s2b(samples: Int): Int = samples * 2 * Float.SIZE_BYTES
    }

    init {
        if (ratio and (ratio - 1) != 0) {
            throw IllegalArgumentException("Ratio must be a power of 2")
        }
        if (ratio <= 1) {
            throw IllegalArgumentException("Ratio must be greater than 1")
        }

        shifterProgram = GLES.INSTANCE.getProgram(R.raw.shifter)
        decimatorProgram = GLES.INSTANCE.getProgram(R.raw.decimator)

        // Taps
        ssboTaps = IntArray(n)
        checkGLError { GLES31.glGenBuffers(ssboTaps.size, ssboTaps, 0) }

        tapsCount = IntArray(n)

        for (i in 0 until n) {
            val taps = Taps.lowPass(1.0f, 0.25f, min(0.1f * 2.0f.pow(i), 0.5f))
            tapsCount[i] = taps.size

            val buffer = ByteBuffer.allocateDirect(taps.size * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())
            buffer.asFloatBuffer().put(taps)

            checkGLError { GLES31.glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, ssboTaps[i]) }
            checkGLError { GLES31.glBufferData(GLES31.GL_SHADER_STORAGE_BUFFER, taps.size * Float.SIZE_BYTES, buffer, GLES31.GL_STATIC_READ) }
        }

        ssboTaps.reverse()
        tapsCount.reverse()

        // Input
        ssboInput = IntArray(n)
        checkGLError { GLES31.glGenBuffers(ssboInput.size, ssboInput, 0) }

        for (i in 0 until n) {
            val length = s2b(getBufferOffset(i) + Complex32.MAX_ARRAY_SIZE shr i)

            val buffer = ByteBuffer.allocateDirect(length).order(ByteOrder.nativeOrder())

            checkGLError { GLES31.glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, ssboInput[i]) }
            checkGLError { GLES31.glBufferData(GLES31.GL_SHADER_STORAGE_BUFFER, length, buffer, GLES31.GL_STREAM_READ) }
        }

        // Output
        checkGLError { GLES31.glGenBuffers(ssboOutput.size, ssboOutput, 0) }

        for (i in ssboOutput.indices) {
            val length = s2b(Complex32.MAX_ARRAY_SIZE / ratio)

            val buffer = ByteBuffer.allocateDirect(length).order(ByteOrder.nativeOrder())

            checkGLError { GLES31.glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, ssboOutput[i]) }
            checkGLError { GLES31.glBufferData(GLES31.GL_SHADER_STORAGE_BUFFER, length, buffer, GLES31.GL_STREAM_READ) }
        }

        Log.d("GLES", "GLES decimator, ratio: $ratio, stages: ${tapsCount.size}, taps: ${tapsCount.sum()}")
    }

    fun setShiftFrequency(frequency: Float) {
        phi = 0.0f
        omega = (frequency / sampleRate).toRadians()
    }

    private fun getBufferOffset(index: Int): Int {
        return if (index == tapsCount.size) 0 else (tapsCount[index] - 1)
    }

    fun decimate(input: Complex32Array, output: Complex32Array, length: Int): Int {
        counters.start()

        input.toArray(floatArray, 0, length)
        upload(length)

        for (i in 0 until n) {
            GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 0, ssboTaps[i])
            GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 1, ssboInput[i])
            GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 2, if (i < n - 1) ssboInput[i + 1] else ssboOutput[bufferIndex])

            if (i == 0 && omega != 0.0f) {
                GLES20.glUseProgram(shifterProgram)
                GLES31.glUniform1f(phiLocation, phi)
                GLES31.glUniform1f(omegaLocation, omega)
                GLES31.glUniform1i(offsetLocation, getBufferOffset(0))
                GLES31.glDispatchCompute(length / computeGroupSize, 1, 1)

                phi = (phi + omega * length).mod(2 * PI.toFloat())
            }

            GLES20.glUseProgram(decimatorProgram)
            GLES31.glUniform1i(offsetLocation, getBufferOffset(i + 1))
            GLES31.glDispatchCompute((length shr i) / 2 / computeGroupSize, 1, 1)

            GLES31.glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, ssboInput[i])
            GLES31.glCopyBufferSubData(GLES31.GL_SHADER_STORAGE_BUFFER, GLES31.GL_SHADER_STORAGE_BUFFER, s2b(length shr i), 0, s2b(getBufferOffset(i)))
        }

        GLES31.glFlush()

        bufferIndex = (bufferIndex + 1) % numBuffers

        val outputLength = length / ratio

        download(outputLength)
        output.fromArray(floatArray, 0, outputLength)

        counters.end()

        return outputLength
    }

    private fun upload(numSamples: Int) {
        val offset = s2b(getBufferOffset(0))
        val length = s2b(numSamples)

        GLES31.glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, ssboInput[0])

        val buffer = GLES31.glMapBufferRange(GLES31.GL_SHADER_STORAGE_BUFFER, offset, length, GLES31.GL_MAP_WRITE_BIT or GLES31.GL_MAP_INVALIDATE_RANGE_BIT)
        if (buffer == null) {
            Log.e("GLES", "Failed to map shader storage buffer for upload")
        } else {
            (buffer as ByteBuffer).order(ByteOrder.nativeOrder()).asFloatBuffer().put(floatArray, 0, numSamples * 2)
            GLES31.glUnmapBuffer(GLES31.GL_SHADER_STORAGE_BUFFER)
        }
    }

    private fun download(numSamples: Int) {
        val length = s2b(numSamples)

        GLES31.glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, ssboOutput[bufferIndex])

        val buffer = GLES31.glMapBufferRange(GLES31.GL_SHADER_STORAGE_BUFFER, 0, length, GLES31.GL_MAP_READ_BIT)
        if (buffer == null) {
            Log.e("GLES", "Failed to map shader storage buffer for download")
        } else {
            (buffer as ByteBuffer).order(ByteOrder.nativeOrder()).asFloatBuffer().get(floatArray, 0, numSamples * 2)
            GLES31.glUnmapBuffer(GLES31.GL_SHADER_STORAGE_BUFFER)
        }
    }

    fun close() {
        GLES31.glDeleteBuffers(ssboTaps.size, ssboTaps, 0)
        GLES31.glDeleteBuffers(ssboInput.size, ssboInput, 0)
        GLES31.glDeleteBuffers(ssboOutput.size, ssboOutput, 0)
    }

    class Counters {
        private var start = 0L
        private var min = Long.MAX_VALUE
        private var max = Long.MIN_VALUE
        private var sum = 0L
        private var counter = 0

        fun start() {
            start = System.nanoTime()
        }

        fun end() {
            val delta = System.nanoTime() - start
            if (delta < min) min = delta
            if (delta > max) max = delta
            sum += delta

            if (++counter == 120) {
                Log.d("GLES", String.format("DSP total time: %4dus / %4dus / %4dus", min / 1000, sum / counter / 1000, max / 1000))
                min = Long.MAX_VALUE
                max = Long.MIN_VALUE
                sum = 0L
                counter = 0
            }
        }
    }
}
