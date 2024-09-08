package com.hypermagik.spectrum.source

import android.util.Log
import com.hypermagik.spectrum.IQRecorder
import com.hypermagik.spectrum.Preferences
import com.hypermagik.spectrum.lib.data.Complex32Array
import com.hypermagik.spectrum.lib.data.SampleType
import com.hypermagik.spectrum.lib.dsp.Utils
import com.hypermagik.spectrum.lib.gen.CW
import com.hypermagik.spectrum.lib.gen.Noise
import com.hypermagik.spectrum.utils.TAG
import com.hypermagik.spectrum.utils.Throttle

class ToneGenerator(private val preferences: Preferences, private val recorder: IQRecorder) : Source {
    private var initialFrequency: Long = 3e9.toLong()
    private var currentFrequency: Long = initialFrequency

    private var sampleRate: Int = 0
    private var supportedSampleRates = intArrayOf(1000000, 2000000, 5000000, 10000000, 20000000, 30000000, 40000000, 61440000)

    private var noiseGain: Float = 2 * Utils.db2mag(-90.0f)
    private val noise = Noise()

    private val initialSignalFrequencies = longArrayOf(-300000, -100000, 100000, 300000)
    private val initialSignalGains = floatArrayOf(-60.0f, -80.0f, -70.0f, -90.0f)
    private val modulatedFrequencies = longArrayOf(100, 200, 400, 800)

    private lateinit var signals: Array<CW>
    private lateinit var signalFrequencies: LongArray
    private lateinit var signalGains: FloatArray

    private var throttle = Throttle()

    override fun getName(): String {
        return "Tone generator"
    }

    override fun getType(): SourceType {
        return SourceType.ToneGenerator
    }

    override fun getSampleType(): SampleType {
        return SampleType.F32
    }

    override fun open(preferences: Preferences): String? {
        Log.d(TAG, "Opening ${getName()}")

        if (!supportedSampleRates.contains(preferences.sourceSettings.sampleRate)) {
            preferences.sourceSettings.sampleRate = supportedSampleRates[0]
            preferences.save()
        }
        sampleRate = preferences.sourceSettings.sampleRate

        if (preferences.sourceSettings.frequency < getMinimumFrequency() || getMaximumFrequency() < preferences.sourceSettings.frequency) {
            preferences.sourceSettings.frequency = initialFrequency
            preferences.save()
        }
        currentFrequency = preferences.sourceSettings.frequency

        signalFrequencies = LongArray(initialSignalFrequencies.size) { i -> initialSignalFrequencies[i] + initialFrequency }

        signals = Array(signalFrequencies.size) { CW(0, sampleRate) }
        signalGains = FloatArray(signals.size)

        for (i in signals.indices) {
            signals[i].setFrequency(initialSignalFrequencies[i] + initialFrequency - currentFrequency)
            signals[i].setModulatedFrequency((modulatedFrequencies[i] * sampleRate / 1e6).toLong())
            signalGains[i] = Utils.db2mag(initialSignalGains[i] + preferences.sourceSettings.gain)
        }

        return null
    }

    override fun close() {
        Log.d(TAG, "Closing")
    }

    override fun start() {
        Log.d(TAG, "Starting")
    }

    override fun stop() {
        Log.d(TAG, "Stopping")
    }

    override fun read(buffer: Complex32Array) {
        throttle.sync(1000000000L * buffer.size / sampleRate)

        val frequencyRange = LongRange(currentFrequency - sampleRate / 2, currentFrequency + sampleRate / 2)

        for (sample in buffer) {
            noise.getNoise(sample, noiseGain)

            for (i in signals.indices) {
                if (signalFrequencies[i] in frequencyRange) {
                    signals[i].addSignal(sample, signalGains[i])
                }
            }
        }

        recorder.record(buffer)
    }

    override fun setFrequency(frequency: Long) {
        currentFrequency = frequency

        val frequencyOffset = initialFrequency - currentFrequency

        for (i in signals.indices) {
            signals[i].setFrequency(initialSignalFrequencies[i] + frequencyOffset)
        }
    }

    override fun getMinimumFrequency(): Long {
        return initialFrequency - preferences.sourceSettings.sampleRate
    }

    override fun getMaximumFrequency(): Long {
        return initialFrequency + preferences.sourceSettings.sampleRate
    }

    override fun setGain(gain: Int) {
        for (i in signalGains.indices) {
            signalGains[i] = Utils.db2mag(initialSignalGains[i] + gain)
        }
    }

    override fun getMinimumGain(): Int {
        return -50
    }

    override fun getMaximumGain(): Int {
        return 50
    }

    override fun setAGC(enable: Boolean) {}
}
