package com.hypermagik.spectrum.lib.dsp

import com.hypermagik.spectrum.lib.data.Complex32
import com.hypermagik.spectrum.lib.data.Complex32Array
import com.hypermagik.spectrum.lib.dsp.Utils.Companion.sinc
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class FLL(
    samplesPerSymbol: Int,
    bandwidth: Float,
    minFrequency: Float,
    maxFrequency: Float,
    filterTapCount: Int,
    filterRolloff: Float
) {
    private val pcl = PCL()
        .setBandwidth(bandwidth).setAlpha(0.0f)
        .setFrequency(0.0f, minFrequency, maxFrequency)

    private val lowerBandEdgeFilter: FIRC
    private val upperBandEdgeFilter: FIRC

    private val lbeFilterOutput = Complex32Array(1) { Complex32() }
    private val ubeFilterOutput = Complex32Array(1) { Complex32() }

    init {
        val taps = FloatArray(filterTapCount)

        val m = filterTapCount / samplesPerSymbol
        for (i in 0 until filterTapCount) {
            val k = -m + i * 2.0f / samplesPerSymbol
            taps[i] = sinc(filterRolloff * k - 0.5f) + sinc(filterRolloff * k + 0.5f)
        }

        val power = taps.sum()
        for (i in 0 until filterTapCount) {
            taps[i] /= power
        }

        val lbeTaps = Complex32Array(filterTapCount) { Complex32() }
        val ubeTaps = Complex32Array(filterTapCount) { Complex32() }

        val n = (filterTapCount - 1) / 2.0f
        for (i in 0 until filterTapCount) {
            val k = (-n + i) / (2.0f * samplesPerSymbol)
            lbeTaps[filterTapCount - 1 - i].set(
                cos(-2 * PI * (1.0f + filterRolloff) * k) * taps[i],
                sin(-2 * PI * (1.0f + filterRolloff) * k) * taps[i]
            )
            ubeTaps[filterTapCount - 1 - i].set(
                cos(2 * PI * (1.0f + filterRolloff) * k) * taps[i],
                sin(2 * PI * (1.0f + filterRolloff) * k) * taps[i]
            )
        }

        lowerBandEdgeFilter = FIRC(lbeTaps)
        upperBandEdgeFilter = FIRC(ubeTaps)
    }

    fun process(input: Complex32Array, output: Complex32Array, length: Int = input.size) {
        for (i in 0 until length) {
            output[i].setmul(input[i], cos(-pcl.phase), sin(-pcl.phase))

            lowerBandEdgeFilter.filter(output, lbeFilterOutput, 1, i)
            upperBandEdgeFilter.filter(output, ubeFilterOutput, 1, i)

            val error = ubeFilterOutput[0].mag() - lbeFilterOutput[0].mag()

            pcl.advance(error)
            pcl.wrapPhase()
        }
    }
}