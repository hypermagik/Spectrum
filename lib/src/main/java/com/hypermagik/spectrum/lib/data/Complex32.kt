package com.hypermagik.spectrum.lib.data

import kotlin.math.sqrt

data class Complex32(var re: Float, var im: Float) {
    constructor() : this(0.0f, 0.0f)
    constructor(re: Double, im: Double) : this(re.toFloat(), im.toFloat())

    fun set(other: Complex32) {
        re = other.re
        im = other.im
    }

    fun set(re: Float, im: Float) {
        this.re = re
        this.im = im
    }

    fun set(re: Double, im: Double) {
        this.re = re.toFloat()
        this.im = im.toFloat()
    }

    fun add(re: Float, im: Float) {
        this.re += re
        this.im += im
    }

    fun add(re: Double, im: Double) {
        this.re += re.toFloat()
        this.im += im.toFloat()
    }

    fun mul(factor: Float) {
        re *= factor
        im *= factor
    }

    fun swap(other: Complex32) {
        val re = other.re
        val im = other.im
        other.re = this.re
        other.im = this.im
        this.re = re
        this.im = im
    }

    fun mag(scale: Float): Float {
        val re = this.re * scale
        val im = this.im * scale
        return sqrt(re * re + im * im)
    }
}

typealias Complex32Array = Array<Complex32>
