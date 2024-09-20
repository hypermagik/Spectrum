package com.hypermagik.spectrum.lib.dsp

import com.hypermagik.spectrum.lib.data.Complex32Array
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class Shifter(sampleRate: Int, val frequency: Long) {
    private var omega = (2 * PI * frequency / sampleRate).toFloat()
    private var phi = 0.0f

    fun shift(input: Complex32Array, output: Complex32Array, length: Int = input.size) {
        for (i in 0 until length) {
            output[i].setmul(input[i], cos(phi), sin(phi))

            phi += omega

            if (phi >= PI * 2) {
                phi = (phi - 2 * PI).toFloat()
            } else if (phi < 0) {
                phi = (phi + 2 * PI).toFloat()
            }
        }
    }
}