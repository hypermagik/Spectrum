package com.hypermagik.spectrum.demodulator

import com.hypermagik.spectrum.lib.data.SampleBuffer
import com.hypermagik.spectrum.lib.demod.Quadrature
import com.hypermagik.spectrum.lib.dsp.FIR
import com.hypermagik.spectrum.lib.dsp.Resampler
import com.hypermagik.spectrum.lib.dsp.Shifter
import com.hypermagik.spectrum.lib.dsp.Taps

class FM(audio: Boolean) : Demodulator {
    private var sampleRate = 1000000

    private val bandwidth = 12500
    private val quadratureRate = 50000
    private val quadratureDeviation = bandwidth / 2

    private var shifter = Shifter(sampleRate, 0.0f)
    private var resampler = Resampler(sampleRate, quadratureRate)
    private var quadrature = Quadrature(quadratureRate, quadratureDeviation)

    private val audioTaps = Taps.lowPass(quadratureRate.toFloat(), bandwidth / 2.0f, bandwidth / 4.0f)
    private var audioFIR = FIR(audioTaps, 2)

    private var audioSink: AudioSink? = null

    private val outputs = mapOf(
        1 to "LPF",
        2 to "Quadrature",
        3 to "Audio"
    )

    override fun getName(): String = "FM"

    override fun getOutputCount(): Int = outputs.size
    override fun getOutputName(output: Int): String = outputs[output]!!

    init {
        if (audio) {
            audioSink = AudioSink(bandwidth * 2, 0.5f)
        }
    }

    override fun getChannelBandwidth(): Int = quadratureRate

    override fun setFrequency(frequency: Long) {
        shifter.update(-frequency.toFloat())
    }

    override fun start() {
        audioSink?.start()
    }

    override fun stop() {
        audioSink?.stop()
    }

    override fun demodulate(buffer: SampleBuffer, output: Int, observe: (samples: SampleBuffer, preserveSamples: Boolean) -> Unit) {
        if (output == 0) {
            observe(buffer, true)
        }

        if (sampleRate != buffer.sampleRate) {
            sampleRate = buffer.sampleRate
            shifter = Shifter(sampleRate, shifter.frequency)
            resampler = Resampler(sampleRate, quadratureRate)
        }

        shifter.shift(buffer.samples, buffer.samples)
        buffer.frequency += -shifter.frequency.toLong()

        buffer.sampleCount = resampler.resample(buffer.samples, buffer.samples, buffer.sampleCount)
        buffer.sampleRate = resampler.outputSampleRate

        if (output == 1) {
            observe(buffer, true)
        }

        quadrature.demodulate(buffer.samples, buffer.samples, buffer.sampleCount)
        buffer.realSamples = true

        if (output == 2) {
            observe(buffer, true)
        }

        buffer.sampleCount = audioFIR.filter(buffer.samples, buffer.samples, buffer.sampleCount)
        buffer.sampleRate /= audioFIR.decimation

        audioSink?.play(buffer.samples, buffer.samples, buffer.sampleCount)

        if (output == 3) {
            observe(buffer, false)
        }
    }

    override fun getText(): String? = null
}