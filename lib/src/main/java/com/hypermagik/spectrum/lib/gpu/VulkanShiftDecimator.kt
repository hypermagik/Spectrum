package com.hypermagik.spectrum.lib.gpu

import android.util.Log
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

class VulkanShiftDecimator(private val sampleRate: Int, private val ratio: Int, forceSingleQueue: Boolean = false) {
    companion object {
        external fun create(taps: ByteBuffer, forceSingleQueue: Boolean): Long
        external fun process(instance: Long, samples: ByteBuffer, sampleCount: Int, phi: Float, omega: Float)
        external fun delete(instance: Long)

        fun isAvailable(ratio: Int): Boolean {
            return ratio and (ratio - 1) == 0 && ratio > 1
        }
    }

    private var instance: Long = 0

    private val n = log2(ratio.toDouble()).toInt()

    private var phi = 0.0f
    private var omega = 0.0f

    private var floatArray = FloatArray(Complex32.MAX_ARRAY_SIZE * 2)
    private var buffer = ByteBuffer.allocateDirect(floatArray.size * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())

    init {
        if (ratio and (ratio - 1) != 0) {
            throw IllegalArgumentException("Ratio must be a power of 2")
        }
        if (ratio <= 1) {
            throw IllegalArgumentException("Ratio must be greater than 1")
        }

        val taps = Array(n) { FloatArray(0) }
        for (i in 0 until n) {
            taps[i] = Taps.lowPass(1.0f, 0.25f, min(0.1f * 2.0f.pow(i), 0.5f))
        }
        taps.reverse()

        val tapBufferSize = Int.SIZE_BYTES + Int.SIZE_BYTES * n + taps.sumOf { it.size } * Float.SIZE_BYTES
        val tapBuffer = ByteBuffer.allocateDirect(tapBufferSize).order(ByteOrder.nativeOrder())

        tapBuffer.putInt(n)

        for (i in 0 until n) {
            tapBuffer.putInt(taps[i].size)
            for (j in 0 until taps[i].size) {
                tapBuffer.putFloat(taps[i][j])
            }
        }

        instance = create(tapBuffer, forceSingleQueue)
        check(instance != 0L)

        Log.d("VK", "Vulkan decimator, ratio: $ratio, stages: ${taps.size}, taps: ${taps.sumOf { it.size }}")
    }

    fun setShiftFrequency(frequency: Float) {
        phi = 0.0f
        omega = (frequency / sampleRate).toRadians()
    }

    fun decimate(input: Complex32Array, output: Complex32Array, length: Int): Int {
        input.toArray(floatArray, 0, length)
        buffer.asFloatBuffer().put(floatArray, 0, length * 2)

        process(instance, buffer, length, phi, omega)

        if (omega != 0.0f) {
            phi = (phi + omega * length).mod(2 * PI.toFloat())
        }

        buffer.asFloatBuffer().get(floatArray, 0, length / ratio * 2)
        output.fromArray(floatArray, 0, length / ratio)

        return length / ratio
    }

    fun close() {
        delete(instance)
        instance = 0
    }
}