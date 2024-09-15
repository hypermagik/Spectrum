package com.hypermagik.spectrum.analyzer

import android.content.Context
import android.os.Bundle
import com.hypermagik.spectrum.Preferences
import com.hypermagik.spectrum.PreferencesWrapper
import com.hypermagik.spectrum.lib.data.Complex32
import com.hypermagik.spectrum.lib.data.Complex32Array
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

    fun setFrequencyRange(minFrequency: Long, maxFrequency: Long) {
        view.setFrequencyRange(minFrequency, maxFrequency)
    }

    fun showSetFrequencyDialog() {
        view.showSetFrequencyDialog()
    }

    fun start() {
        fpsLimit.update()
        throttle.setFPS(fpsLimit.value)

        view.start()
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

    fun analyze(samples: Complex32Array, preserveSamples: Boolean) {
        if (!preserveSamples) {
            analyze(samples)
        } else {
            for (i in samples.indices) {
                fftInput[i] = samples[i]
            }
            analyze(fftInput)
        }
    }

    private fun analyze(samples: Complex32Array) {
        val fftSize = min(samples.size, preferences.fftSize)

        val index = log2(fftSize.toDouble()).toInt() - 4
        if (index < 0) {
            return
        }

        val fft = ffts[index]
        val window = windows[index][preferences.fftWindowType]!!

        Window.apply(window, samples)

        fft.fft(samples)
        fft.magnitudes(samples, fftOutput)

        view.updateFFT(fftOutput, fft.size)
    }
}