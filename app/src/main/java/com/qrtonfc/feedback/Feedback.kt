package com.qrtonfc.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

enum class Cue { SCANNED, WRITTEN, LOCKED, LIFTED, SUCCESS, ERROR }

/**
 * Haptic + sound per step, so the whole flow can be done without looking at the screen.
 * Tones are synthesized once at startup; no audio assets needed.
 */
class Feedback(context: Context) {

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    private val sampleRate = 44_100
    private val tracks: Map<Cue, AudioTrack> = runCatching {
        mapOf(
            Cue.SCANNED to track(tone(1320.0, 0.06)),
            Cue.WRITTEN to track(tone(880.0, 0.07) + tone(1175.0, 0.09)),
            Cue.LOCKED to track(tone(220.0, 0.05, decay = 40.0) + tone(330.0, 0.08, decay = 30.0)),
            Cue.LIFTED to track(tone(990.0, 0.05)),
            Cue.SUCCESS to track(tone(1047.0, 0.08) + tone(1319.0, 0.08) + tone(1568.0, 0.18)),
            Cue.ERROR to track(tone(330.0, 0.1) + silence(0.04) + tone(262.0, 0.16)),
        )
    }.getOrDefault(emptyMap())

    fun play(cue: Cue) {
        vibrate(cue)
        tracks[cue]?.let { t ->
            runCatching {
                if (t.playState == AudioTrack.PLAYSTATE_PLAYING) t.stop()
                t.reloadStaticData()
                t.play()
            }
        }
    }

    private fun vibrate(cue: Cue) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val effect = if (Build.VERSION.SDK_INT >= 30 && v.areAllPrimitivesSupported(
                VibrationEffect.Composition.PRIMITIVE_CLICK,
                VibrationEffect.Composition.PRIMITIVE_TICK,
                VibrationEffect.Composition.PRIMITIVE_THUD,
            )
        ) {
            composed(cue)
        } else {
            when (cue) {
                Cue.SCANNED, Cue.LIFTED -> VibrationEffect.createOneShot(25, 180)
                Cue.WRITTEN -> VibrationEffect.createWaveform(longArrayOf(0, 30, 60, 30), intArrayOf(0, 200, 0, 200), -1)
                Cue.LOCKED -> VibrationEffect.createOneShot(60, 255)
                Cue.SUCCESS -> VibrationEffect.createWaveform(longArrayOf(0, 25, 50, 25, 50, 60), intArrayOf(0, 150, 0, 200, 0, 255), -1)
                Cue.ERROR -> VibrationEffect.createWaveform(longArrayOf(0, 80, 80, 80), intArrayOf(0, 255, 0, 255), -1)
            }
        }
        runCatching { v.vibrate(effect) }
    }

    private fun composed(cue: Cue): VibrationEffect {
        val c = VibrationEffect.startComposition()
        when (cue) {
            Cue.SCANNED -> c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.8f)
            Cue.LIFTED -> c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.7f)
            Cue.WRITTEN -> c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.6f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f, 60)
            Cue.LOCKED -> c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1f)
            Cue.SUCCESS -> c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.5f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.8f, 50)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f, 50)
            Cue.ERROR -> c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1f, 90)
        }
        return c.compose()
    }

    private fun tone(freq: Double, seconds: Double, decay: Double = 14.0): ShortArray {
        val n = (sampleRate * seconds).toInt()
        val attack = (sampleRate * 0.004).toInt()
        return ShortArray(n) { i ->
            val t = i.toDouble() / sampleRate
            val env = (if (i < attack) i.toDouble() / attack else 1.0) * exp(-decay * t)
            (sin(2 * PI * freq * t) * env * 0.35 * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun silence(seconds: Double) = ShortArray((sampleRate * seconds).toInt())

    private fun track(pcm: ShortArray): AudioTrack =
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
            .apply { write(pcm, 0, pcm.size) }

    fun release() = tracks.values.forEach { runCatching { it.release() } }
}
