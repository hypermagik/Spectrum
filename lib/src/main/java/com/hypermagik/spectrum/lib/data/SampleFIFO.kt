package com.hypermagik.spectrum.lib.data

class SampleFIFO(bufferCount: Int, val bufferSize: Int) {
    private val queue: Array<Complex32Array> = Array(bufferCount) { Complex32Array(bufferSize) { Complex32() } }

    private var popIndex = 0
    private var pushIndex = 0

    fun getPopBuffer(): Complex32Array? {
        if (popIndex == pushIndex) {
            return null
        }
        return queue[popIndex]
    }

    fun pop() {
        popIndex = (popIndex + 1) % queue.size
    }

    fun getPushBuffer(): Complex32Array? {
        if ((pushIndex + 1) % queue.size == popIndex) {
            return null
        }
        return queue[pushIndex]
    }

    fun push() {
        pushIndex = (pushIndex + 1) % queue.size
    }

    fun clear() {
        popIndex = 0
        pushIndex = 0
    }
}
