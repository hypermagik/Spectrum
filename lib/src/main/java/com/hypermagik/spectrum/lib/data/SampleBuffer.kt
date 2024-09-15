package com.hypermagik.spectrum.lib.data

class SampleBuffer(size: Int) {
    val samples = Array(size) { Complex32() }
    var sampleCount = 0

    var frequency = 0L
    var sampleRate = 0
}