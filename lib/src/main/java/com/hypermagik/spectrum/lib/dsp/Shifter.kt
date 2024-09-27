package com.hypermagik.spectrum.lib.dsp

import com.hypermagik.spectrum.lib.data.Complex32
import com.hypermagik.spectrum.lib.data.Complex32Array
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class Shifter(private var sampleRate: Int, var frequency: Float) {
    private var phase = Complex32(1.0f, 0.0f)
    private var increment = Complex32()
    private var normalizationCounter = 0

    init {
        update(frequency)
    }

    fun shift(input: Complex32Array, output: Complex32Array, length: Int = input.size) {
        if (frequency == 0.0f) {
            if (input !== output) {
                for (i in 0 until length) {
                    output[i].set(input[i])
                }
            }
        } else {
            for (i in 0 until length) {
                output[i].setmul(input[i], phase)
                phase.mul(increment)
            }

            normalizationCounter += length
            if (normalizationCounter >= sampleRate) {
                normalizationCounter -= sampleRate
                phase.mul(1.0f / phase.mag())
            }
        }
    }

    fun update(frequency: Float) {
        this.frequency = frequency

        phase.set(1.0f, 0.0f)
        normalizationCounter = 0

        increment.set(
            cos(2 * PI * frequency / sampleRate).toFloat(),
            sin(2 * PI * frequency / sampleRate).toFloat()
        )
    }
}