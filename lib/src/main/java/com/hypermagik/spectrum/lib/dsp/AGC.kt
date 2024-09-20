package com.hypermagik.spectrum.lib.dsp

import com.hypermagik.spectrum.lib.data.Complex32Array

class AGC(private val target: Float = 1.0f, private val maxGain: Float = 1e6f, private val rate: Float = 0.1f) {
    private var gain = 1.0f

    fun process(input: Complex32Array, output: Complex32Array, length: Int) {
        for (i in 0 until length) {
            output[i].setmul(input[i], gain)

            val mag = output[i].mag()

            gain += (target - mag) * rate

            if (gain > maxGain) {
                gain = maxGain
            }
        }
    }
}