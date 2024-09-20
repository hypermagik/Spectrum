package com.hypermagik.spectrum.lib.dsp

import kotlin.math.PI
import kotlin.math.cos

class Taps {
    companion object {
        fun estimateTapCount(transitionWidth: Float, sampleRate: Float, attenuation: Int = 90): Int {
            // Based on formula from Multirate Signal Processing for Communications Systems by Fredric J. Harris
            val count = (attenuation / 22.0 * sampleRate / transitionWidth).toInt()
            return if (count % 2 == 0) count + 1 else count
        }

        fun lowPass(
            sampleRate: Float,
            cutoff: Float,
            transitionWidth: Float,
            attenuation: Int = 90,
            windowType: Window.Type = Window.Type.BLACKMAN_NUTALL
        ): FloatArray {
            val count = estimateTapCount(transitionWidth, sampleRate, attenuation)
            val window = Window.make(windowType, count)
            return WindowedSinc.make(count, cutoff, sampleRate) { i -> window[i] }
        }

        fun lowPass(
            sampleRate: Float,
            cutoff: Float,
            numTaps: Int,
            windowType: Window.Type = Window.Type.BLACKMAN_NUTALL
        ): FloatArray {
            val window = Window.make(windowType, numTaps)
            return WindowedSinc.make(numTaps, cutoff, sampleRate) { i -> window[i] }
        }

        fun halfBand(
            numTaps: Int = 9,
            windowType: Window.Type = Window.Type.BLACKMAN_NUTALL
        ): FloatArray {
            val window = Window.make(windowType, numTaps)
            return WindowedSinc.make(numTaps, 0.25f, 1.0f) { i -> window[i] }
        }

        fun highPass(
            sampleRate: Float,
            cutoff: Float,
            transitionWidth: Float,
            attenuation: Int = 90,
            windowType: Window.Type = Window.Type.BLACKMAN_NUTALL
        ): FloatArray {
            val count = estimateTapCount(transitionWidth, sampleRate, attenuation)
            val window = Window.make(windowType, count)
            return WindowedSinc.make(count, cutoff, sampleRate) { window[it] * if (it % 2 == 0) 1.0f else -1.0f }
        }

        fun bandPass(
            sampleRate: Float,
            bandStart: Float,
            bandStop: Float,
            transitionWidth: Float,
            attenuation: Int = 90,
            windowType: Window.Type = Window.Type.BLACKMAN_NUTALL
        ): FloatArray {
            val tapCount = estimateTapCount(transitionWidth, sampleRate, attenuation)
            val window = Window.make(windowType, tapCount)
            val offsetOmega = 2 * PI.toFloat() * (bandStart + bandStop) / 2 / sampleRate
            return WindowedSinc.make(tapCount, (bandStop - bandStart) / 2, sampleRate) { window[it] * 2 * cos(offsetOmega * it) }
        }
    }
}