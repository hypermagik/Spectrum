package com.hypermagik.spectrum.lib.dsp

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

class Utils {
    companion object {
        fun Double.toRadians(): Double {
            return 2 * PI * this
        }

        fun Float.toRadians(): Float {
            return (2 * PI * this).toFloat()
        }

        fun db2mag(db: Float): Float {
            return 10.0.pow(db / 20.0).toFloat()
        }

        fun sinc(x: Float): Float {
            return if (x == 0.0f) 1.0f else sin(x) / x
        }

        fun gcd(a: Int, b: Int): Int {
            var x = a
            var y = b
            while (y != 0) {
                val t = y
                y = x % y
                x = t
            }
            return x
        }

        fun step(value: Float): Float {
            return if (value > 0.0f) 1.0f else -1.0f
        }
    }
}
