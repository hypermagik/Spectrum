package com.hypermagik.spectrum.lib.gen

import com.hypermagik.spectrum.lib.data.Complex32
import com.hypermagik.spectrum.lib.dsp.Utils.Companion.minimaxCos
import com.hypermagik.spectrum.lib.dsp.Utils.Companion.minimaxSin
import com.hypermagik.spectrum.lib.dsp.Utils.Companion.toRadians
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class CW(frequency: Long, private val sampleRate: Int, private val mode: Mode = Mode.Minimax) {
    enum class Mode { SinCos, Minimax }

    private var phi = 0.0f
    private var omega = 0.0f

    private var phi2 = 0.0f
    private var omega2 = 0.0f

    private var cos = 1.0f
    private var sin = 0.0f

    init {
        setFrequency(frequency)
    }

    fun setFrequency(frequency: Long) {
        omega = (1.0f * frequency / sampleRate).toRadians()
    }

    fun setModulatedFrequency(frequency: Long) {
        omega2 = (1.0f * frequency / sampleRate).toRadians()
    }

    fun addSignal(sample: Complex32, scale: Float) {
        sample.add(cos * scale, sin * scale)

        phi += omega

        if (phi > PI) {
            phi -= 2 * PI.toFloat()
        } else if (phi < -PI) {
            phi += 2 * PI.toFloat()
        }

        var phase = phi

        if (omega2 != 0.0f) {
            phi2 += omega2

            if (phi2 > PI) {
                phi2 -= 2 * PI.toFloat()
            } else if (phi2 < -PI) {
                phi2 += 2 * PI.toFloat()
            }

            phase += 2 * PI.toFloat() * sin(phi2)

            if (phase > PI) {
                phase -= 2 * PI.toFloat()
            } else if (phase < -PI) {
                phase += 2 * PI.toFloat()
            }
        }

        if (mode == Mode.Minimax) {
            cos = minimaxCos(phase)
            sin = minimaxSin(phase)
        } else {
            cos = cos(phase)
            sin = sin(phase)
        }
    }
}
