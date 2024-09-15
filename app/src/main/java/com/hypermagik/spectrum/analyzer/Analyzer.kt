package com.hypermagik.spectrum.analyzer

import android.content.Context
import android.os.Bundle
import com.hypermagik.spectrum.Preferences
import com.hypermagik.spectrum.PreferencesWrapper
import com.hypermagik.spectrum.lib.data.Complex32
import com.hypermagik.spectrum.lib.data.Complex32Array
import com.hypermagik.spectrum.lib.dsp.FFT
import com.hypermagik.spectrum.utils.Throttle

class Analyzer(context: Context, private val preferences: Preferences) {
    val view: AnalyzerView = AnalyzerView(context, preferences)

    var fft: FFT = FFT(preferences.fftSize, preferences.fftWindowType)
        private set

    private var fftInput: Complex32Array = Complex32Array(preferences.fftSize) { Complex32() }
    private var fftOutput: FloatArray = FloatArray(preferences.fftSize) { 0.0f }

    private val throttle = Throttle()
    private val fpsLimit = PreferencesWrapper.FPSLimit(preferences)

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
        if (fft.size != preferences.fftSize || fft.windowType != preferences.fftWindowType) {
            fft = FFT(preferences.fftSize, preferences.fftWindowType)
        }

        if (fftOutput.size != fft.size) {
            fftOutput = FloatArray(fft.size) { 0.0f }
        }

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
        fft.applyWindow(samples)
        fft.fft(samples)
        fft.magnitudes(samples, fftOutput)

        view.updateFFT(fftOutput, fftSize)
    }
}