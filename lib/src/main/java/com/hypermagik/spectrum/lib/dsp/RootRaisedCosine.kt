package com.hypermagik.spectrum.lib.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class RootRaisedCosine {
    companion object {
        fun make(samplesPerSymbol: Int, count: Int, beta: Float): FloatArray {
            val taps = FloatArray(count)

            val half = count / 2.0f
            val limit = samplesPerSymbol / (4.0f * beta)

            for (i in 0 until count) {
                val t = i - half + 0.5f

                taps[i] = when (t) {
                    0.0f -> (
                                (1.0f + beta * (4 / PI - 1.0f)) / samplesPerSymbol
                            ).toFloat()

                    limit, -limit -> (
                                (
                                    (1.0f + 2 / PI) * sin(PI / 4 / beta) +
                                    (1.0f - 2 / PI) * cos(PI / 4 / beta)
                                ) * beta / (samplesPerSymbol * sqrt(2.0f))
                            ).toFloat()

                    else -> (
                                (
                                    sin((1.0f - beta) * PI * t / samplesPerSymbol) +
                                    cos((1.0f + beta) * PI * t / samplesPerSymbol) * 4 * beta * t / samplesPerSymbol
                                ) /
                                (
                                    (
                                        1.0f - (4 * beta * t / samplesPerSymbol) * (4 * beta * t / samplesPerSymbol)
                                    ) * PI * t / samplesPerSymbol
                                ) /
                                samplesPerSymbol
                            ).toFloat()
                }
            }

            return taps
        }
    }
}