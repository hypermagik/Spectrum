package com.hypermagik.spectrum.lib.dsp

import com.hypermagik.spectrum.lib.data.Complex32
import com.hypermagik.spectrum.lib.data.Complex32Array
import com.hypermagik.spectrum.lib.dsp.Utils.Companion.minimaxCos
import com.hypermagik.spectrum.lib.dsp.Utils.Companion.minimaxSin
import com.hypermagik.spectrum.lib.dsp.Utils.Companion.toRadians
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin

class Shifter(private var sampleRate: Int, var frequency: Float, private val mode: Mode = Mode.Minimax) {
    enum class Mode { SinCos, Minimax, LUT }

    private var omega = 0.0f
    private var phi = 0.0f

    private var lut = Complex32Array(0) { Complex32() }
    private var lutIndex = 0

    init {
        update(frequency)
    }

    fun shift(input: Complex32Array, output: Complex32Array, length: Int = input.size) {
        if (frequency == 0.0f) {
            if (input !== output) {
                for (i in 0 until length) {
                    output[i].set(input[i])
                }
            }
        } else if (mode == Mode.LUT) {
            for (i in 0 until length) {
                output[i].setmul(input[i], lut[lutIndex])
                lutIndex = (lutIndex + 1) % lut.size
            }
        } else if (mode == Mode.Minimax) {
            for (i in 0 until length) {
                output[i].setmul(input[i], minimaxCos(phi), minimaxSin(phi))

                phi += omega

                if (phi > PI) {
                    phi -= 2 * PI.toFloat()
                } else if (phi < -PI) {
                    phi += 2 * PI.toFloat()
                }
            }
        } else {
            for (i in 0 until length) {
                output[i].setmul(input[i], cos(phi), sin(phi))

                phi += omega

                if (phi > PI) {
                    phi -= 2 * PI.toFloat()
                } else if (phi < -PI) {
                    phi += 2 * PI.toFloat()
                }
            }
        }
    }

    fun update(frequency: Float) {
        this.frequency = frequency

        omega = (1.0f * frequency / sampleRate).toRadians()
        phi = 0.0f

        if (mode == Mode.LUT && frequency != 0.0f) {
            val length = abs(sampleRate / frequency)

            var bestLength = round(length)
            var bestError = abs(length - bestLength)

            for (i in 1 until (1000 / length).toInt()) {
                val error = abs(i * length - round(i * length))
                if (bestError > error) {
                    bestError = error
                    bestLength = round(i * length)
                }
            }

            lut = Complex32Array(bestLength.toInt()) { Complex32() }
            lutIndex = 0

            for (i in lut.indices) {
                lut[i].set(cos(phi), sin(phi))

                phi += omega

                if (phi > PI) {
                    phi -= 2 * PI.toFloat()
                } else if (phi < -PI) {
                    phi += 2 * PI.toFloat()
                }
            }
        }
    }
}