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
    fun signalSampleSinCos() {
        val signal = CW(500000, 1000000, CW.Mode.SinCos)
        val sample = Complex32()

        benchmarkRule.measureRepeated {
            for (i in 0 until 1000000) {
                signal.addSignal(sample, 1.0f)
            }
        }
    }

    @Test
    fun signalSampleMinimax() {
        val signal = CW(500000, 1000000, CW.Mode.Minimax)
        val sample = Complex32()

        benchmarkRule.measureRepeated {
            for (i in 0 until 1000000) {
                signal.addSignal(sample, 1.0f)
            }
        }
    }
}
