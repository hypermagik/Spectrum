package com.hypermagik.spectrum.demodulator

import android.util.Log
import com.hypermagik.spectrum.Preferences
import com.hypermagik.spectrum.lib.data.SampleBuffer
import com.hypermagik.spectrum.lib.dsp.AGC
import com.hypermagik.spectrum.lib.dsp.FIR
import com.hypermagik.spectrum.lib.dsp.Resampler
import com.hypermagik.spectrum.lib.dsp.Taps
import com.hypermagik.spectrum.utils.TAG

class AM(private val preferences: Preferences) : Demodulator {
    private var sampleRate = 1000000
    private var shiftFrequency = 0.0f

    private val bandwidth = 12500
    private val channelSampleRate = 25000

    private var resampler = Resampler(sampleRate, channelSampleRate, preferences.demodulatorGPUAPI)
    private var agc = AGC(50, 5, channelSampleRate)

    private val audioTaps = Taps.lowPass(channelSampleRate.toFloat(), bandwidth / 2.0f, bandwidth / 4.0f)
    private var audioFIR = FIR(audioTaps)

    private var audioSink: AudioSink? = null

    private val outputs = mapOf(
        1 to "Channel",
        2 to "Audio"
    )

    override fun getName(): String = "AM"

    override fun getOutputCount(): Int = outputs.size
    override fun getOutputName(output: Int): String = outputs[output]!!

    init {
        if (preferences.demodulatorAudio) {
            audioSink = AudioSink(bandwidth * 2, 0.5f)
        }
    }

    override fun getChannelBandwidth(): Int = channelSampleRate

    override fun setFrequency(frequency: Long) {
        shiftFrequency = frequency.toFloat()
        resampler.setShiftFrequency(-shiftFrequency)
    }

    override fun start() {
        audioSink?.start()
    }

    override fun stop() {
        audioSink?.stop()
        resampler.close()
    }

    override fun demodulate(buffer: SampleBuffer, output: Int, observe: (samples: SampleBuffer, preserveSamples: Boolean) -> Unit) {
        if (output == 0) {
            observe(buffer, true)
        }

        if (sampleRate != buffer.sampleRate) {
            Log.d(TAG, "Sample rate changed from $sampleRate to ${buffer.sampleRate}")
            sampleRate = buffer.sampleRate

            resampler.close()
            resampler = Resampler(sampleRate, channelSampleRate, preferences.demodulatorGPUAPI)
            resampler.setShiftFrequency(-shiftFrequency)
        }

        buffer.sampleCount = resampler.resample(buffer.samples, buffer.samples, buffer.sampleCount)
        buffer.sampleRate = resampler.outputSampleRate
        buffer.frequency -= shiftFrequency.toLong()

        agc.process(buffer.samples, buffer.samples, buffer.sampleCount)

        if (output == 1) {
            observe(buffer, true)
        }

        buffer.sampleCount = audioFIR.filter(buffer.samples, buffer.samples, buffer.sampleCount)
        buffer.sampleRate /= audioFIR.decimation

        buffer.realSamples = true

        audioSink?.play(buffer.samples, buffer.samples, buffer.sampleCount)

        if (output == 2) {
            observe(buffer, false)
        }
    }

    override fun getText(): String? = null
}