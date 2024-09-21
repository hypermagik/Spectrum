package com.hypermagik.spectrum.lib.dsp

import com.hypermagik.spectrum.lib.dsp.Utils.Companion.sinc
import kotlin.math.PI

class WindowedSinc {
    companion object {
        fun make(count: Int, cutoff: Float, sampleRate: Float, window: (Int) -> Float): FloatArray {
            return make(count, 2 * PI.toFloat() * cutoff / sampleRate, window)
        }

        private fun make(count: Int, omega: Float, window: (Int) -> Float): FloatArray {
            val taps = FloatArray(count)

            val m = (count - 1) / 2
            for (n in -m..m) {
                taps[n + m] = (sinc(n * omega) * omega / PI * window(n + m)).toFloat()
            }

            return taps
        }

        fun make(count: Int, omega: Float, windowType: Window.Type = Window.Type.BLACKMAN_NUTALL): FloatArray {
            val window = Window.make(windowType, count)
            return make(count, omega) { window[it] }
        }
    }
}