package com.hypermagik.spectrum.lib.loop

import kotlin.math.PI
import kotlin.math.sqrt

class PCL {
    private var alpha = 0.0f
    private var beta = 0.0f

    var phase: Float = 0.0f
    private var minPhase: Float = -PI.toFloat()
    private var maxPhase: Float = PI.toFloat()

    var frequency: Float = 0.0f
    private var minFrequency: Float = -1.0f
    private var maxFrequency: Float = 1.0f

    fun setAlpha(alpha: Float): PCL {
        this.alpha = alpha
        return this
    }

    fun setAlphaBeta(alpha: Float, beta: Float): PCL {
        this.alpha = alpha
        this.beta = beta
        return this
    }

    fun setBandwidth(bandwidth: Float): PCL {
        val dampingFactor = sqrt(2.0) / 2.0
        val denominator = (1.0 + 2.0 * dampingFactor * bandwidth + bandwidth * bandwidth)
        alpha = ((4 * dampingFactor * bandwidth) / denominator).toFloat()
        beta = ((4 * bandwidth * bandwidth) / denominator).toFloat()
        return this
    }

    fun setFrequency(frequency: Float, minFrequency: Float, maxFrequency: Float): PCL {
        this.frequency = frequency
        this.minFrequency = minFrequency
        this.maxFrequency = maxFrequency
        return this
    }

    fun setPhase(phase: Float, minPhase: Float, maxPhase: Float): PCL {
        this.phase = phase
        this.minPhase = minPhase
        this.maxPhase = maxPhase
        return this
    }

    fun advance(error: Float) {
        frequency += beta * error

        limitFrequency()

        phase += frequency + alpha * error
    }

    fun limitFrequency() {
        frequency = frequency.coerceIn(minFrequency, maxFrequency)
    }

    fun wrapPhase() {
        while (phase < minPhase) {
            phase += maxPhase - minPhase
        }
        while (phase > maxPhase) {
            phase -= maxPhase - minPhase
        }
    }
}