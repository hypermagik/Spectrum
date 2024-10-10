package com.hypermagik.spectrum.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hypermagik.spectrum.lib.data.Complex32
import com.hypermagik.spectrum.lib.data.Complex32Array
import com.hypermagik.spectrum.lib.gpu.GLESShiftDecimator
import com.hypermagik.spectrum.lib.gpu.GLES
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GLES {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    init {
        System.loadLibrary("spectrum")
    }

    @Test
    fun decimator64() {
        GLES.INSTANCE.makeCurrent(InstrumentationRegistry.getInstrumentation().context)

        val uut = GLESShiftDecimator(1000000, 64)
        uut.setShiftFrequency(-100001.0f)

        val input = Complex32Array(128 * 1024) { Complex32() }
        val output = Complex32Array(2 * 1024) { Complex32() }

        benchmarkRule.measureRepeated {
            benchmarkRule.getState().pauseTiming()
            GLES.INSTANCE.makeCurrent(InstrumentationRegistry.getInstrumentation().context)
            benchmarkRule.getState().resumeTiming()

            uut.decimate(input, output, input.size)
        }
    }
}
