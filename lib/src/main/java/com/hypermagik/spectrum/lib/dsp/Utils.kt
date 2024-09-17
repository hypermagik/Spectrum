package com.hypermagik.spectrum.lib.dsp

import kotlin.math.pow
import kotlin.math.sin

class Utils {
    companion object {
        fun db2mag(db: Float): Float {
            return 10.0.pow(db / 20.0).toFloat()
        }

        fun sinc(x: Float): Float {
            return if (x == 0.0f) 1.0f else sin(x) / x
        }
    }
}
