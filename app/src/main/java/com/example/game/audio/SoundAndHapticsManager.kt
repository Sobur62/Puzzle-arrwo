package com.example.game.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

class SoundAndHapticsManager(context: Context) {

  private val appContext = context.applicationContext
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  @Volatile var sfxEnabled: Boolean = true
  @Volatile var bgmEnabled: Boolean = false
  @Volatile var vibrationEnabled: Boolean = true

  private var bgmJob: Job? = null

  private val vibrator: Vibrator? by lazy {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val mgr = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
      mgr?.defaultVibrator
    } else {
      @Suppress("DEPRECATION")
      appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
  }

  fun setBgmActive(enabled: Boolean) {
    bgmEnabled = enabled
    if (enabled) {
      startBgmIfNeeded()
    } else {
      stopBgm()
    }
  }

  fun onAppResume() {
    if (bgmEnabled) {
      startBgmIfNeeded()
    }
  }

  fun onAppPause() {
    stopBgm()
  }

  private fun startBgmIfNeeded() {
    if (bgmJob?.isActive == true) return
    bgmJob =
      scope.launch {
        // Gentle marimba/pentatonic puzzle loop notes (C5, E5, G5, A5, G5, E5, D5, G4)
        val notesHz = doubleArrayOf(523.25, 659.25, 783.99, 880.0, 783.99, 659.25, 587.33, 392.0)
        var step = 0
        while (isActive && bgmEnabled) {
          val freq = notesHz[step % notesHz.size]
          playToneInternal(freqStart = freq, freqEnd = freq * 0.995, durationMs = 260, volume = 0.045)
          step++
          delay(460L)
        }
      }
  }

  private fun stopBgm() {
    bgmJob?.cancel()
    bgmJob = null
  }

  fun playEscapeSfx() {
    if (sfxEnabled) {
      scope.launch {
        playToneInternal(freqStart = 420.0, freqEnd = 840.0, durationMs = 130, volume = 0.22)
      }
    }
    vibratePattern(longArrayOf(0, 18), intArrayOf(0, 110))
  }

  fun playBlockedSfx() {
    if (sfxEnabled) {
      scope.launch {
        playToneInternal(freqStart = 185.0, freqEnd = 95.0, durationMs = 170, volume = 0.28)
      }
    }
    vibratePattern(longArrayOf(0, 40, 35, 55), intArrayOf(0, 180, 0, 210))
  }

  fun playHintSfx() {
    if (sfxEnabled) {
      scope.launch {
        playToneInternal(freqStart = 660.0, freqEnd = 990.0, durationMs = 160, volume = 0.18)
      }
    }
    vibratePattern(longArrayOf(0, 20), intArrayOf(0, 90))
  }

  fun playClickSfx() {
    if (sfxEnabled) {
      scope.launch {
        playToneInternal(freqStart = 540.0, freqEnd = 620.0, durationMs = 45, volume = 0.12)
      }
    }
  }

  fun playWinSfx() {
    if (sfxEnabled) {
      scope.launch {
        playToneInternal(523.25, 523.25, 110, 0.22)
        delay(95L)
        playToneInternal(659.25, 659.25, 110, 0.22)
        delay(95L)
        playToneInternal(783.99, 783.99, 130, 0.22)
        delay(110L)
        playToneInternal(1046.5, 1046.5, 260, 0.24)
      }
    }
    vibratePattern(longArrayOf(0, 30, 40, 50, 40, 80), intArrayOf(0, 140, 0, 180, 0, 220))
  }

  fun playGravityDropSfx() {
    if (sfxEnabled) {
      scope.launch {
        playToneInternal(freqStart = 280.0, freqEnd = 190.0, durationMs = 85, volume = 0.14)
      }
    }
  }

  private fun vibratePattern(timings: LongArray, amplitudes: IntArray) {
    if (!vibrationEnabled) return
    val vib = vibrator ?: return
    if (!vib.hasVibrator()) return
    runCatching {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vib.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
      } else {
        @Suppress("DEPRECATION")
        vib.vibrate(timings, -1)
      }
    }
  }

  private fun playToneInternal(
    freqStart: Double,
    freqEnd: Double,
    durationMs: Int,
    volume: Double,
  ) {
    val sampleRate = 22050
    val numSamples = (sampleRate * durationMs / 1000).coerceAtLeast(1)
    val samples = ShortArray(numSamples)
    var phase = 0.0
    for (i in 0 until numSamples) {
      val t = i.toDouble() / numSamples
      val freq = freqStart + (freqEnd - freqStart) * t
      phase += 2.0 * PI * freq / sampleRate
      val envelope = (1.0 - t) * exp(-2.2 * t)
      val sample = sin(phase) * envelope * volume * Short.MAX_VALUE
      samples[i] = sample.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }

    runCatching {
      val minBuf =
        AudioTrack.getMinBufferSize(
          sampleRate,
          AudioFormat.CHANNEL_OUT_MONO,
          AudioFormat.ENCODING_PCM_16BIT,
        )
      val bufBytes = maxOf(minBuf, samples.size * 2)
      val track =
        AudioTrack.Builder()
          .setAudioAttributes(
            AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_GAME)
              .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
              .build()
          )
          .setAudioFormat(
            AudioFormat.Builder()
              .setSampleRate(sampleRate)
              .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
              .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
              .build()
          )
          .setBufferSizeInBytes(bufBytes)
          .setTransferMode(AudioTrack.MODE_STATIC)
          .build()

      track.write(samples, 0, samples.size)
      track.play()
      Thread.sleep((durationMs + 30).toLong())
      track.stop()
      track.release()
    }
  }

  fun release() {
    stopBgm()
    scope.cancel()
  }
}
