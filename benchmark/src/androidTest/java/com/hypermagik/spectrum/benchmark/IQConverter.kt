package com.hypermagik.spectrum.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hypermagik.spectrum.lib.data.Complex32
import com.hypermagik.spectrum.lib.data.Complex32Array
import com.hypermagik.spectrum.lib.data.converter.IQConverterS12P
import com.hypermagik.spectrum.lib.data.converter.IQConverterS8
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.SplittableRandom

@RunWith(AndroidJUnit4::class)
class IQConverter {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private val samplesPerBuffer = 1000
    private val numberOfBuffers = 1000

    private val complex = Complex32Array(samplesPerBuffer) { Complex32() }

    private val random = SplittableRandom()

    @Test
    fun iqConverter8Signed() {
        val uut = IQConverterS8()

        val a = ByteArray(samplesPerBuffer * uut.getSampleSize())
        val b = ByteBuffer.wrap(a).order(ByteOrder.nativeOrder())

        for (i in a.indices) {
            a[i] = random.nextInt(255).toByte()
            a[i] = random.nextInt(255).toByte()
        }

        benchmarkRule.measureRepeated {
            for (i in 0 until numberOfBuffers) {
                b.rewind()
                uut.convert(b, complex)
            }
        }
    }

    @Test
    fun iqConverter8SignedLUT() {
        val uut = IQConverterS8()

        val a = ByteArray(samplesPerBuffer * uut.getSampleSize())
        val b = ByteBuffer.wrap(a).order(ByteOrder.nativeOrder())

        for (i in a.indices) {
            a[i] = random.nextInt(255).toByte()
            a[i] = random.nextInt(255).toByte()
        }

        benchmarkRule.measureRepeated {
            for (i in 0 until numberOfBuffers) {
                b.rewind()
                uut.convertWithLUT(b, complex)
            }
        }
    }

    @Test
    fun iqConverter8Unsigned() {
        val uut = IQConverterS8()

        val a = ByteArray(samplesPerBuffer * uut.getSampleSize())
        val b = ByteBuffer.wrap(a).order(ByteOrder.nativeOrder())

        for (i in a.indices) {
            a[i] = random.nextInt(255).toByte()
            a[i] = random.nextInt(255).toByte()
        }

        benchmarkRule.measureRepeated {
            for (i in 0 until numberOfBuffers) {
                b.rewind()
                uut.convert(b, complex)
            }
        }
    }

    @Test
    fun iqConverter8UnsignedLUT() {
        val uut = IQConverterS8()

        val a = ByteArray(samplesPerBuffer * uut.getSampleSize())
        val b = ByteBuffer.wrap(a).order(ByteOrder.nativeOrder())

        for (i in a.indices) {
            a[i] = random.nextInt(255).toByte()
            a[i] = random.nextInt(255).toByte()
        }

        benchmarkRule.measureRepeated {
            for (i in 0 until numberOfBuffers) {
                b.rewind()
                uut.convertWithLUT(b, complex)
            }
        }
    }

    @Test
    fun iqConverter12SignedPadded() {
        val uut = IQConverterS12P()

        val a = ByteArray(samplesPerBuffer * uut.getSampleSize())
        val b = ByteBuffer.wrap(a).order(ByteOrder.nativeOrder())
        val c = b.asShortBuffer()

        for (i in 0 until samplesPerBuffer) {
            c.put((random.nextInt(4096) - 2048).toShort())
            c.put((random.nextInt(4096) - 2048).toShort())
        }

        benchmarkRule.measureRepeated {
            for (i in 0 until numberOfBuffers) {
                b.rewind()
                uut.convert(b, complex)
            }
        }
    }

    @Test
    fun iqConverter12SignedPaddedLUT() {
        val uut = IQConverterS12P()

        val a = ByteArray(samplesPerBuffer * uut.getSampleSize())
        val b = ByteBuffer.wrap(a).order(ByteOrder.nativeOrder())
        val c = b.asShortBuffer()

        for (i in 0 until samplesPerBuffer) {
            c.put((random.nextInt(4096) - 2048).toShort())
            c.put((random.nextInt(4096) - 2048).toShort())
        }

        benchmarkRule.measureRepeated {
            for (i in 0 until numberOfBuffers) {
                b.rewind()
                uut.convertWithLUT(b, complex)
            }
        }
    }
}
