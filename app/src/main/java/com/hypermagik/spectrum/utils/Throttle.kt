package com.hypermagik.spectrum.utils

class Throttle(private var interval: Long) {
    constructor() : this(0)

    private var t0: Long = System.nanoTime()

    fun setFPS(fps: Int): Throttle {
        interval = if (fps <= 0) 0 else 1000000000L / fps
        t0 = System.nanoTime()
        return this
    }

    fun sync(interval: Long) {
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

    fun isSynced(): Boolean {
        if (interval <= 0) {
            return true
        }

        val t1 = System.nanoTime()
        val dt = t1 - t0

        if (dt < interval) {
            return false
        }


        if (dt > 5 * interval) {
            t0 = t1
        } else {
            t0 += interval
        }

        return true
    }
}
