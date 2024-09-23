package com.hypermagik.spectrum.lib.gen

import com.hypermagik.spectrum.lib.data.Complex32
import com.hypermagik.spectrum.lib.dsp.Utils.Companion.minimaxCos
import com.hypermagik.spectrum.lib.dsp.Utils.Companion.minimaxSin
import com.hypermagik.spectrum.lib.dsp.Utils.Companion.toRadians
import java.util.SplittableRandom
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

class Noise(private val mode: Mode = Mode.Minimax) {
    enum class Mode { SinCos, Minimax }

    private val random = SplittableRandom()

    fun getNoise(sample: Complex32, scale: Float) {
        val u1 = random.nextDouble()
        val u2 = random.nextDouble()

        val sq = sqrt(-ln(u1)) * scale
        val phi = u2.toRadians()

        if (mode == Mode.Minimax) {
            sample.set(sq * minimaxCos(phi.toFloat()), sq * minimaxSin(phi.toFloat()))
        } else {
            sample.set(sq * cos(phi), sq * sin(phi))
        }
    }
}