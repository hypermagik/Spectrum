package com.hypermagik.spectrum.lib.dsp

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

@Suppress("NOTHING_TO_INLINE")
class Utils {
    companion object {
        inline fun Double.toRadians(): Double {
            return 2 * PI * this
        }

        inline fun Float.toRadians(): Float {
            return (2 * PI * this).toFloat()
        }

        inline fun db2mag(db: Float): Float {
            return 10.0.pow(db / 20.0).toFloat()
        }

        const val S1 = 0.999999999978849
        const val S2 = -0.1666666660882607
        const val S3 = 0.008333330720557737
        const val S4 = -1.9840832823261957E-4
        const val S5 = 2.752397107463265E-6
        const val S6 = -2.3868346521031026E-8

        inline fun minimaxSin(x: Float): Float {
            val x2 = x * x
            return (x * (S1 + x2 * (S2 + x2 * (S3 + x2 * (S4 + x2 * (S5 + x2 * S6)))))).toFloat()
        }

        const val C1 = 0.999999999996645
        const val C2 = -0.49999999990409344
        const val C3 = 0.041666666191989846
        const val C4 = -0.0013888879703277091
        const val C5 = 2.4800713655614513E-5
        const val C6 = -2.751356111645714E-7
        const val C7 = 1.9764418299584176E-9

        inline fun minimaxCos(x: Float): Float {
            val x2 = x * x
            return (C1 + x2 * (C2 + x2 * (C3 + x2 * (C4 + x2 * (C5 + x2 * (C6 + x2 * C7)))))).toFloat()
        }

        inline fun sinc(x: Float): Float {
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

        inline fun step(value: Float): Float {
            return if (value > 0.0f) 1.0f else -1.0f
        }
    }
}
