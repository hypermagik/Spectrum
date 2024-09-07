package com.hypermagik.spectrum

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.documentfile.provider.DocumentFile
import com.hypermagik.spectrum.lib.data.Complex32Array
import com.hypermagik.spectrum.lib.data.SampleType
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class IQRecorder(private val context: Context, private val preferences: Preferences) {
    private var fd: ParcelFileDescriptor? = null
    private var stream: FileOutputStream? = null
    private var channel: FileChannel? = null

    fun start(sampleType: SampleType): String? {
        val fileName = String.format("${preferences.frequency}Hz_${preferences.sampleRate}Sps_${sampleType}_${System.currentTimeMillis()}.iq")

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

        val buffer = ByteBuffer.allocateDirect(samples.size * 2 * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())
        for (sample in samples) {
            buffer.putFloat(sample.re)
            buffer.putFloat(sample.im)
        }

        buffer.rewind()
        channel.write(buffer)

        if (channel.position() >= preferences.recordLimit) {
            stop()
        }
    }
}
