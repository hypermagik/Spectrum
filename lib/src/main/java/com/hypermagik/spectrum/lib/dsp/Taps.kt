package com.hypermagik.spectrum.lib.dsp

import com.hypermagik.spectrum.lib.data.Complex32
import com.hypermagik.spectrum.lib.data.Complex32Array
import com.hypermagik.spectrum.lib.dsp.Utils.Companion.toRadians
import kotlin.math.cos
import kotlin.math.sin

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
            val offsetOmega = ((bandStart + bandStop) / 2.0f / sampleRate).toRadians()
            return WindowedSinc.make(tapCount, (bandStop - bandStart) / 2, sampleRate) {
                2 * cos(offsetOmega * it) * window[it]
            }
        }

        fun bandPassC(
            sampleRate: Float,
            bandStart: Float,
            bandStop: Float,
            transitionWidth: Float,
            attenuation: Int = 90,
            windowType: Window.Type = Window.Type.BLACKMAN_NUTALL
        ): Complex32Array {
            val tapCount = estimateTapCount(transitionWidth, sampleRate, attenuation)
            val window = Window.make(windowType, tapCount)
            val offsetOmega = ((bandStart + bandStop) / 2.0f / sampleRate).toRadians()
            return WindowedSinc.makeComplex(tapCount, (bandStop - bandStart) / 2, sampleRate) {
                Complex32(cos(-offsetOmega * it) * window[it], sin(-offsetOmega * it) * window[it])
            }
        }
    }
}