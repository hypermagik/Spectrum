package com.hypermagik.spectrum.source

import android.util.Log
import com.hypermagik.spectrum.Preferences
import com.hypermagik.spectrum.lib.data.Complex32
import com.hypermagik.spectrum.lib.data.Complex32Array
import com.hypermagik.spectrum.lib.dsp.Utils
import com.hypermagik.spectrum.lib.gen.CW
import com.hypermagik.spectrum.lib.gen.Noise
import com.hypermagik.spectrum.utils.TAG
import com.hypermagik.spectrum.utils.Throttle

class ToneGenerator : Source {
    private var initialFrequency: Long = 0
    private var currentFrequency: Long = 0
    private var sampleRate: Int = 0

    private var bufferSize: Int = 0
    private var buffer: Complex32Array? = null

    private var noiseGain: Float = 2 * Utils.db2mag(-90f)
    private val noise = Noise()

    private val initialSignalFrequencies = longArrayOf(-300000, -100000, 100000, 300000)
    private val initialSignalGains = floatArrayOf(-60f, -80f, -70f, -90f)
    private val modulatedFrequencies = longArrayOf(100, 200, 400, 800)

    private lateinit var signals: Array<CW>
    private lateinit var signalFrequencies: LongArray
    private lateinit var signalGains: FloatArray

    private lateinit var throttle: Throttle

    override fun getName(): String {
        return "Tone Generator"
    }

    override fun open(preferences: Preferences): Boolean {
        Log.d(TAG, "Opening ${getName()}")

        initialFrequency = preferences.frequency
        currentFrequency = initialFrequency
        sampleRate = preferences.sampleRate

        bufferSize = preferences.sampleFifoBufferSize
        buffer = Array(bufferSize) { Complex32() }

        signalFrequencies = LongArray(initialSignalFrequencies.size) { i -> initialSignalFrequencies[i] + currentFrequency }

        signals = Array(signalFrequencies.size) { CW(0, sampleRate) }
        signalGains = FloatArray(signals.size)

        for (i in signals.indices) {
            signals[i].setFrequency(initialSignalFrequencies[i])
            signals[i].setModulatedFrequency((modulatedFrequencies[i] * sampleRate / 1e6).toLong())
            signalGains[i] = Utils.db2mag(initialSignalGains[i] + preferences.gain)
        }

        throttle = Throttle(bufferSize, preferences.sampleRate)

        return true
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
        throttle.sync()

        val frequencyRange = LongRange(currentFrequency - sampleRate / 2, currentFrequency + sampleRate / 2)

        for (sample in buffer) {
            noise.getNoise(sample, noiseGain)

            for (i in signals.indices) {
                if (signalFrequencies[i] in frequencyRange) {
                    signals[i].addSignal(sample, signalGains[i])
                }
            }
        }
    }

    override fun setFrequency(frequency: Long) {
        currentFrequency = frequency

        val frequencyOffset = initialFrequency - currentFrequency

        for (i in signals.indices) {
            signals[i].setFrequency(initialSignalFrequencies[i] + frequencyOffset)
        }
    }

    override fun getMinimumFrequency(): Long {
        return initialFrequency - sampleRate
    }

    override fun getMaximumFrequency(): Long {
        return initialFrequency + sampleRate
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
