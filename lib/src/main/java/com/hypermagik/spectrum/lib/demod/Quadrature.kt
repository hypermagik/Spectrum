package com.hypermagik.spectrum.lib.demod

import com.hypermagik.spectrum.lib.data.Complex32
import com.hypermagik.spectrum.lib.data.Complex32Array
import kotlin.math.PI

class Quadrature(sampleRate: Int, deviation: Int) {
    // Polar discriminator
    // http://www.hyperdynelabs.com/dspdude/papers/DigRadio_w_mathcad.pdf#page=33

    private var gain = (sampleRate / (2 * PI * deviation)).toFloat()
    private var previousSample = Complex32(1.0f, 0.0f)
    private var sample = Complex32()

    fun demodulate(input: Complex32Array, output: Complex32Array, length: Int = input.size) {
        // Sample holds the phase difference between the current sample and the previous sample.
        sample.setmulconj(input[0], previousSample)
        previousSample.set(input[0])

        output[0].re = gain * sample.phase()
        output[0].im = 0.0f

        for (i in 1 until length) {
            sample.setmulconj(input[i], previousSample)
            previousSample.set(input[i])

            output[i].re = gain * sample.phase()
            output[i].im = 0.0f
        }
    }
}