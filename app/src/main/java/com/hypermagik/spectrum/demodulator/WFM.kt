package com.hypermagik.spectrum.demodulator

import com.hypermagik.spectrum.lib.data.SampleBuffer
import com.hypermagik.spectrum.lib.demod.Quadrature
import com.hypermagik.spectrum.lib.dsp.LowPassFIR
import com.hypermagik.spectrum.lib.dsp.Shifter

class WFM(private val demodulatorAudio: Boolean) : Demodulator {
    private var sampleRate = 1000000
    private val frequencyOffset = 200000L

    private val supportedSampleRates = listOf(
        1000000,
        1024000,
        2000000,
        2048000,
    )

    private val quadratureDeviation = 75000
    private val quadratureRates = mapOf(
        1000000 to 250000,
        1024000 to 256000,
        2000000 to 250000,
        2048000 to 256000,
    )

    private val audioSampleRates = mapOf(
        1000000 to 31250,
        1024000 to 32000,
        2000000 to 31250,
        2048000 to 32000,
    )

    private var shifter = Shifter(sampleRate, -frequencyOffset)

    private var lowPassFIR1 = LowPassFIR(9, 2)
    private var lowPassFIR2 = LowPassFIR(9, 4)
    private var lowPassFIR3 = LowPassFIR(9, 2)

    private var quadrature = Quadrature(quadratureRates[sampleRate]!!, quadratureDeviation)

    private var audioFIR = LowPassFIR(39, 4, 1 / 10f)
    private var audioSink: AudioSink? = null

    private val outputs = mapOf(
        1 to "LPF",
        2 to "Quadrature",
        3 to "Audio"
    )

    override fun getOutputCount(): Int = outputs.size
    override fun getOutputName(output: Int): String = outputs[output]!!

    init {
        if (demodulatorAudio) {
            audioSink = AudioSink(audioSampleRates[sampleRate]!!)
        }
    }

    override fun start() {
        audioSink?.start()
    }

    override fun stop() {
        audioSink?.stop()
    }

    private fun setSampleRate(sampleRate: Int): Boolean {
        if (!supportedSampleRates.contains(sampleRate)) {
            return false
        }

        shifter = Shifter(sampleRate, -frequencyOffset)
        quadrature = Quadrature(quadratureRates[sampleRate]!!, quadratureDeviation)

        if (demodulatorAudio) {
            audioSink?.stop()
            audioSink = AudioSink(audioSampleRates[sampleRate]!!)
            audioSink?.start()
        }

        this.sampleRate = sampleRate

        return true
    }

    override fun demodulate(buffer: SampleBuffer, output: Int, observe: (samples: SampleBuffer, preserveSamples: Boolean) -> Unit) {
        if (sampleRate != buffer.sampleRate) {
            if (!setSampleRate(buffer.sampleRate)) {
                observe(buffer, false)
                return
            }
        }

        if (output == 0) {
            observe(buffer, true)
        }

        shifter.shift(buffer.samples, buffer.samples)
        buffer.frequency += frequencyOffset

        if (sampleRate >= 2000000) {
            lowPassFIR1.filter(buffer.samples, buffer.samples, buffer.sampleCount)
            buffer.sampleCount /= 2
            buffer.sampleRate /= 2
        }

        lowPassFIR2.filter(buffer.samples, buffer.samples)
        buffer.sampleCount /= 4
        buffer.sampleRate /= 4

        if (output == 1) {
            observe(buffer, true)
        }

        quadrature.demodulate(buffer.samples, buffer.samples, buffer.sampleCount)

        lowPassFIR3.filter(buffer.samples, buffer.samples, buffer.sampleCount)
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