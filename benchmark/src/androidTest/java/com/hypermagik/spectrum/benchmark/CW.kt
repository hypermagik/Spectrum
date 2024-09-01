package com.hypermagik.spectrum.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hypermagik.spectrum.lib.data.Complex32
import com.hypermagik.spectrum.lib.gen.CW
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CW {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    @Test
    fun signalSample() {
        val signal = CW(1000, 1000)
        val sample = Complex32()

        benchmarkRule.measureRepeated {
            signal.addSignal(sample, 1f)
        }
    }
}
