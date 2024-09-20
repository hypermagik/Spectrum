package com.hypermagik.spectrum.lib.dsp

import com.hypermagik.spectrum.lib.data.Complex32Array
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class PLL(bandwidth: Float) {
    private val pcl = PCL().setBandwidth(bandwidth)

    fun process(input: Complex32Array, output: Complex32Array, length: Int = input.size) {
        for (i in 0 until length) {
            output[i].set(cos(pcl.phase), sin(pcl.phase))

            var phase = input[i].phase() - pcl.phase
            if (phase > PI) {
                phase -= (2 * PI).toFloat()
            } else if (phase < -PI) {
                phase += (2 * PI).toFloat()
            }

            pcl.advance(phase)
            pcl.wrapPhase()
            pcl.limitFrequency()
        }
    }
}