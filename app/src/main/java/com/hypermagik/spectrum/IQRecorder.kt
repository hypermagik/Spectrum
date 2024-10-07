package com.hypermagik.spectrum

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.documentfile.provider.DocumentFile
import com.hypermagik.spectrum.lib.data.Complex32
import com.hypermagik.spectrum.lib.data.Complex32Array
import com.hypermagik.spectrum.lib.data.SampleType
import com.hypermagik.spectrum.lib.utils.toArray
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class IQRecorder(private val context: Context, private val preferences: Preferences) {
    private var fd: ParcelFileDescriptor? = null
    private var stream: FileOutputStream? = null
    private var channel: FileChannel? = null

    private val array = FloatArray(Complex32.MAX_ARRAY_SIZE * 2)
    private val buffer = ByteBuffer.allocateDirect(Complex32.MAX_ARRAY_SIZE * 2 * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())

    fun start(sampleType: SampleType): String? {
        val fileName = String.format("${preferences.sourceSettings.frequency}Hz_${preferences.sourceSettings.sampleRate}Sps_${sampleType}_${System.currentTimeMillis()}.iq")

        val loc = DocumentFile.fromTreeUri(context, Uri.parse(preferences.recordLocation)) ?: return "Failed to open IQ file location."
        val doc = loc.createFile("application/octet-stream", fileName) ?: return "Failed to create IQ file."

        fd = context.contentResolver.openFileDescriptor(doc.uri, "w") ?: return "Failed to open IQ file."

        stream = FileOutputStream(fd!!.fileDescriptor)
        channel = stream!!.channel

        preferences.isRecording = true

        return null
    }

    fun stop() {
        channel?.close()
        channel = null

        stream?.close()
        stream = null

        fd?.close()
        fd = null

        preferences.isRecording = false
    }

    fun isRecording(): Boolean {
        return stream != null
    }

    fun record(samples: Complex32Array) {
        val channel = channel ?: return

        samples.toArray(array)

        buffer.limit(samples.size * 2 * Float.SIZE_BYTES).rewind()
        buffer.asFloatBuffer().put(array, 0, samples.size * 2).rewind()

        channel.write(buffer)

        if (channel.position() >= preferences.recordLimit) {
            stop()
        }
    }

    fun record(samples: ByteBuffer) {
        val channel = channel ?: return

        channel.write(samples)

        if (channel.position() >= preferences.recordLimit) {
            stop()
        }
    }
}
