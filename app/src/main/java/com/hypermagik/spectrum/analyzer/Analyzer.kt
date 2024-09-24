package com.hypermagik.spectrum.analyzer

import android.content.Context
import android.os.Bundle
import com.hypermagik.spectrum.Preferences
import com.hypermagik.spectrum.PreferencesWrapper
import com.hypermagik.spectrum.lib.data.Complex32
import com.hypermagik.spectrum.lib.data.Complex32Array
import com.hypermagik.spectrum.lib.data.SampleBuffer
import com.hypermagik.spectrum.lib.dsp.FFT
import com.hypermagik.spectrum.lib.dsp.Window
import com.hypermagik.spectrum.utils.Throttle
import kotlin.math.log2
import kotlin.math.min

class Analyzer(context: Context, private val preferences: Preferences) {
    val view: AnalyzerView = AnalyzerView(context, preferences)

    private val fftMinSize = 16
    private val fftMaxSize = 16384

    private val ffts: List<FFT>
    private val windows: List<Map<Window.Type, FloatArray>>

    private var fftInput: Complex32Array = Complex32Array(16384) { Complex32() }
    private var fftOutput: FloatArray = FloatArray(16384) { 0.0f }

    private val throttle = Throttle()
    private val fpsLimit = PreferencesWrapper.FPSLimit(preferences)

    init {
        ffts = ArrayList()
        windows = ArrayList()

        var fftSize = fftMinSize
        while (fftSize <= fftMaxSize) {
            ffts.add(FFT(fftSize))

            val map = HashMap<Window.Type, FloatArray>()
            for (windowType in Window.Type.entries) {
                map[windowType] = Window.make(windowType, fftSize)
            }
            windows.add(map)

            fftSize *= 2
        }
    }

    fun saveInstanceState(outState: Bundle) {
        view.saveInstanceState(outState)
    }

    fun restoreInstanceState(savedInstanceState: Bundle) {
        view.restoreInstanceState(savedInstanceState)
    }

    fun setSourceInput(name: String, minFrequency: Long, maxFrequency: Long) {
        view.setInputInfo("Source", name, minFrequency, maxFrequency, true)
    }

    fun setDemodulatorInput(name: String, details: String) {
        view.setInputInfo(name, details, 0, 0, false)
    }

    fun showSetFrequencyDialog() {
        view.showSetFrequencyDialog()
    }

    fun start(channelBandwidth: Int) {
        fpsLimit.update()
        throttle.setFPS(fpsLimit.value)

        view.start(channelBandwidth)
    }

    fun stop(restart: Boolean) {
        view.stop(restart)
    }

    fun needsSamples(): Boolean {
        if (fpsLimit.update()) {
            throttle.setFPS(fpsLimit.value)
        }
        return throttle.isSynced()
    }

    fun analyze(buffer: SampleBuffer, preserveSamples: Boolean) {
        if (!view.isReady) {
            return
        }

        if (view.frequency != buffer.frequency) {
            view.updateFrequency(buffer.frequency)
        }
        if (view.sampleRate != buffer.sampleRate) {
            view.updateSampleRate(buffer.sampleRate)
        }
        if (view.realSamples != buffer.realSamples) {
            view.updateRealSamples(buffer.realSamples)
        }

        var input = buffer.samples
        if (preserveSamples) {
            val n = min(buffer.sampleCount, preferences.fftSize)
            for (i in 0 until n) {
                fftInput[i].set(buffer.samples[i])
            }
            input = fftInput
        }
        analyze(input, buffer.sampleCount, buffer.realSamples)
    }

    private fun analyze(samples: Complex32Array, sampleCount: Int, realSamples: Boolean) {
        val fftSize = min(sampleCount, preferences.fftSize)

        val index = log2(fftSize.toDouble()).toInt() - 4
        if (index < 0 || index >= ffts.size) {
            return
        }

        val fft = ffts[index]
        val window = windows[index][preferences.fftWindowType]!!

        Window.apply(window, samples)

        fft.fft(samples)
        fft.magnitudes(samples, fftOutput, preferences.fftSize, realSamples)

        view.updateFFT(fftOutput, preferences.fftSize, fft.size)
    }

    fun setDemodulatorText(text: String?) {
        view.setDemodulatorText(text)
    }
}