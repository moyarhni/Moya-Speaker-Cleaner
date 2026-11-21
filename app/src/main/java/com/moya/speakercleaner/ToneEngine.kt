package com.moya.speakercleaner

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import kotlin.math.PI
import kotlin.math.sin

class ToneEngine(context: Context) {
    private val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    @Volatile private var running = false
    @Volatile private var track: AudioTrack? = null
    private var worker: Thread? = null
    private var focusRequest: AudioFocusRequest? = null

    val isPlaying: Boolean get() = running

    @Synchronized
    fun start(frequencyHz: Float, seconds: Int, intensity: Float, mode: String, channel: String): Boolean {
        if (running) return false
        val safeFrequency = frequencyHz.coerceIn(20f, 20_000f)
        val safeDuration = seconds.coerceIn(1, 120)
        val safeGain = intensity.coerceIn(0f, MAX_GAIN)
        if (safeGain <= 0f || !requestFocus()) return false

        running = true
        worker = Thread({ render(safeFrequency, safeDuration, safeGain, mode, channel) }, "MoyaTone")
            .apply { priority = Thread.NORM_PRIORITY; start() }
        return true
    }

    fun stop() { running = false }

    private fun requestFocus(): Boolean {
        val listener = AudioManager.OnAudioFocusChangeListener { change ->
            if (change <= AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) stop()
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
                .setOnAudioFocusChangeListener(listener)
                .build()
            focusRequest = request
            audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun render(baseFrequency: Float, duration: Int, gain: Float, mode: String, channel: String) {
        var audioTrack: AudioTrack? = null
        try {
            val sampleRate = 44_100
            val minBytes = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
            if (minBytes <= 0) return
            val bufferFrames = maxOf(512, minBytes / 4)

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
                .setAudioFormat(AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build())
                .setBufferSizeInBytes(bufferFrames * 4)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            if (audioTrack.state != AudioTrack.STATE_INITIALIZED) return
            track = audioTrack
            audioTrack.play()

            val pcm = ShortArray(bufferFrames * 2)
            val startedAt = System.nanoTime()
            var phase = 0.0

            while (running && (System.nanoTime() - startedAt) / 1_000_000_000.0 < duration) {
                val elapsed = (System.nanoTime() - startedAt) / 1_000_000_000.0

                for (i in 0 until bufferFrames) {
                    val t = elapsed + i.toDouble() / sampleRate
                    val frequency = when (mode) {
                        "dust" -> (baseFrequency * (1.0 + 0.18 * sin(2.0 * PI * t / 3.5)))
                            .coerceIn(200.0, 20_000.0)
                        else -> baseFrequency.toDouble()
                    }

                    phase += 2.0 * PI * frequency / sampleRate
                    if (phase >= 2.0 * PI) phase -= 2.0 * PI

                    val fadeIn = (t / 0.12).coerceIn(0.0, 1.0)
                    val remaining = duration - t
                    val fadeOut = (remaining / 0.12).coerceIn(0.0, 1.0)
                    val sample = (sin(phase) * gain * minOf(fadeIn, fadeOut) * Short.MAX_VALUE)
                        .toInt().toShort()

                    val left = channel != "right"
                    val right = channel != "left"
                    pcm[i * 2] = if (left) sample else 0
                    pcm[i * 2 + 1] = if (right) sample else 0
                }

                val written = audioTrack.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING)
                if (written < 0) break
            }
        } catch (_: Exception) {
        } finally {
            running = false
            try {
                audioTrack?.pause()
                audioTrack?.flush()
                audioTrack?.release()
            } catch (_: Exception) { }
            track = null
            abandonFocus()
        }
    }

    @Synchronized
    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }

    companion object { const val MAX_GAIN = 0.70f }
}
