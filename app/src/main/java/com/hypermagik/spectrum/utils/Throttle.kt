package com.hypermagik.spectrum.utils

class Throttle(private val interval: Long) {
    constructor(interval: Double) : this(interval.toLong())
    constructor(bufferSize: Int, sampleRate: Int) : this(1e9 * bufferSize / sampleRate)

    private var t0: Long = System.nanoTime()

    fun sync() {
        if (interval <= 0) {
            return
        }

        val t1 = System.nanoTime()
        val dt = t1 - t0

        if (dt > 5 * interval) {
            t0 = t1
        } else {
            if (dt < interval) {
                val sleep = interval - dt
                val us = sleep / 1000000
                val ns = sleep % 1000000

                try {
                    Thread.sleep(us, ns.toInt())
                } catch (e: InterruptedException) {
                    return
                }
            }
            t0 += interval
        }
    }
}
