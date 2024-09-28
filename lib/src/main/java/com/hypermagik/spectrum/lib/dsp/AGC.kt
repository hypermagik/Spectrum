package com.hypermagik.spectrum.lib.dsp

import com.hypermagik.spectrum.lib.data.Complex32Array
import kotlin.math.min

class AGC(attack: Int, decay: Int, sampleRate: Int) {
    private val attackPerSample = 1.0f * attack / sampleRate
    private val decayPerSample = 1.0f * decay / sampleRate

    private var amplitude = 0.0f
    private val maxGain = 1e6f

    fun process(input: Complex32Array, output: Complex32Array, length: Int = input.size) {
        for (i in 0 until length) {
            val magnitude = input[i].mag()
            var gain = 1.0f

            if (magnitude != 0.0f) {
                amplitude = if (magnitude > amplitude) (amplitude * (1 - attackPerSample) + magnitude * attackPerSample) else (amplitude * (1 - decayPerSample) + magnitude * decayPerSample)
                gain = min(1 / amplitude, maxGain)
            }

            if (magnitude * gain > 1.0f) {
                amplitude = 0.0f
                for (j in i until length) {
                    val mag = input[j].mag()
                    if (amplitude < mag) {
                        amplitude = mag
                    }
                }
                gain = min(1 / amplitude, maxGain)
            }

            output[i].setmul(input[i], gain)
        }
    }
}