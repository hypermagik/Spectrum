package com.hypermagik.spectrum.lib.gen

import com.hypermagik.spectrum.lib.data.Complex32
import com.hypermagik.spectrum.lib.dsp.Utils.Companion.toRadians
import java.util.SplittableRandom
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

class Noise {
    private val random = SplittableRandom()

    fun getNoise(sample: Complex32, scale: Float) {
        val u1 = random.nextDouble()
        val u2 = random.nextDouble()

        val sq = sqrt(-ln(u1)) * scale
        val ph = u2.toRadians()

        sample.set(sq * cos(ph), sq * sin(ph))
    }
}