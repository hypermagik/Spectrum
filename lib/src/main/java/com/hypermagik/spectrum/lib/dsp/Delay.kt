package com.hypermagik.spectrum.lib.dsp

import com.hypermagik.spectrum.lib.data.Complex32
import com.hypermagik.spectrum.lib.data.Complex32Array

class Delay(private val delay: Int) {
    private var delayBuffer = Complex32Array(delay) { Complex32() }
    private var copyBuffer = Complex32Array(delay) { Complex32() }

    fun process(input: Complex32Array, output: Complex32Array, length: Int = input.size) {
        for (i in 0 until delay) {
            copyBuffer[i].set(input[length - delay + i])
        }

        for (i in 0 until length - delay) {
            output[length - 1 - i].set(input[length - 1 - i - delay])
        }

        for (i in 0 until delay) {
            output[i].set(delayBuffer[i])
            delayBuffer[i].set(copyBuffer[i])
        }
    }
}