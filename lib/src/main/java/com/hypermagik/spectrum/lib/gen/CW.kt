package com.hypermagik.spectrum.lib.gen

import com.hypermagik.spectrum.lib.data.Complex32
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class CW(frequency: Long, private val sampleRate: Int) {
    private var phi = 0f
    private var omega = 0f
    private var cos = 1f
    private var sin = 0f

    init {
        setFrequency(frequency)
    }

    fun setFrequency(frequency: Long) {
        omega = (2 * PI * frequency / sampleRate).toFloat()
    }

    fun addSignal(sample: Complex32, scale: Float) {
        sample.add(cos * scale, sin * scale)

        phi += omega

        if (phi >= PI * 2) {
            phi = (phi - 2 * PI).toFloat()
        } else if (phi < 0) {
            phi = (phi + 2 * PI).toFloat()
        }

        cos = cos(phi)
        sin = sin(phi)
    }
}