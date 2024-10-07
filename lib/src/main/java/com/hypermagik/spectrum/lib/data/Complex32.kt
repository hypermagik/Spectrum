@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package com.hypermagik.spectrum.lib.data

import com.hypermagik.spectrum.lib.dsp.Utils.Companion.step
import kotlin.math.atan2
import kotlin.math.sqrt

data class Complex32(@JvmField var re: Float, @JvmField var im: Float) {
    constructor() : this(0.0f, 0.0f)
    constructor(c: Complex32) : this(c.re, c.im)
    constructor(re: Double, im: Double) : this(re.toFloat(), im.toFloat())

    companion object {
        const val MAX_ARRAY_SIZE = 128 * 1024
    }

    @kotlin.internal.InlineOnly
    inline fun zero() {
        this.re = 0.0f
        this.im = 0.0f
    }

    @kotlin.internal.InlineOnly
    inline fun set(c: Complex32) {
        this.re = c.re
        this.im = c.im
    }

    @kotlin.internal.InlineOnly
    inline fun set(re: Float, im: Float) {
        this.re = re
        this.im = im
    }

    @kotlin.internal.InlineOnly
    inline fun set(re: Double, im: Double) {
        this.re = re.toFloat()
        this.im = im.toFloat()
    }

    @kotlin.internal.InlineOnly
    inline fun add(c: Complex32) {
        this.re += c.re
        this.im += c.im
    }

    @kotlin.internal.InlineOnly
    inline fun add(re: Float, im: Float) {
        this.re += re
        this.im += im
    }

    @kotlin.internal.InlineOnly
    inline fun mul(c: Complex32) {
        val s1 = this.re * c.re
        val s2 = this.im * c.im
        val s3 = (this.re + this.im) * (c.re + c.im)
        this.re = s1 - s2
        this.im = s3 - s1 - s2
    }

    @kotlin.internal.InlineOnly
    inline fun mul(re: Float, im: Float) {
        val s1 = this.re * re
        val s2 = this.im * im
        val s3 = (this.re + this.im) * (re + im)
        this.re = s1 - s2
        this.im = s3 - s1 - s2
    }

    @kotlin.internal.InlineOnly
    inline fun mul(constant: Float) {
        this.re *= constant
        this.im *= constant
    }

    @kotlin.internal.InlineOnly
    inline fun setsum(c1: Complex32, c2: Complex32) {
        this.re = c1.re + c2.re
        this.im = c1.im + c2.im
    }

    @kotlin.internal.InlineOnly
    inline fun setdif(c1: Complex32, c2: Complex32) {
        this.re = c1.re - c2.re
        this.im = c1.im - c2.im
    }

    @kotlin.internal.InlineOnly
    inline fun setmul(c1: Complex32, c2: Complex32) {
        val s1 = c1.re * c2.re
        val s2 = c1.im * c2.im
        val s3 = (c1.re + c1.im) * (c2.re + c2.im)
        this.re = s1 - s2
        this.im = s3 - s1 - s2
    }

    @kotlin.internal.InlineOnly
    inline fun setmul(c: Complex32, constant: Float) {
        this.re = c.re * constant
        this.im = c.im * constant
    }

    @kotlin.internal.InlineOnly
    inline fun setmul(c: Complex32, re: Float, im: Float) {
        val s1 = c.re * re
        val s2 = c.im * im
        val s3 = (c.re + c.im) * (re + im)
        this.re = s1 - s2
        this.im = s3 - s1 - s2
    }

    @kotlin.internal.InlineOnly
    inline fun setmulconj(c1: Complex32, c2: Complex32) {
        val s1 = c1.re * c2.re
        val s2 = c1.im * c2.im
        val s3 = (c1.re + c1.im) * (c2.re - c2.im)
        this.re = s1 + s2
        this.im = s3 - s1 + s2
    }

    @kotlin.internal.InlineOnly
    inline fun setstep(c: Complex32) {
        this.re = step(c.re)
        this.im = step(c.im)
    }

    @kotlin.internal.InlineOnly
    inline fun addmul(c1: Complex32, c2: Complex32) {
        val s1 = c1.re * c2.re
        val s2 = c1.im * c2.im
        val s3 = (c1.re + c1.im) * (c2.re + c2.im)
        this.re += s1 - s2
        this.im += s3 - s1 - s2
    }

    @kotlin.internal.InlineOnly
    inline fun addmul(c1: Complex32, constant: Float) {
        this.re += c1.re * constant
        this.im += c1.im * constant
    }

    @kotlin.internal.InlineOnly
    inline fun swap(c: Complex32) {
        val re = c.re
        val im = c.im
        c.re = this.re
        c.im = this.im
        this.re = re
        this.im = im
    }

    @kotlin.internal.InlineOnly
    inline fun mag(scale: Float = 1.0f): Float {
        val re = this.re * scale
        val im = this.im * scale
        return sqrt(re * re + im * im)
    }

    @kotlin.internal.InlineOnly
    inline fun phase(): Float {
        return atan2(im, re)
    }
}

typealias Complex32Array = Array<Complex32>
