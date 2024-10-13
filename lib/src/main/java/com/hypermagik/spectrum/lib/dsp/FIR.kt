package com.hypermagik.spectrum.lib.dsp

import com.hypermagik.spectrum.lib.data.Complex32
import com.hypermagik.spectrum.lib.data.Complex32Array

class FIR(private val taps: FloatArray, val decimation: Int = 1, private val half: Boolean = false) {
    private var buffer = Complex32Array(0) { Complex32() }
    private var decimationCounter = 0

    fun filter(input: Complex32Array, output: Complex32Array, length: Int = input.size): Int {
        if (buffer.size < length + taps.size) {
            buffer = Complex32Array(length + taps.size) { Complex32() }
        }

        for (i in 0 until length) {
            buffer[taps.size - 1 + i].set(input[i])
        }

        var outputIndex = 0

        for (i in 0 until length) {
            if (decimationCounter == 0) {
                val out = output[outputIndex++]

                out.zero()

                if (half) {
                    val middle = taps.size / 2

                    out.addmul(buffer[i + middle], taps[middle])

                    var j = 1
                    while (j < middle) {
                        out.addmul(buffer[i + middle + j], taps[middle + j])
                        out.addmul(buffer[i + middle - j], taps[middle - j])
                        j += 2
                    }
                } else {
                    for (j in taps.indices) {
                        out.addmul(buffer[i + j], taps[j])
                    }
                }
            }

            decimationCounter = (decimationCounter + 1) % decimation
        }

        for (i in 0 until taps.size - 1) {
            buffer[i].set(buffer[length + i])
        }

        return outputIndex
    }
}