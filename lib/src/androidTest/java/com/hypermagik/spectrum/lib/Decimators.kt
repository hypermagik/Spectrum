package com.hypermagik.spectrum.lib

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hypermagik.spectrum.lib.data.Complex32
import com.hypermagik.spectrum.lib.data.Complex32Array
import com.hypermagik.spectrum.lib.dsp.Decimator
import com.hypermagik.spectrum.lib.gpu.GLES
import com.hypermagik.spectrum.lib.gpu.GLESShiftDecimator
import com.hypermagik.spectrum.lib.gpu.Vulkan
import com.hypermagik.spectrum.lib.gpu.VulkanShiftDecimator
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.math.max

@RunWith(AndroidJUnit4::class)
class Decimators {
    @Test
    fun resamplersHaveSameOutput() {
        val samples = Complex32Array(8192) { Complex32(1.0f, 0.0f) }

        val decimator1 = Decimator(64)
        val output1 = Complex32Array(4096) { Complex32() }
        decimator1.decimate(samples, output1, samples.size)

        GLES.INSTANCE.makeCurrent(InstrumentationRegistry.getInstrumentation().context)

        val decimator2 = GLESShiftDecimator(1, 64)
        val output2 = Complex32Array(4096) { Complex32() }
        decimator2.decimate(samples, output2, samples.size)
        decimator2.decimate(samples, output2, samples.size)
        decimator2.close()

        System.loadLibrary("spectrum")
        Vulkan.init(InstrumentationRegistry.getInstrumentation().context)

        val decimator3 = VulkanShiftDecimator(1, 64)
        val output3 = Complex32Array(4096) { Complex32() }
        decimator3.decimate(samples, output3, samples.size)
        decimator3.decimate(samples, output3, samples.size)
        decimator3.close()

        var error1 = 0.0f
        var error2 = 0.0f

        for (i in 0 until 128) {
            error1 = max(error1, abs(output1[i].re - output2[i].re))
            error2 = max(error2, abs(output1[i].re - output3[i].re))
        }

        Log.d("Decimators", "GLES error: $error1, Vulkan error: $error2")

        check(error1 == error2)
        check(error1 < 2e-7)
        check(error2 < 2e-7)
    }
}