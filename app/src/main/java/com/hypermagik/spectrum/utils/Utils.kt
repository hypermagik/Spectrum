package com.hypermagik.spectrum.utils

import java.util.Locale
import kotlin.math.abs

val Any.TAG: String
    get() {
        return javaClass.simpleName
    }

fun getFrequencyLabel(value: Double): String {
    return if (abs(value) < 1000) {
        String.format(Locale.getDefault(), "%.0fHz", value)
    } else if (abs(value) < 1000000) {
        String.format(Locale.getDefault(), "%.3fK", value / 1000.0)
    } else {
        String.format(Locale.getDefault(), "%.3fM", value / 1000000.0)
    }
}
