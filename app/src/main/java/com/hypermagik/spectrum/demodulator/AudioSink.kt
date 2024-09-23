package com.hypermagik.spectrum.demodulator

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.hypermagik.spectrum.lib.data.Complex32Array
import com.hypermagik.spectrum.lib.data.SampleBuffer
import com.hypermagik.spectrum.lib.data.SampleFIFO
import com.hypermagik.spectrum.utils.TAG
import kotlin.concurrent.thread

class AudioSink(sampleRate: Int, private val gain: Float = 1.0f) {
    private val audioBuffer = ShortArray(2 * sampleRate / 10)

    private val audioFormat = AudioFormat.Builder()
        .setSampleRate(sampleRate)
        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
        .build()

    private val audioBufferSize = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_STEREO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    private val audioAttributes = AudioAttributes.Builder()
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val audioTrack = AudioTrack(
        audioAttributes,
        audioFormat,
        audioBufferSize,
        AudioTrack.MODE_STREAM,
        AudioManager.AUDIO_SESSION_ID_GENERATE
    )

    private var sampleFifo = SampleFIFO(4, 2 * sampleRate / 10)
    private var thread: Thread? = null
    private var running = false

    fun start() {
        running = true

        thread = thread { threadFn() }
    }

    fun stop() {
        running = false

        try {
            thread?.join()
        } catch (_: InterruptedException) {
            thread?.interrupt()
        }

        sampleFifo.clear()
    }

    private fun threadFn() {
        Log.i(TAG, "Starting audio thread")

        audioTrack.play()

        while (running) {
            var samples: SampleBuffer?

            while (true) {
                samples = sampleFifo.getPopBuffer()

                if (samples != null || !running) {
                    break
                }

                try {
                    Thread.sleep(1)
                } catch (_: InterruptedException) {
                    break
                }
            }

            if (samples != null) {
                for (i in 0 until samples.sampleCount) {
                    audioBuffer[2 * i + 0] = (samples.samples[i].re * 32767 * gain).toInt().toShort()
                    audioBuffer[2 * i + 1] = (samples.samples[i].im * 32767 * gain).toInt().toShort()
                }
                sampleFifo.pop()
                audioTrack.write(audioBuffer, 0, samples.sampleCount * 2)
            }
        }

        audioTrack.stop()
    }

    fun play(left: Complex32Array, right: Complex32Array, sampleCount: Int) {
        val buffer = sampleFifo.getPushBuffer() ?: return
        for (i in 0 until sampleCount) {
            buffer.samples[i].set(left[i].re, right[i].re)
        }
        buffer.sampleCount = sampleCount
        sampleFifo.push()
    }
}