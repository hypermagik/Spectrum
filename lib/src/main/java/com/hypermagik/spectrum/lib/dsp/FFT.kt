package com.hypermagik.spectrum.lib.dsp

import com.hypermagik.spectrum.lib.data.Complex32
import com.hypermagik.spectrum.lib.data.Complex32Array
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin

class FFT(val size: Int = 256) {
    private val m: Int = (ln(size.toDouble()) / ln(2.0)).toInt()
    private val twiddles: Array<Complex32Array>
    private val product = Complex32()

    init {
        if (size != 1 shl m) {
            throw RuntimeException("FFT length must be power of 2")
        }

        twiddles = Array(m) { Complex32Array(size / 2) { Complex32() } }

        for (p in 0 until m) {
            val step = 1 shl p
            for (group in 0 until step) {
                val angle = -PI * group / step
                twiddles[p][group] = Complex32(cos(angle), sin(angle))
            }
        }
    }

    private fun rearrange(data: Complex32Array) {
        var target = 0
        for (position in 0 until size) {
            if (target > position) {
                data[target].swap(data[position])
            }

            var mask = size / 2

            while (target and mask != 0) {
                target = target and mask.inv()
                mask /= 2
            }

            target = target or mask
        }
    }

    private fun compute(data: Complex32Array) {
        for (p in 0 until m) {
            val step = 1 shl p

            for (group in 0 until step) {
                val twiddle = twiddles[p][group]

                var pair = group
                while (pair < size) {
                    product.setmul(twiddle, data[pair + step])
                    data[pair + step].setdif(data[pair], product)
                    data[pair].add(product)
                    pair += step * 2
                }
            }
        }
    }

    fun fft(data: Complex32Array) {
        if (data.size < size) {
            throw RuntimeException("FFT length mismatch")
        }

        // Based on https://lloydrochester.com/post/c/example-fft/

        rearrange(data)
        compute(data)
    }

    fun magnitudes(data: Complex32Array, output: FloatArray, outputSize: Int, realSamples: Boolean = false) {
        val scale = 1.0f / size
        val halfSize = size / 2
        val halfOutputSize = outputSize / 2
        val multiplier = outputSize / size * if (realSamples) 2 else 1

        if (realSamples) {
            if (multiplier == 2) {
                for (i in 0 until halfSize) {
                    val mag = data[i].mag(scale)
                    output[2 * i + 0] = mag
                    output[2 * i + 1] = mag
                }
            } else for (i in 0 until halfSize) {
                for (j in 0 until multiplier) {
                    output[multiplier * i + j] = data[i].mag(scale)
                }
            }
        } else {
            if (multiplier == 1) {
                for (i in 0 until size) {
                    output[(i + halfSize) % size] = data[i].mag(scale)
                }
            } else for (i in 0 until size) {
                val mag = data[i].mag(scale)
                for (j in 0 until multiplier) {
                    output[(multiplier * i + j + halfOutputSize) % outputSize] = mag
                }
            }
        }
    }
}
