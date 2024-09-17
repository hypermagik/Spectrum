package com.hypermagik.spectrum.lib.dsp

import com.hypermagik.spectrum.lib.data.Complex32Array
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min

class Window {
    enum class Type { BLACKMAN, BLACKMAN_HARRIS, BLACKMAN_NUTALL, FLAT_TOP, HAMMING, HANN, RECTANGULAR }

    companion object {
        private val coefficients = mapOf(
            Type.BLACKMAN to floatArrayOf(0.42f, 0.5f, 0.08f),
            Type.BLACKMAN_HARRIS to floatArrayOf(0.35875f, 0.48829f, 0.14128f, 0.01168f),
            Type.BLACKMAN_NUTALL to floatArrayOf(0.3635819f, 0.4891775f, 0.1365995f, 0.0106411f),
            Type.FLAT_TOP to floatArrayOf(1.0f, 1.930f, 1.290f, 0.388f, 0.028f),
            Type.HAMMING to floatArrayOf(0.54f, 0.46f),
            Type.HANN to floatArrayOf(0.5f, 0.5f),
            Type.RECTANGULAR to floatArrayOf(1.0f),
        )

        fun make(type: Type, size: Int): FloatArray {
            val coefficients = coefficients[type]!!
            val window = FloatArray(size)

            for (i in window.indices) {
                var sign = 1.0f
                for (j in coefficients.indices) {
                    window[i] += sign * coefficients[j] * cos(j * 2 * PI * i / (size -1)).toFloat()
                    sign = -sign
                }
            }

            return window
        }

        fun apply(window: FloatArray, samples: Complex32Array) {
            val n = min(window.size, samples.size)
            for (i in 0 until n) {
                samples[i].mul(window[i])
            }
        }
    }
}