package com.hypermagik.spectrum.lib.loop

import com.hypermagik.spectrum.lib.data.Complex32Array
import com.hypermagik.spectrum.lib.dsp.Utils.Companion.step
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class Costas(bandwidth: Float, minFrequency: Float = -1.0f, maxFrequency: Float = 1.0f, frequency: Float = 0.0f) {
    private val pcl = PCL()
        .setBandwidth(bandwidth)
        .setFrequency(frequency, minFrequency, maxFrequency)

    private var phi = 0.0f

    fun process(input: Complex32Array, output: Complex32Array, length: Int = input.size) {
        for (i in 0 until length) {
            output[i].setmul(input[i], cos(-pcl.phase), sin(-pcl.phase))

            val error = (output[i].re * output[i].im).coerceIn(-1.0f, 1.0f)

            pcl.advance(error)
            pcl.wrapPhase()
        }
    }

    fun processPI4(input: Complex32Array, output: Complex32Array, length: Int = input.size) {
        for (i in 0 until length) {
            output[i].setmul(input[i], cos(-pcl.phase), sin(-pcl.phase))

            phi -= PI.toFloat() / 4
            if (phi > PI) {
                phi -= 2 * PI.toFloat()
            } else if (phi < PI) {
                phi += 2 * PI.toFloat()
            }

            output[i].mul(cos(phi), sin(phi))

            val error = (step(output[i].re) * output[i].im - step(output[i].im) * output[i].re).coerceIn(-1.0f, 1.0f)

            pcl.advance(error)
            pcl.wrapPhase()
        }
    }
}