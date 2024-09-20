package com.hypermagik.spectrum.lib.dsp

import android.util.Log
import com.hypermagik.spectrum.lib.data.Complex32Array
import kotlin.math.log2
import kotlin.math.min
import kotlin.math.pow

class Decimator(private val ratio: Int) {
    private val filters = ArrayList<FIR>()

    init {
        if (ratio and (ratio - 1) != 0) {
            throw IllegalArgumentException("Ratio must be a power of 2")
        }
        if (ratio < 1) {
            throw IllegalArgumentException("Ratio must be greater than 0")
        }

        var totalTaps = 0

        val n = log2(ratio.toDouble()).toInt()
        for (i in 0 until n) {
            val taps = Taps.lowPass(1.0f, 0.25f, min(0.1f * 2.0f.pow(i), 0.5f))
            filters.add(0, FIR(taps, 2, true))

            totalTaps += taps.size
        }

        Log.d("DSP", "Decimator, ratio: $ratio, filters: ${filters.size}, total taps: $totalTaps")
    }

    fun decimate(input: Complex32Array, output: Complex32Array, length: Int = input.size): Int {
        var outputLength = length

        if (ratio == 1) {
            for (i in 0 until length) {
                output[i].set(input[i])
            }
        } else {
            var data = input
            for (i in 0 until filters.size) {
                outputLength = filters[i].filter(data, output, outputLength)
                data = output
            }
        }

        return outputLength
    }
}