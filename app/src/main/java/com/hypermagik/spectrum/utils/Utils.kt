package com.hypermagik.spectrum.utils

val Any.TAG: String
    get() {
        return javaClass.simpleName
    }

fun Double.format(digits: Int) = "%.${digits}f".format(this)
