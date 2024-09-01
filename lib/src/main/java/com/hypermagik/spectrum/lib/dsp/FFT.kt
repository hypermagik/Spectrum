package com.hypermagik.spectrum.lib.dsp

import com.hypermagik.spectrum.lib.data.Complex32
import com.hypermagik.spectrum.lib.data.Complex32Array
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.sin

class FFT(val size: Int = 256, val windowType: WindowType = WindowType.FLAT_TOP) {
    enum class WindowType { HAMMING, BLACKMAN_HARRIS, FLAT_TOP }

    private val m: Int = (ln(size.toDouble()) / ln(2.0)).toInt()
    private val twiddles: Array<Complex32Array>
    private var window: FloatArray

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

        window = FloatArray(size)

        for (i in window.indices) {
            val w = when (windowType) {
                WindowType.HAMMING -> 0.54f - 0.46f * cos(2 * PI * i / (size - 1))

                WindowType.BLACKMAN_HARRIS -> 0.35875f -
                        0.48829f * cos(2 * PI * i / (size - 1)) +
                        0.14128f * cos(4 * PI * i / (size - 1)) -
                        0.01168f * cos(6 * PI * i / (size - 1))

                WindowType.FLAT_TOP -> 1.0f -
                        1.930f * cos(2 * PI * i / (size - 1)) +
                        1.290f * cos(4 * PI * i / (size - 1)) -
                        0.388f * cos(6 * PI * i / (size - 1)) +
                        0.028f * cos(8 * PI * i / (size - 1))
            }

            window[i] = w.toFloat()
        }
    }

    fun applyWindow(data: Complex32Array) {
        for (i in 0 until size) {
            data[i].mul(window[i])
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
                    val d1 = data[pair]
                    val d2 = data[pair + step]
                    val prodre = twiddle.re * d2.re - twiddle.im * d2.im
                    val prodim = twiddle.im * d2.re + twiddle.re * d2.im
                    d2.set(d1.re - prodre, d1.im - prodim)
                    d1.add(prodre, prodim)
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

    fun magnitudes(data: Complex32Array, output: FloatArray) {
        val half = size / 2
        val scale = 1.0f / size

        for (i in output.indices) {
            output[(i + half) % size] = data[i].mag(scale)
        }
    }
}
