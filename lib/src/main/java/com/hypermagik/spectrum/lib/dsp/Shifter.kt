package com.hypermagik.spectrum.lib.dsp

import com.hypermagik.spectrum.lib.data.Complex32Array
import com.hypermagik.spectrum.lib.dsp.Utils.Companion.toRadians
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class Shifter(private var sampleRate: Int, var frequency: Float) {
    private var omega = (1.0f * frequency / sampleRate).toRadians()
    private var phi = 0.0f

    fun shift(input: Complex32Array, output: Complex32Array, length: Int = input.size) {
        if (frequency == 0.0f) {
            if (input !== output) {
                for (i in 0 until length) {
                    output[i].set(input[i])
                }
            }
        } else {
            for (i in 0 until length) {
                output[i].setmul(input[i], cos(phi), sin(phi))

                phi += omega

                if (phi >= 2 * PI) {
                    phi -= 2 * PI.toFloat()
                } else if (phi < 0) {
                    phi += 2 * PI.toFloat()
                }
            }
        }
    }

    fun update(frequency: Float) {
        this.frequency = frequency

        omega = (1.0f * frequency / sampleRate).toRadians()
        phi = 0.0f
    }
}