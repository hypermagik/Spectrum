package com.hypermagik.spectrum.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hypermagik.spectrum.lib.data.Complex32
import com.hypermagik.spectrum.lib.gen.Noise
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Noise {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    @Test
    fun noiseSample() {
        val noise = Noise()
        val sample = Complex32()

        benchmarkRule.measureRepeated {
            noise.getNoise(sample, 1.0f)
        }
    }
}
