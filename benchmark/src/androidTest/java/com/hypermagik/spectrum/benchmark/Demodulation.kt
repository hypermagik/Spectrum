package com.hypermagik.spectrum.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hypermagik.spectrum.lib.data.Complex32
import com.hypermagik.spectrum.lib.data.Complex32Array
import com.hypermagik.spectrum.lib.demod.Quadrature
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.SplittableRandom

@RunWith(AndroidJUnit4::class)
class Demodulation {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private val samples = Complex32Array(1000000) { Complex32() }
    private val random = SplittableRandom()

    init {
        for (i in samples.indices) {
            samples[i].set(random.nextDouble(), random.nextDouble())
        }
    }

    @Test
    fun quadrature() {
        val uut = Quadrature(1000000, 75000)

        benchmarkRule.measureRepeated {
            uut.demodulate(samples, samples)
        }
    }
}
