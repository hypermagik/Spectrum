package com.hypermagik.spectrum.lib.data

class SampleBuffer(size: Int) {
    var samples = Array(size) { Complex32() }
    var sampleCount = 0

    var realSamples = false

    var frequency = 0L
    var sampleRate = 0
}