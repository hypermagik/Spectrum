package com.hypermagik.spectrum.lib.dsp

import android.util.Log
import com.hypermagik.spectrum.lib.data.Complex32
import com.hypermagik.spectrum.lib.data.Complex32Array

class Polyphase(private val interpolation: Int, private val decimation: Int, taps: FloatArray) {
    private val tapsPerPhase = (taps.size + interpolation - 1) / interpolation
    private val phases = Array(interpolation) { FloatArray(tapsPerPhase) }

    private var phase = 0
    private var offset = 0

    private var buffer = Complex32Array(0) { Complex32() }

    init {
        val count = interpolation * tapsPerPhase
        for (i in 0 until count) {
            phases[(interpolation - 1) - (i % interpolation)][i / interpolation] = if (i < taps.size) taps[i] else 0.0f
        }

        Log.d("DSP", "Polyphase $interpolation/$decimation, phases: ${phases.size}, taps: ${taps.size}/$tapsPerPhase")
    }

    fun filter(input: Complex32Array, output: Complex32Array, length: Int = input.size): Int {
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

            for (i in 0 until tapsPerPhase) {
                out.addmul(buffer[offset + i], phases[phase][i])
            }

            phase += decimation
            offset += phase / interpolation
            phase %= interpolation
        }

        offset -= length

        for (i in 0 until tapsPerPhase - 1) {
            buffer[i].set(buffer[length + i])
        }

        return outputIndex
    }
}