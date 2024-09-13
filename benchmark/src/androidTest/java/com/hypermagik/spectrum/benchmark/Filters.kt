package com.hypermagik.spectrum.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hypermagik.spectrum.lib.data.Complex32
import com.hypermagik.spectrum.lib.data.Complex32Array
import com.hypermagik.spectrum.lib.dsp.LowPassFIR
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.SplittableRandom

@RunWith(AndroidJUnit4::class)
class Filters {

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
    fun lowPassFIR9() {
        val uut = LowPassFIR(9, 2, 0.2f)

        benchmarkRule.measureRepeated {
            uut.filter(samples, samples)
        }
    }

    @Test
    fun lowPassFIR9HB() {
        val uut = LowPassFIR(9)

        benchmarkRule.measureRepeated {
            uut.filter(samples, samples)
        }
    }

    @Test
    fun lowPassFIR19() {
        val uut = LowPassFIR(19, 2, 0.2f)

        benchmarkRule.measureRepeated {
            uut.filter(samples, samples)
        }
    }

    @Test
    fun lowPassFIR19HB() {
        val uut = LowPassFIR(19)

        benchmarkRule.measureRepeated {
            uut.filter(samples, samples)
        }
    }

    @Test
    fun lowPassFIR39() {
        val uut = LowPassFIR(39, 2, 0.2f)

        benchmarkRule.measureRepeated {
            uut.filter(samples, samples)
        }
    }

    @Test
    fun lowPassFIR39HB() {
        val uut = LowPassFIR(39)

        benchmarkRule.measureRepeated {
            uut.filter(samples, samples)
        }
    }
}
