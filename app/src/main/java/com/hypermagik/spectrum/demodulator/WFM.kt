package com.hypermagik.spectrum.demodulator

import android.util.Log
import com.hypermagik.spectrum.lib.data.SampleBuffer
import com.hypermagik.spectrum.lib.demod.Quadrature
import com.hypermagik.spectrum.lib.dsp.Deemphasis
import com.hypermagik.spectrum.lib.dsp.FIR
import com.hypermagik.spectrum.lib.dsp.Shifter
import com.hypermagik.spectrum.lib.dsp.Taps
import com.hypermagik.spectrum.utils.TAG

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

    private val halfBandTaps = Taps.lowPass(1.0f, 1.0f / 4, 1.0f / 4, 60)
    private val quarterBandTaps = Taps.lowPass(1.0f, 1.0f / 8, 1.0f / 8, 60)
    private val audioTaps = Taps.lowPass(1.0f, 0.1f, 0.1f)

    private lateinit var shifter: Shifter
    private lateinit var lowPassFIR1: FIR
    private lateinit var lowPassFIR2: FIR
    private lateinit var lowPassFIR3: FIR
    private lateinit var quadrature: Quadrature

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

    override fun getText(): String? = null

    init {
        if (demodulatorAudio) {
            audioSink = AudioSink(audioSampleRates[sampleRate]!!)
        }

        setSampleRate(supportedSampleRates[0])

        Log.d(TAG, "Half-band taps (${halfBandTaps.size}): ${halfBandTaps.joinToString(", ")}")
        Log.d(TAG, "Quarter-band taps (${quarterBandTaps.size}): ${quarterBandTaps.joinToString(", ")}")
        Log.d(TAG, "Audio taps (${audioTaps.size}): ${audioTaps.joinToString(", ")}")
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

        lowPassFIR1 = FIR(halfBandTaps, 2, true)
        lowPassFIR2 = FIR(quarterBandTaps, 4)
        lowPassFIR3 = FIR(halfBandTaps, 2, true)

        quadrature = Quadrature(quadratureRates[sampleRate]!!, quadratureDeviation)


        audioFIR = FIR(audioTaps, 4)

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
        buffer.realSamples = true

        lowPassFIR3.filter(buffer.samples, buffer.samples, buffer.sampleCount)
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