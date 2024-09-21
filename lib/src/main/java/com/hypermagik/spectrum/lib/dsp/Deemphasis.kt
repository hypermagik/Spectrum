package com.hypermagik.spectrum.lib.dsp

import com.hypermagik.spectrum.lib.data.Complex32Array
import com.hypermagik.spectrum.lib.data.SampleBuffer

class Deemphasis(private val tau: Float) {
    private var lastOutput = 0.0f

    fun filter(samples: Complex32Array, sampleRate: Int, length: Int = samples.size) {
        val dt = 1.0f / sampleRate
        val alpha = dt / (tau + dt)

        samples[0].re = alpha * samples[0].re + (1.0f - alpha) * lastOutput
        for (i in 1 until length) {
            samples[i].re = alpha * samples[i].re + (1.0f - alpha) * samples[i - 1].re
        }
        lastOutput = samples[length - 1].re
    }

    fun filter(buffer: SampleBuffer) {
        filter(buffer.samples, buffer.sampleRate, buffer.sampleCount)
    }
}