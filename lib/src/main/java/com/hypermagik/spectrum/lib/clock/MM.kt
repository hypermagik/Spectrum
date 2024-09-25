package com.hypermagik.spectrum.lib.clock

import com.hypermagik.spectrum.lib.data.Complex32
import com.hypermagik.spectrum.lib.data.Complex32Array
import com.hypermagik.spectrum.lib.dsp.PCL
import com.hypermagik.spectrum.lib.dsp.Utils.Companion.toRadians
import com.hypermagik.spectrum.lib.dsp.WindowedSinc
import kotlin.math.floor

class MM(baud: Float, sampleRate: Float) {
    private var pcl = PCL()
        .setAlphaBeta(0.01f, 1e-6f)
        .setFrequency(sampleRate / baud, sampleRate / baud * 0.99f, sampleRate / baud * 1.01f)
        .setPhase(0.0f, 0.0f, 1.0f)

    private val phaseCount = 128
    private val tapsPerPhase = 8
    private val phases: Array<FloatArray> = Array(phaseCount) { FloatArray(tapsPerPhase) }

    private var buffer = Complex32Array(0) { Complex32() }
    private var offset = 0

    private val p = Complex32Array(3) { Complex32() }
    private val c = Complex32Array(3) { Complex32() }
    private val e = Complex32Array(3) { Complex32() }

    init {
        val bandwidth = 0.5f / phaseCount
        val taps = WindowedSinc.make(phaseCount * tapsPerPhase, bandwidth.toRadians())

        for (i in taps.indices) {
            taps[i] = taps[i] * phaseCount
        }

        for (i in taps.indices) {
            phases[(phaseCount - 1) - (i % phaseCount)][i / phaseCount] = taps[i]
        }
    }

    fun process(input: Complex32Array, output: Complex32Array, length: Int = input.size): Int {
        if (buffer.size < length + tapsPerPhase) {
            buffer = Complex32Array(length + tapsPerPhase) { Complex32() }
        }

        for (i in 0 until length) {
            buffer[tapsPerPhase - 1 + i].set(input[i])
        }

        var outputIndex = 0

        while (offset < length) {
            val out = output[outputIndex++]

            out.zero()

            val phase = floor(pcl.phase * phaseCount).toInt().coerceIn(0, phaseCount - 1)

            for (i in 0 until tapsPerPhase) {
                out.addmul(buffer[offset + i], phases[phase][i])
            }

            p[2].set(p[1])
            p[1].set(p[0])
            p[0].set(out)

            c[2].set(c[1])
            c[1].set(c[0])
            c[0].setstep(out)

            val error = errorFunction()
            pcl.advance(error)
            pcl.limitFrequency()

            val delta = floor(pcl.phase)
            offset += delta.toInt()
            pcl.phase -= delta
        }

        offset -= length

        for (i in 0 until tapsPerPhase - 1) {
            buffer[i].set(buffer[length + i])
        }

        return outputIndex
    }

    private fun errorFunction(): Float {
        e[0].zero()
        e[0].setdif(p[0], p[2])
        e[0].setmulconj(e[0], c[1])

        e[1].zero()
        e[1].setdif(c[0], c[2])
        e[1].setmulconj(e[1], p[1])

        e[2].setdif(e[0], e[1])

        return e[2].re.coerceIn(-1.0f, 1.0f)
    }
}