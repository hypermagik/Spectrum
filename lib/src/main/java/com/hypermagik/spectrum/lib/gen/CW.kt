package com.hypermagik.spectrum.lib.gen

import com.hypermagik.spectrum.lib.data.Complex32
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class CW(frequency: Long, private val sampleRate: Int) {
    private var phi = 0.0f
    private var omega = 0.0f
    private var cos = 1.0f
    private var sin = 0.0f

    private var fm = 0.0f
    private var phi2 = 0.0f
    private var omega2 = 0.0f

    init {
        setFrequency(frequency)
    }

    fun setFrequency(frequency: Long) {
        omega = (2 * PI * frequency / sampleRate).toFloat()
    }

    fun setModulatedFrequency(frequency: Long) {
        fm = 10e3f / frequency
        omega2 = (2 * PI * frequency / sampleRate).toFloat()
    }

    fun addSignal(sample: Complex32, scale: Float) {
        sample.add(cos * scale, sin * scale)

        phi += omega

        if (phi >= PI * 2) {
            phi = (phi - 2 * PI).toFloat()
        } else if (phi < 0) {
            phi = (phi + 2 * PI).toFloat()
        }

        var mod = 0.0f

        if (omega2 != 0.0f) {
            phi2 += omega2

            if (phi2 >= 2 * PI) {
                phi2 = (phi2 - 2 * PI).toFloat()
            } else if (phi2 < 0) {
                phi2 = (phi2 + 2 * PI).toFloat()
            }

            mod = fm * sin(phi2)
        }

        cos = cos(phi + mod)
        sin = sin(phi + mod)
    }
}
