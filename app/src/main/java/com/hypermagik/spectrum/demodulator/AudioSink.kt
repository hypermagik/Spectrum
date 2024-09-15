package com.hypermagik.spectrum.demodulator

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.hypermagik.spectrum.lib.data.Complex32Array

class AudioSink(sampleRate: Int) {
    private val audioBuffer = ShortArray(sampleRate)

    private val audioFormat = AudioFormat.Builder()
        .setSampleRate(31250)
        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
        .build()

    private val audioBufferSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)

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

    fun start() {
        audioTrack.play()
    }

    fun stop() {
        audioTrack.stop()
    }

    fun play(samples: Complex32Array, sampleCount: Int) {
        for (i in 0 until sampleCount) {
            audioBuffer[i] = (samples[i].re * 32767).toInt().toShort()
        }

        audioTrack.write(audioBuffer, 0, sampleCount)
    }
}