package com.hypermagik.spectrum.demodulator

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.hypermagik.spectrum.lib.data.Complex32Array

class AudioSink(sampleRate: Int) {
    private val audioBuffer = ShortArray(2 * sampleRate / 10)

    private val audioFormat = AudioFormat.Builder()
        .setSampleRate(sampleRate)
        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
        .build()

    private val audioBufferSize = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_STEREO,
        AudioFormat.ENCODING_PCM_16BIT)

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

    fun play(samples: Complex32Array, sampleCount: Int, gain: Float = 1.0f) {
        for (i in 0 until sampleCount) {
            audioBuffer[2 * i + 0] = (samples[i].re * 32767 * gain).toInt().toShort()
            audioBuffer[2 * i + 1] = (samples[i].re * 32767 * gain).toInt().toShort()
        }

        audioTrack.write(audioBuffer, 0, sampleCount * 2)
    }

    fun play(left: Complex32Array, right: Complex32Array, sampleCount: Int, gain: Float = 1.0f) {
        for (i in 0 until sampleCount) {
            audioBuffer[2 * i + 0] = (left[i].re * 32767 * gain).toInt().toShort()
            audioBuffer[2 * i + 1] = (right[i].re * 32767 * gain).toInt().toShort()
        }

        audioTrack.write(audioBuffer, 0, sampleCount * 2)
    }
}