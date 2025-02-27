package com.hypermagik.spectrum.demodulator

import com.hypermagik.spectrum.lib.data.SampleBuffer

interface Demodulator {
    fun getName(): String

    fun getOutputCount(): Int
    fun getOutputName(output: Int): String

    fun getChannelBandwidth(): Int
    fun setFrequency(frequency: Long)

    fun start()
    fun stop()

    fun demodulate(buffer: SampleBuffer, output: Int, observe: (samples: SampleBuffer, preserveSamples: Boolean) -> Unit)
    fun getText(): String?
}
