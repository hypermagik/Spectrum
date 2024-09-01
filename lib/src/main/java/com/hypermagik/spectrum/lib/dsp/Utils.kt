package com.hypermagik.spectrum.lib.dsp

import kotlin.math.pow

class Utils {
    companion object {
        fun db2mag(db: Float): Float {
            return 10.0.pow(db / 20.0).toFloat()
        }
    }
}
