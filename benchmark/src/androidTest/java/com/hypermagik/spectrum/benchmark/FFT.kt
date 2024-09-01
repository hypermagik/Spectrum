package com.hypermagik.spectrum.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hypermagik.spectrum.lib.data.Complex32
import com.hypermagik.spectrum.lib.data.Complex32Array
import com.hypermagik.spectrum.lib.dsp.FFT
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FFT {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    @Test
    fun fft256() {
        val fft = FFT(256)
        val samples = Complex32Array(256) { Complex32() }

        benchmarkRule.measureRepeated {
            fft.fft(samples)
        }
    }

    @Test
    fun fft4096() {
        val fft = FFT(4096)
        val samples = Complex32Array(4096) { Complex32() }

        benchmarkRule.measureRepeated {
            fft.fft(samples)
        }
    }

    @Test
    fun fft32768() {
        val fft = FFT(32768)
        val samples = Complex32Array(32768) { Complex32() }

        benchmarkRule.measureRepeated {
            fft.fft(samples)
        }
    }
}
