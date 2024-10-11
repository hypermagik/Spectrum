package com.hypermagik.spectrum.lib.data

class SampleFIFO(bufferCount: Int, val bufferSize: Int) {
    private val queue = Array(bufferCount) { SampleBuffer(bufferSize) }

    @Volatile
    private var popIndex = 0
    @Volatile
    private var pushIndex = 0

    fun getPopBuffer(): SampleBuffer? {
        if (popIndex == pushIndex) {
            return null
        }
        return queue[popIndex]
    }

    fun pop() {
        popIndex = (popIndex + 1) % queue.size
    }

    fun getPushBuffer(): SampleBuffer? {
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
