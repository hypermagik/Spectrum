package com.hypermagik.spectrum.lib.loop

import com.hypermagik.spectrum.lib.data.Complex32Array
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class PLL(bandwidth: Float, frequency: Float = 0.0f, minFrequency: Float = -1.0f, maxFrequency: Float = 1.0f) {
    private val pcl = PCL()
        .setBandwidth(bandwidth)
        .setFrequency(frequency, minFrequency, maxFrequency)

    fun process(input: Complex32Array, output: Complex32Array, length: Int = input.size) {
        for (i in 0 until length) {
            var phase = input[i].phase() - pcl.phase

            output[i].set(cos(pcl.phase), sin(pcl.phase))

            if (phase > PI) {
                phase -= 2 * PI.toFloat()
            } else if (phase < -PI) {
                phase += 2 * PI.toFloat()
            }

            pcl.advance(phase)
            pcl.wrapPhase()
        }
    }
}