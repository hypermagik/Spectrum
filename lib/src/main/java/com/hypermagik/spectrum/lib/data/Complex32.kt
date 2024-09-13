package com.hypermagik.spectrum.lib.data

import kotlin.math.atan2
import kotlin.math.sqrt

data class Complex32(var re: Float, var im: Float) {
    constructor() : this(0.0f, 0.0f)
    constructor(c: Complex32) : this(c.re, c.im)
    constructor(re: Double, im: Double) : this(re.toFloat(), im.toFloat())

    fun zero() {
        this.re = 0.0f
        this.im = 0.0f
    }

    fun set(c: Complex32) {
        this.re = c.re
        this.im = c.im
    }

    fun set(re: Float, im: Float) {
        this.re = re
        this.im = im
    }

    fun set(re: Double, im: Double) {
        this.re = re.toFloat()
        this.im = im.toFloat()
    }

    fun add(c: Complex32) {
        this.re += c.re
        this.im += c.im
    }

    fun add(re: Float, im: Float) {
        this.re += re
        this.im += im
    }

    fun mul(constant: Float) {
        this.re *= constant
        this.im *= constant
    }

    fun setdif(c1: Complex32, c2: Complex32) {
        this.re = c1.re - c2.re
        this.im = c1.im - c2.im
    }

    fun setmul(c1: Complex32, c2: Complex32) {
        val mre = c1.re * c2.re - c1.im * c2.im
        val mim = c1.re * c2.im + c1.im * c2.re
        this.re = mre
        this.im = mim
    }

    fun setmul(c: Complex32, re: Float, im: Float) {
        val mre = c.re * re - c.im * im
        val mim = c.re * im + c.im * re
        this.re = mre
        this.im = mim
    }

    fun setmulconj(c1: Complex32, c2: Complex32) {
        val mre = c1.re * c2.re + c1.im * c2.im
        val mim = c1.im * c2.re - c1.re * c2.im
        this.re = mre
        this.im = mim
    }

    fun swap(c: Complex32) {
        val re = c.re
        val im = c.im
        c.re = this.re
        c.im = this.im
        this.re = re
        this.im = im
    }

    fun mag(scale: Float): Float {
        val re = this.re * scale
        val im = this.im * scale
        return sqrt(re * re + im * im)
    }

    fun phase(): Float {
        return atan2(im, re)
    }
}

typealias Complex32Array = Array<Complex32>
