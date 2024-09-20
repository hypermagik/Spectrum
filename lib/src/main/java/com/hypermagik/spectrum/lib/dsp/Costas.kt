package com.hypermagik.spectrum.lib.dsp

import com.hypermagik.spectrum.lib.data.Complex32Array
import kotlin.math.cos
import kotlin.math.sin

class Costas(bandwidth: Float, frequency: Float = 0.0f, minFrequency: Float = -1.0f, maxFrequency: Float = 1.0f) {
    private val pcl = PCL().setBandwidth(bandwidth).setFrequency(frequency, minFrequency, maxFrequency)

    fun process(input: Complex32Array, output: Complex32Array, length: Int = input.size) {
        for (i in 0 until length) {
            output[i].setmul(input[i], cos(-pcl.phase), sin(-pcl.phase))
            pcl.advance((output[i].re * output[i].im).coerceIn(-1.0f, 1.0f))
        }
    }
}