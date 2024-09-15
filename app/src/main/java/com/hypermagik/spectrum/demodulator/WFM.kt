package com.hypermagik.spectrum.demodulator

import com.hypermagik.spectrum.lib.data.SampleBuffer
import com.hypermagik.spectrum.lib.demod.Quadrature
import com.hypermagik.spectrum.lib.dsp.LowPassFIR
import com.hypermagik.spectrum.lib.dsp.Shifter

class WFM(demodulatorAudio: Boolean) : Demodulator {
    private val sampleRate = 1000000
    private val frequencyOffset = 200000L

    private var shifter = Shifter(sampleRate, -frequencyOffset)
    private val lowPassFIR4 = LowPassFIR(9, 4)
    private val lowPassFIR2 = LowPassFIR(9, 2)
    private val quadrature = Quadrature(250000, 75000)
    private val audioFIR = LowPassFIR(39, 4, 1 / 10f)

    private var audioSink: AudioSink? = null

    private val outputs = mapOf(
        1 to "F/4 LPF",
        2 to "Quadrature",
        3 to "Audio"
    )

    override fun getOutputCount(): Int = outputs.size
    override fun getOutputName(output: Int): String = outputs[output]!!

    init {
        if (demodulatorAudio) {
            audioSink = AudioSink(31250)
        }
    }

    override fun start() {
        audioSink?.start()
    }

    override fun stop() {
        audioSink?.stop()
    }

    override fun demodulate(buffer: SampleBuffer, output: Int, observe: (samples: SampleBuffer, preserveSamples: Boolean) -> Unit) {
        if (buffer.sampleRate != sampleRate) {
            observe(buffer, false)
            return
        }

        if (output == 0) {
            observe(buffer, true)
        }

        shifter.shift(buffer.samples, buffer.samples)
        buffer.frequency += frequencyOffset

        lowPassFIR4.filter(buffer.samples, buffer.samples)
        buffer.sampleCount /= 4
        buffer.sampleRate /= 4

        if (output == 1) {
            observe(buffer, true)
        }

        quadrature.demodulate(buffer.samples, buffer.samples, buffer.sampleCount)

        lowPassFIR2.filter(buffer.samples, buffer.samples, buffer.sampleCount)
        buffer.sampleCount /= 2
        buffer.sampleRate /= 2

        if (output == 2) {
            observe(buffer, true)
        }

        audioFIR.filter(buffer.samples, buffer.samples, buffer.sampleCount)
        buffer.sampleCount /= 4
        buffer.sampleRate /= 4

        audioSink?.play(buffer.samples, buffer.sampleCount)

        if (output == 3) {
            observe(buffer, false)
        }
    }
}