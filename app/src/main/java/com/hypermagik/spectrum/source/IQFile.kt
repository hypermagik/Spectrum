package com.hypermagik.spectrum.source

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.hypermagik.spectrum.Preferences
import com.hypermagik.spectrum.lib.data.Complex32Array
import com.hypermagik.spectrum.utils.TAG
import com.hypermagik.spectrum.utils.Throttle
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class IQFile(private val context: Context) : Source {
    private var fd: ParcelFileDescriptor? = null
    private var stream: FileInputStream? = null
    private var fileName: String = ""
    private var fileSize = 0L

    private lateinit var byteBuffer: ByteArray
    private lateinit var shortBuffer: ByteBuffer

    private var frequency: Long = 0
    private var sampleRate: Int = 0
    private var gain: Int = 0

    private val lookupTable = FloatArray(4096)

    private val throttle = Throttle()

    init {
        for (i in 0 until 4096) {
            lookupTable[i] = (i - 2048) / 2048.0f
        }
    }

    override fun getName(): String {
        return if (fileName.isEmpty()) "IQ File" else "IQ File: $fileName"
    }

    override fun getType(): SourceType {
        return SourceType.IQFile
    }

    override fun open(preferences: Preferences): String? {
        if (preferences.iqFile == null) {
            return "No IQ file selected."
        }

        val uri = Uri.parse(preferences.iqFile)
        val doc = DocumentFile.fromSingleUri(context, uri) ?: return "Failed to open IQ file."
        val name = doc.name ?: return "Failed to open IQ file."

        Log.d(TAG, "Opening IQ file $name")

        val fd = context.contentResolver.openFileDescriptor(uri, "r") ?: return "Failed to open IQ file."
        val stream = FileInputStream(fd.fileDescriptor)

        fileName = name
        fileSize = stream.available().toLong()

        sampleRate = tryGetWaveSampleRate(stream)
        if (sampleRate == 0) {
            Regex("\\D*(\\d+)Sps\\D*").find(name)?.also {
                sampleRate = it.groupValues[1].toInt()
            }
        }
        if (sampleRate == 0) {
            return "Cannot detect sample rate from IQ file."
        }

        frequency = 1000000000L
        Regex("\\D*(\\d+)Hz\\D*").find(name)?.also {
            frequency = it.groupValues[1].toLong()
        }

        preferences.frequency = frequency
        preferences.sampleRate = sampleRate

        byteBuffer = ByteArray(preferences.getSampleFifoBufferSize() * 2 * Short.SIZE_BYTES)
        shortBuffer = ByteBuffer.wrap(byteBuffer).order(ByteOrder.LITTLE_ENDIAN)

        this.fd = fd
        this.stream = stream

        return null
    }

    private fun tryGetWaveSampleRate(stream: FileInputStream): Int {
        val header = ByteArray(44)
        stream.read(header)
        stream.skip(-44)

        byteBuffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

        val signature = String(header.sliceArray(0..3))
        if (signature != "RIFF") {
            return 0
        }

        val fileType = String(header.sliceArray(8..11))
        if (fileType != "WAVE") {
            return 0
        }

        return byteBuffer.getInt(24)
    }

    override fun close() {
        Log.d(TAG, "Closing IQ file")

        stream?.close()
        stream = null

        fd?.close()
        fd = null

        fileName = ""
        fileSize = 0
    }

    override fun start() {}

    override fun stop() {}

    override fun read(buffer: Complex32Array) {
        throttle.sync(1000000000L * buffer.size / sampleRate)

        val stream = this.stream ?: return

        var bytesRead = 0
        while (bytesRead < byteBuffer.size) {
            val n = stream.read(byteBuffer, bytesRead, byteBuffer.size - bytesRead)

            if (n > 0) {
                bytesRead += n
            } else {
                stream.skip(-fileSize)
            }
        }

        shortBuffer.rewind()

        for (i in buffer.indices) {
            buffer[i].re = lookupTable[shortBuffer.getShort() + 2048]
            buffer[i].im = lookupTable[shortBuffer.getShort() + 2048]
        }
    }

    override fun setFrequency(frequency: Long) {}

    override fun getMinimumFrequency(): Long {
        return frequency
    }

    override fun getMaximumFrequency(): Long {
        return frequency
    }

    override fun setGain(gain: Int) {
        this.gain = gain
    }

    override fun getMinimumGain(): Int {
        return -50
    }

    override fun getMaximumGain(): Int {
        return 50
    }

    override fun setAGC(enable: Boolean) {}
}
