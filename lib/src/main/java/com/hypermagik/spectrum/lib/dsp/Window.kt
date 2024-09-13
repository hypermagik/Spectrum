package com.hypermagik.spectrum.lib.dsp

import kotlin.math.PI
import kotlin.math.cos

class Window {
    enum class Type { HAMMING, BLACKMAN_HARRIS, FLAT_TOP }

    companion object {
        fun make(type: Type, size: Int): FloatArray {
            val window = FloatArray(size)

            for (i in window.indices) {
                val w = when (type) {
                    Type.HAMMING -> 0.54f - 0.46f * cos(2 * PI * i / (size - 1))

                    Type.BLACKMAN_HARRIS -> 0.35875f -
                            0.48829f * cos(2 * PI * i / (size - 1)) +
                            0.14128f * cos(4 * PI * i / (size - 1)) -
                            0.01168f * cos(6 * PI * i / (size - 1))

                    Type.FLAT_TOP -> 1.0f -
                            1.930f * cos(2 * PI * i / (size - 1)) +
                            1.290f * cos(4 * PI * i / (size - 1)) -
                            0.388f * cos(6 * PI * i / (size - 1)) +
                            0.028f * cos(8 * PI * i / (size - 1))
                }

                window[i] = w.toFloat()
            }

            return window
        }
    }
}