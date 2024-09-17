package com.hypermagik.spectrum.lib.dsp

import com.hypermagik.spectrum.lib.data.Complex32
import com.hypermagik.spectrum.lib.data.Complex32Array

class FIR(private val taps: FloatArray, private val decimation: Int = 1, private val half: Boolean = false) {
    private var delayLine = Complex32Array(taps.size) { Complex32() }

    private var tapIndex = 0
    private var decimationCounter = 1

    fun filter(input: Complex32Array, output: Complex32Array, length: Int = input.size): Int {
        var j = 0

        for (i in 0 until length) {
            delayLine[tapIndex].set(input[i])

            if (decimationCounter == 0) {
                if (j == output.size) {
                    return output.size
                }

                output[j].zero()

                if (half) {
                    val middle = taps.size / 2

                    var index = (tapIndex - middle).mod(taps.size)
                    output[j].addmul(delayLine[index], taps[middle])

                    var k = 1
                    while (k < middle) {
                        index = (tapIndex - middle - k).mod(taps.size)
                        output[j].addmul(delayLine[index], taps[middle + k])

                        index = (tapIndex - middle + k).mod(taps.size)
                        output[j].addmul(delayLine[index], taps[middle - k])

                        k += 2
                    }
                } else {
                    var index = tapIndex
                    for (tap in taps) {
                        output[j].addmul(delayLine[index], tap)
                        index = (index - 1).mod(taps.size)
                    }
                }

                j++
            }

            tapIndex = (tapIndex + 1) % taps.size
            decimationCounter = (decimationCounter + 1) % decimation
        }

        return j
    }
}