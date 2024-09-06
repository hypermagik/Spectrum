package com.hypermagik.spectrum.source

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.hypermagik.spectrum.Preferences
import com.hypermagik.spectrum.lib.data.Complex32Array
import com.hypermagik.spectrum.lib.data.converter.IQConverter
import com.hypermagik.spectrum.lib.data.converter.IQConverterFactory
import com.hypermagik.spectrum.lib.dsp.Utils
import com.hypermagik.spectrum.utils.TAG
import com.hypermagik.spectrum.utils.Throttle
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.ClosedByInterruptException
import java.nio.channels.FileChannel
import kotlin.math.abs

class IQFile(private val context: Context) : Source {
    private var fd: ParcelFileDescriptor? = null
    private var stream: FileInputStream? = null
    private var channel: FileChannel? = null

    private var fileName: String = ""
    private var headerSize = 0

    private lateinit var converter: IQConverter

    private var bufferSize = 0
    private lateinit var byteBuffer: ByteBuffer

    private var frequency: Long = 0
    private var sampleRate: Int = 0
    private var gain: Int = 0

    private val throttle = Throttle()

    override fun getName(): String {
        return if (fileName.isEmpty()) "IQ file" else "IQ file: $fileName"
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

        parseWaveHeader(stream)

        if (sampleRate == 0) {
            Regex("\\D*(\\d+)Sps\\D*").find(name)?.also {
                sampleRate = it.groupValues[1].toInt()
            }
        }

        if (sampleRate == 0) {
            stream.close()
            fd.close()
            return "Cannot detect sample rate from IQ file."
        }

        frequency = 1000000000L
        Regex("\\D*(\\d+)Hz\\D*").find(name)?.also {
            frequency = it.groupValues[1].toLong()
        }

        preferences.frequency = frequency
        preferences.sampleRate = sampleRate
        gain = preferences.gain

        fileName = name

        converter = IQConverterFactory.create(preferences.iqFileType)

        bufferSize = preferences.getSampleFifoBufferSize() * converter.getSampleSize()
        byteBuffer = ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.LITTLE_ENDIAN)

        this.fd = fd
        this.stream = stream
        this.channel = stream.channel

        return null
    }

    private fun parseWaveHeader(stream: FileInputStream) {
        val waveHeaderSize = 44

        val header = ByteArray(waveHeaderSize)
        stream.read(header)
        stream.skip(-waveHeaderSize.toLong())

        byteBuffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

        val signature = String(header.sliceArray(0..3))
        if (signature != "RIFF") {
            return
        }

        val fileType = String(header.sliceArray(8..11))
        if (fileType != "WAVE") {
            return
        }

        headerSize = waveHeaderSize
        sampleRate = byteBuffer.getInt(24)
    }

    override fun close() {
        Log.d(TAG, "Closing IQ file")

        channel?.close()
        channel = null

        stream?.close()
        stream = null

        fd?.close()
        fd = null
    }

    override fun start() {}

    override fun stop() {}

    override fun read(buffer: Complex32Array) {
        val channel = this.channel ?: return

        byteBuffer.rewind()

        try {
            while (byteBuffer.position() != bufferSize) {
                if (channel.read(byteBuffer) <= 0) {
                    channel.position(headerSize.toLong())
                }
            }
        } catch (e: ClosedByInterruptException) {
            return
        }

        byteBuffer.rewind()

        converter.convert(byteBuffer, buffer)

        if (gain != 0) {
            var mag = Utils.db2mag(abs(gain).toFloat())
            if (gain < 0) {
                mag = 1.0f / mag
            }
            for (i in buffer.indices) {
                buffer[i].re *= mag
                buffer[i].im *= mag
            }
        }

        throttle.sync(1000000000L * buffer.size / sampleRate)
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
