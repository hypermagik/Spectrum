package com.hypermagik.spectrum.lib.clock

import com.hypermagik.spectrum.lib.data.Complex32
import com.hypermagik.spectrum.lib.data.Complex32Array
import com.hypermagik.spectrum.lib.loop.PCL
import com.hypermagik.spectrum.lib.dsp.Utils.Companion.toRadians
import com.hypermagik.spectrum.lib.dsp.WindowedSinc
import kotlin.math.floor
import kotlin.math.sign

class FD(bandwidth: Float, private val samplesPerSymbol: Int) {
    private var pcl = PCL()
        .setBandwidth(bandwidth)
        .setFrequency(samplesPerSymbol * 1.0f, samplesPerSymbol * 0.99f, samplesPerSymbol * 1.01f)
        .setPhase(0.0f, 0.0f, 1.0f)

    private val phaseCount = 128
    private val tapsPerPhase = 8
    private val phases: Array<FloatArray> = Array(phaseCount) { FloatArray(tapsPerPhase) }

    private var buffer = Complex32Array(0) { Complex32() }
    private var offset = 0

    private var counter = 0

    private val ft = Array(2) { Complex32() }
    private val dfdt = Complex32()

    init {
        val taps = WindowedSinc.make(phaseCount * tapsPerPhase, (0.5f / phaseCount).toRadians())

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

            val error = errorFunction(phase, out)
            pcl.advance(error)

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

    private fun errorFunction(phase: Int, out: Complex32): Float {
        var error = 0.0f

        if (counter == 0) {
            when (phase) {
                0 -> {
                    ft[0].zero()
                    for (i in 0 until tapsPerPhase) {
                        ft[0].addmul(buffer[offset + i], phases[1][i])
                    }
                    dfdt.setdif(ft[0], out)
                }

                phaseCount - 1 -> {
                    ft[1].zero()
                    for (i in 0 until tapsPerPhase) {
                        ft[1].addmul(buffer[offset + i], phases[phaseCount - 2][i])
                    }
                    dfdt.setdif(out, ft[1])
                }

                else -> {
                    ft[0].zero()
                    ft[1].zero()
                    for (i in 0 until tapsPerPhase) {
                        ft[0].addmul(buffer[offset + i], phases[phase + 1][i])
                        ft[1].addmul(buffer[offset + i], phases[phase - 1][i])
                    }
                    dfdt.setdif(ft[0], ft[1])
                    dfdt.mul(0.5f)
                }
            }

            error = (out.re.sign * dfdt.re + out.im.sign * dfdt.im)
        }

        counter += 1
        if (counter >= samplesPerSymbol) {
            counter = 0
        }

        return error.coerceIn(-1.0f, 1.0f)
    }
}