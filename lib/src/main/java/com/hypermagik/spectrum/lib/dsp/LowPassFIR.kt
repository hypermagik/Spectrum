package com.hypermagik.spectrum.lib.dsp

import android.util.Log
import com.hypermagik.spectrum.lib.data.Complex32
import com.hypermagik.spectrum.lib.data.Complex32Array
import kotlin.math.PI
import kotlin.math.sin

class LowPassFIR {
    private var decimation = 2
    private var isHalfBand = true

    private lateinit var taps: FloatArray
    private lateinit var delayLine: Complex32Array

    private var tapIndex = 0
    private var decimationCounter = 1

    constructor(numTaps: Int, decimation: Int = 2, passBand: Float = 0.5f / decimation, gain: Float = 1.0f) {
        init(numTaps, decimation, passBand, gain)
    }

    constructor(sampleFrequency: Int, decimation: Int, cutoffFrequency: Float, transitionWidth: Float, attenuation: Float, gain: Float = 1.0f) {
        if (sampleFrequency <= 0) {
            throw IllegalArgumentException("Sample frequency must be positive")
        }
        if (decimation <= 0) {
            throw IllegalArgumentException("Decimation must be positive")
        }
        if (cutoffFrequency <= 0.0f || cutoffFrequency > sampleFrequency / 2) {
            throw IllegalArgumentException("Cut-off frequency must be positive and below half the sample frequency")
        }
        if (transitionWidth <= 0.0f) {
            throw IllegalArgumentException("Transition width must be positive")
        }
        if (attenuation <= 0.0f) {
            throw IllegalArgumentException("Attenuation must be positive")
        }

        // Based on formula from Multirate Signal Processing for Communications Systems by Fredric J. Harris
        var numTaps = (attenuation * sampleFrequency / 22.0 / transitionWidth).toInt()
        if (numTaps % 2 == 0) {
            numTaps += 1
        }

        Log.d("FIR", "Creating low-pass FIR filter, " +
                    "sampling frequency $sampleFrequency, " +
                    "decimation $decimation, " +
                    "cutoff $cutoffFrequency, " +
                    "transition width $transitionWidth, " +
                    "attenuation $attenuation, " +
                    "gain $gain"
        )

        init(numTaps, decimation, cutoffFrequency / sampleFrequency, gain)
    }

    private fun init(numTaps: Int, decimation: Int, passBand: Float, gain: Float) {
        if (numTaps <= 0) {
            throw IllegalArgumentException("Number of taps must be positive")
        }
        if (numTaps % 2 == 0) {
            throw IllegalArgumentException("Number of taps must be odd")
        }
        if (passBand <= 0.0f) {
            throw IllegalArgumentException("Cut-off frequency must be positive")
        }
        if (decimation <= 0) {
            throw IllegalArgumentException("Decimation must be positive")
        }

        this.decimation = decimation
        this.isHalfBand = passBand == 0.25f

        // Construct the truncated ideal impulse response - sin(x) / x
        taps = FloatArray(numTaps)

        val M = (numTaps - 1) / 2
        val fwT0 = 2 * PI * passBand
        val window = Window.make(Window.Type.BLACKMAN_HARRIS, numTaps)

        for (n in -M..M) {
            if (n == 0) {
                taps[n + M] = (fwT0 / PI * window[n + M]).toFloat()
            } else {
                taps[n + M] = (sin(n * fwT0) / (n * PI) * window[n + M]).toFloat()
            }
        }

        // Find the factor to normalize the gain, fmax.
        // For low-pass, gain @ zero freq = 1.0
        var fMax = taps[M]
        for (i in 0 until M) {
            fMax += 2 * taps[i]
        }

        val actualGain = gain / fMax
        for (i in 0 until numTaps) {
            taps[i] *= actualGain
        }

        delayLine = Complex32Array(numTaps) { Complex32() }

        Log.d("FIR", "Created low-pass FIR filter with $numTaps taps, pass band $passBand, decimation $decimation, gain $gain")
        Log.d("FIR", "Taps: ${taps.joinToString(", ")}")
    }

    fun filter(input: Complex32Array, output: Complex32Array, length: Int = input.size) {
        var j = 0

        for (i in 0 until length) {
            delayLine[tapIndex].set(input[i])

            if (decimationCounter == 0) {
                if (j == output.size) {
                    return
                }

                output[j].zero()

                if (isHalfBand) {
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
    }
}
