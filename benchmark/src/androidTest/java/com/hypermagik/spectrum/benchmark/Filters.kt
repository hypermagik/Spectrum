package com.hypermagik.spectrum.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hypermagik.spectrum.lib.data.Complex32
import com.hypermagik.spectrum.lib.data.Complex32Array
import com.hypermagik.spectrum.lib.dsp.Decimator
import com.hypermagik.spectrum.lib.dsp.FIR
import com.hypermagik.spectrum.lib.dsp.Resampler
import com.hypermagik.spectrum.lib.gpu.GPUAPI
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
        val taps = FloatArray(9) { random.nextDouble().toFloat() }
        val uut = FIR(taps, 2)

        benchmarkRule.measureRepeated {
            uut.filter(samples, samples)
        }
    }

    @Test
    fun lowPassFIR9HB() {
        val taps = FloatArray(9) { random.nextDouble().toFloat() }
        val uut = FIR(taps, 2, true)

        benchmarkRule.measureRepeated {
            uut.filter(samples, samples)
        }
    }

    @Test
    fun lowPassFIR19() {
        val taps = FloatArray(19) { random.nextDouble().toFloat() }
        val uut = FIR(taps, 2)

        benchmarkRule.measureRepeated {
            uut.filter(samples, samples)
        }
    }

    @Test
    fun lowPassFIR19HB() {
        val taps = FloatArray(19) { random.nextDouble().toFloat() }
        val uut = FIR(taps, 2, true)

        benchmarkRule.measureRepeated {
            uut.filter(samples, samples)
        }
    }

    @Test
    fun lowPassFIR39() {
        val taps = FloatArray(39) { random.nextDouble().toFloat() }
        val uut = FIR(taps, 2)

        benchmarkRule.measureRepeated {
            uut.filter(samples, samples)
        }
    }

    @Test
    fun lowPassFIR39HB() {
        val taps = FloatArray(39) { random.nextDouble().toFloat() }
        val uut = FIR(taps, 2, true)

        benchmarkRule.measureRepeated {
            uut.filter(samples, samples)
        }
    }

    @Test
    fun decimator8192() {
        val uut = Decimator(8192)

        benchmarkRule.measureRepeated {
            uut.decimate(samples, samples)
        }
    }

    @Test
    fun resampler9taps1024to1000() {
        val uut = Resampler(1024000, 1000000, GPUAPI.None, 9)

        benchmarkRule.measureRepeated {
            uut.resample(samples, samples)
        }
    }

    @Test
    fun resampler25taps1024to1000() {
        val uut = Resampler(1024000, 1000000, GPUAPI.None, 25)

        benchmarkRule.measureRepeated {
            uut.resample(samples, samples)
        }
    }
}
