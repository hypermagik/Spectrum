package com.hypermagik.spectrum.demodulator

import com.hypermagik.spectrum.lib.data.SampleBuffer

interface Demodulator {
    fun getOutputCount(): Int
    fun getOutputName(output: Int): String

    fun start()
    fun stop()

    fun demodulate(buffer: SampleBuffer, output: Int, observe: (samples: SampleBuffer, preserveSamples: Boolean) -> Unit)
}
