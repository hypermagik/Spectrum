package com.hypermagik.spectrum.lib.dsp

import com.hypermagik.spectrum.lib.data.Complex32
import com.hypermagik.spectrum.lib.data.Complex32Array
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

        fun makeComplex(count: Int, cutoff: Float, sampleRate: Float, window: (Int) -> Complex32): Complex32Array {
            return makeComplex(count, 2 * PI.toFloat() * cutoff / sampleRate, window)
        }

        private fun makeComplex(count: Int, omega: Float, window: (Int) -> Complex32): Complex32Array {
            val taps = Complex32Array(count) { Complex32() }

            val m = (count - 1) / 2
            for (n in -m..m) {
                taps[n + m].set((sinc(n * omega) * omega / PI).toFloat(), 0.0f)
                taps[n + m].mul(window(n + m))
            }

            return taps
        }
    }
}