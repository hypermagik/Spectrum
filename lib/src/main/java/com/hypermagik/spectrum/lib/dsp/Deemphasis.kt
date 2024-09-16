package com.hypermagik.spectrum.lib.dsp

import com.hypermagik.spectrum.lib.data.SampleBuffer

class Deemphasis(private val tau: Float) {
    private var lastOutput = 0.0f

    fun filter(buffer: SampleBuffer) {
        val dt = 1.0f / buffer.sampleRate
        val alpha = dt / (tau + dt)

        buffer.samples[0].re = alpha * buffer.samples[0].re + (1.0f - alpha) * lastOutput
        for (i in 1 until buffer.sampleCount) {
            buffer.samples[i].re = alpha * buffer.samples[i].re + (1.0f - alpha) * buffer.samples[i - 1].re
        }
        lastOutput = buffer.samples[buffer.sampleCount - 1].re
    }
}