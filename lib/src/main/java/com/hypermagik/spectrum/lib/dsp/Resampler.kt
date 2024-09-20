package com.hypermagik.spectrum.lib.dsp

import android.util.Log
import com.hypermagik.spectrum.lib.data.Complex32Array
import com.hypermagik.spectrum.lib.dsp.Utils.Companion.gcd
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.min
import kotlin.math.round

class Resampler(inputSampleRate: Int, val outputSampleRate: Int, numTaps: Int = 9) {
    private var decimator: Decimator? = null
    private var polyphase: Polyphase? = null

    init {
        val ratio = inputSampleRate / outputSampleRate

        val decimatorPower = floor(log2(ratio.toDouble())).toInt()
        val decimatorRatio = 1 shl decimatorPower

        var interpolatorSampleRate = inputSampleRate.toDouble()

        if (inputSampleRate > outputSampleRate && decimatorPower > 0) {
            decimator = Decimator(decimatorRatio)
            interpolatorSampleRate /= decimatorRatio
        }

        val inSampleRate = round(interpolatorSampleRate).toInt()

        val gcd = gcd(inSampleRate, outputSampleRate)

        val interpolation = outputSampleRate / gcd
        val decimation = inSampleRate / gcd

        if (interpolation != decimation) {
            val sampleRate = inSampleRate * interpolation
            val cutoff = min(inputSampleRate, outputSampleRate) / 2.0f
            val taps = Taps.lowPass(sampleRate.toFloat(), cutoff, interpolation * numTaps)

            for (i in taps.indices) {
                taps[i] = taps[i] * interpolation
            }

            polyphase = Polyphase(interpolation, decimation, taps)
        }

        val actualOutputSampleRate = 1.0 * inSampleRate * interpolation / decimation
        val error = abs((actualOutputSampleRate - outputSampleRate) / outputSampleRate) * 100.0

        Log.d("DSP", "Resampler error: $error%, decimator: $decimatorRatio, interpolator: $interpolation/$decimation")
    }

    fun resample(input: Complex32Array, output: Complex32Array, length: Int = input.size): Int {
        var outputLength = length
        decimator?.run { outputLength = decimate(input, output, outputLength) }
        polyphase?.run { outputLength = filter(output, output, outputLength) }
        return outputLength
    }
}