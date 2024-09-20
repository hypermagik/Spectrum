package com.hypermagik.spectrum.demodulator

import android.util.Log
import com.hypermagik.spectrum.lib.data.SampleBuffer
import com.hypermagik.spectrum.lib.demod.Quadrature
import com.hypermagik.spectrum.lib.dsp.Deemphasis
import com.hypermagik.spectrum.lib.dsp.FIR
import com.hypermagik.spectrum.lib.dsp.Resampler
import com.hypermagik.spectrum.lib.dsp.Shifter
import com.hypermagik.spectrum.lib.dsp.Taps
import com.hypermagik.spectrum.utils.TAG

class WFM(demodulatorAudio: Boolean) : Demodulator {
    private var sampleRate = 1000000
    private val frequencyOffset = 200000L

    private val quadratureRate = 250000
    private val quadratureDeviation = 75000

    private val halfBandTaps = Taps.halfBand()
    private val audioTaps = Taps.lowPass(1.0f, 0.1f, 0.1f)

    private lateinit var shifter: Shifter
    private lateinit var resampler: Resampler
    private lateinit var quadrature: Quadrature
    private lateinit var lowPassFIR: FIR

    private val rdsDemodulator = RDS(quadratureRate)

    // Typical time constant values:
    // USA: tau = 75 us
    // EU:  tau = 50 us
    private var deemphasis = Deemphasis(22e-6f)

    private lateinit var audioFIR: FIR
    private var audioSink: AudioSink? = null

    private val outputs = mapOf(
        1 to "LPF",
        2 to "Quadrature",
        3 to "Audio"
    )

    override fun getOutputCount(): Int = outputs.size
    override fun getOutputName(output: Int): String = outputs[output]!!

    override fun getText(): String? = rdsDemodulator.getText()

    init {
        if (demodulatorAudio) {
            audioSink = AudioSink(31250)
        }

        setSampleRate(1000000)

        Log.d(TAG, "Half-band taps (${halfBandTaps.size}): ${halfBandTaps.joinToString(", ")}")
        Log.d(TAG, "Audio taps (${audioTaps.size}): ${audioTaps.joinToString(", ")}")
    }

    override fun start() {
        audioSink?.start()
    }

    override fun stop() {
        audioSink?.stop()
    }

    private fun setSampleRate(sampleRate: Int): Boolean {
        shifter = Shifter(sampleRate, -frequencyOffset)
        resampler = Resampler(sampleRate, quadratureRate)
        quadrature = Quadrature(quadratureRate, quadratureDeviation)
        lowPassFIR = FIR(halfBandTaps, 2, true)
        audioFIR = FIR(audioTaps, 4)

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

        buffer.sampleCount = resampler.resample(buffer.samples, buffer.samples, buffer.sampleCount)
        buffer.sampleRate = resampler.outputSampleRate

        if (output == 1) {
            observe(buffer, true)
        }

        quadrature.demodulate(buffer.samples, buffer.samples, buffer.sampleCount)
        buffer.realSamples = true

        rdsDemodulator.demodulate(buffer)

        lowPassFIR.filter(buffer.samples, buffer.samples, buffer.sampleCount)
        buffer.sampleCount /= 2
        buffer.sampleRate /= 2

        if (output == 2) {
            observe(buffer, true)
        }

        audioFIR.filter(buffer.samples, buffer.samples, buffer.sampleCount)
        buffer.sampleCount /= 4
        buffer.sampleRate /= 4

        deemphasis.filter(buffer)

        audioSink?.play(buffer.samples, buffer.sampleCount)

        if (output == 3) {
            observe(buffer, false)
        }
    }
}