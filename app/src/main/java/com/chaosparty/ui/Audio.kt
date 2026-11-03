package com.chaosparty.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

enum class Sfx { SHOT, CLICK, HEAL, BOOM, TICK, PICK, GLUE, BOING, WIN, LOSE, WHOOSH, DING, ZAP, CARD }

/**
 * All sound is synthesized on first launch into small WAV files in the cache,
 * so the APK carries no audio assets.
 */
object Audio {
    private const val RATE = 22050
    private var pool: SoundPool? = null
    private val ids = IntArray(Sfx.entries.size)
    private var player: MediaPlayer? = null
    private var wantedTrack = -1
    private var playingTrack = -1
    private var ready = false
    private lateinit var app: Context

    fun init(ctx: Context) {
        if (::app.isInitialized) return
        app = ctx.applicationContext
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val sp = SoundPool.Builder().setMaxStreams(8).setAudioAttributes(attrs).build()
        pool = sp
        Thread {
            val dir = File(app.cacheDir, "audio-v1").apply { mkdirs() }
            for (s in Sfx.entries) {
                val f = File(dir, "${s.name}.wav")
                if (!f.exists()) writeWav(f, synth(s))
                ids[s.ordinal] = sp.load(f.path, 1)
            }
            for (t in 0..1) {
                val f = File(dir, "music$t.wav")
                if (!f.exists()) writeWav(f, Music.compose(t, RATE))
            }
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                ready = true
                if (wantedTrack >= 0) music(wantedTrack)
            }
        }.apply { priority = Thread.MIN_PRIORITY }.start()
    }

    fun play(s: Sfx, volume: Float = 1f, rate: Float = 1f) {
        if (!Prefs.sfx) return
        val id = ids[s.ordinal]
        if (id != 0) pool?.play(id, volume, volume, 1, 0, rate)
    }

    /** 0 = menu track, 1 = in-game track, -1 = silence. */
    fun music(track: Int) {
        wantedTrack = track
        if (!ready) return
        if (!Prefs.music || track < 0) {
            player?.release()
            player = null
            playingTrack = -1
            return
        }
        if (playingTrack == track && player != null) {
            player?.start()
            return
        }
        player?.release()
        player = runCatching {
            MediaPlayer().apply {
                setDataSource(File(app.cacheDir, "audio-v1/music$track.wav").path)
                isLooping = true
                setVolume(0.45f, 0.45f)
                prepare()
                start()
            }
        }.getOrNull()
        playingTrack = track
    }

    fun pauseMusic() {
        runCatching { player?.pause() }
    }

    fun refreshMusic() = music(wantedTrack)

    fun resumeMusic() {
        if (wantedTrack >= 0) music(wantedTrack)
    }

    fun vibrate(ms: Long, strong: Boolean = false) {
        if (!Prefs.vibrate) return
        val v = app.getSystemService(Vibrator::class.java) ?: return
        runCatching {
            v.vibrate(VibrationEffect.createOneShot(ms, if (strong) 255 else 90))
        }
    }

    // ------------------------------------------------------------------ synthesis

    private fun synth(s: Sfx): FloatArray {
        val r = Random(s.ordinal * 77 + 3)
        return when (s) {
            Sfx.SHOT -> gen(0.55) { t, _ ->
                val noise = (r.nextFloat() * 2 - 1) * exp(-t * 14)
                val thump = sin(2 * PI * (70 - 40 * t) * t) * exp(-t * 9)
                (noise * 0.8 + thump * 0.9).toFloat()
            }.also { lowpass(it, 0.5f) }
            Sfx.CLICK -> gen(0.18) { t, _ ->
                val a = if (t < 0.012) (r.nextFloat() * 2 - 1) * 0.6 + sin(2 * PI * 2400 * t) * 0.5 else 0.0
                val b = if (t in 0.09..0.11) sin(2 * PI * 1800 * t) * exp(-(t - 0.09) * 120) * 0.6 else 0.0
                (a + b).toFloat()
            }
            Sfx.HEAL -> notes(listOf(72, 76, 79, 84), 0.075, 0.25)
            Sfx.BOOM -> gen(1.3) { t, _ ->
                val n = (r.nextFloat() * 2 - 1) * exp(-t * 3.2)
                val low = sin(2 * PI * (48 - 20 * t) * t) * exp(-t * 2.5)
                (n * 0.9 + low).toFloat()
            }.also { lowpass(it, 0.18f) }
            Sfx.TICK -> gen(0.04) { t, _ -> (sin(2 * PI * 1500 * t) * exp(-t * 90)).toFloat() * 0.6f }
            Sfx.PICK -> gen(0.16) { t, _ ->
                val f = if (t < 0.07) 660.0 else 990.0
                (sin(2 * PI * f * t) * exp(-t * 14) * 0.6).toFloat()
            }
            Sfx.GLUE -> gen(0.5) { t, _ ->
                val f = 380 - 260 * t + 25 * sin(2 * PI * 18 * t)
                (sin(2 * PI * f * t) * (1 - t / 0.5) * 0.6 + (r.nextFloat() - 0.5) * 0.08).toFloat()
            }
            Sfx.BOING -> {
                var phase = 0.0
                gen(0.6) { t, _ ->
                    val f = 180 + 500 * exp(-t * 5) * abs(sin(2 * PI * 3 * t)) + 30 * sin(2 * PI * 12 * t)
                    phase += 2 * PI * f / RATE
                    (sin(phase) * exp(-t * 3) * 0.7).toFloat()
                }
            }
            Sfx.WIN -> notes(listOf(67, 72, 76, 79, 76, 79, 84), 0.12, 0.35)
            Sfx.LOSE -> notes(listOf(67, 63, 60, 55), 0.2, 0.4)
            Sfx.WHOOSH -> gen(0.45) { t, _ ->
                val env = sin(PI * t / 0.45)
                ((r.nextFloat() * 2 - 1) * env * 0.5).toFloat()
            }.also { lowpass(it, 0.12f) }
            Sfx.DING -> gen(0.7) { t, _ ->
                ((sin(2 * PI * 880 * t) * 0.6 + sin(2 * PI * 1760 * t) * 0.25 + sin(2 * PI * 2640 * t) * 0.1) * exp(-t * 5)).toFloat()
            }
            Sfx.ZAP -> gen(0.35) { t, _ ->
                val f = 900 + 600 * sin(2 * PI * 25 * t)
                ((if (sin(2 * PI * f * t) > 0) 0.35 else -0.35) * exp(-t * 7)).toFloat()
            }
            Sfx.CARD -> gen(0.12) { t, _ -> ((r.nextFloat() * 2 - 1) * exp(-t * 40) * 0.6).toFloat() }.also { lowpass(it, 0.35f) }
        }
    }

    private inline fun gen(seconds: Double, f: (Double, Int) -> Float): FloatArray {
        val n = (seconds * RATE).toInt()
        return FloatArray(n) { i -> f(i.toDouble() / RATE, i) }
    }

    private fun notes(midi: List<Int>, step: Double, tail: Double): FloatArray {
        val out = FloatArray(((midi.size * step + tail) * RATE).toInt())
        midi.forEachIndexed { k, m ->
            val freq = 440.0 * Math.pow(2.0, (m - 69) / 12.0)
            val start = (k * step * RATE).toInt()
            val len = ((step + tail) * RATE).toInt()
            for (i in 0 until len) {
                val idx = start + i
                if (idx >= out.size) break
                val t = i.toDouble() / RATE
                out[idx] += ((sin(2 * PI * freq * t) * 0.5 + sin(4 * PI * freq * t) * 0.15) * exp(-t * 7) * 0.7).toFloat()
            }
        }
        return out
    }

    private fun lowpass(a: FloatArray, k: Float) {
        var y = 0f
        for (i in a.indices) {
            y += k * (a[i] - y)
            a[i] = y
        }
        val peak = a.maxOf { abs(it) }.coerceAtLeast(0.01f)
        for (i in a.indices) a[i] = a[i] / peak * 0.9f
    }

    private fun writeWav(f: File, samples: FloatArray) {
        val data = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) data.putShort((s.coerceIn(-1f, 1f) * 32000).toInt().toShort())
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + samples.size * 2); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(RATE); putInt(RATE * 2)
            putShort(2); putShort(16); put("data".toByteArray()); putInt(samples.size * 2)
        }
        val tmp = File(f.path + ".tmp")
        FileOutputStream(tmp).use { it.write(header.array()); it.write(data.array()) }
        tmp.renameTo(f)
    }
}

/** Tiny procedural composer for two original background loops. */
object Music {
    fun compose(track: Int, rate: Int): FloatArray {
        val bpm = if (track == 0) 96.0 else 112.0
        val beat = 60.0 / bpm
        // Chord roots (MIDI) and whether the chord is minor.
        val chords = if (track == 0) listOf(57 to true, 53 to false, 48 to false, 55 to false)
        else listOf(50 to true, 46 to false, 45 to false, 50 to true)
        val barsPerChord = 2
        val beats = chords.size * barsPerChord * 4
        val out = FloatArray((beats * beat * rate).toInt())
        val rng = Random(track * 1234 + 7)
        fun freq(m: Int) = 440.0 * Math.pow(2.0, (m - 69) / 12.0)
        fun add(startSec: Double, dur: Double, midi: Int, vol: Double, kind: Int) {
            val f = freq(midi)
            val s0 = (startSec * rate).toInt()
            val n = (dur * rate).toInt()
            for (i in 0 until n) {
                val idx = s0 + i
                if (idx >= out.size) break
                val t = i.toDouble() / rate
                val env = when (kind) {
                    0 -> exp(-t * 6)                              // pluck
                    1 -> minOf(1.0, t * 8) * minOf(1.0, (dur - t) * 4) // pad
                    else -> exp(-t * 3)                           // bell
                }
                val ph = 2 * PI * f * t
                val wave = when (kind) {
                    0 -> sin(ph) + 0.3 * sin(2 * ph)
                    1 -> sin(ph) * 0.6 + sin(ph * 1.003) * 0.4
                    else -> sin(ph) + 0.2 * sin(3 * ph)
                }
                out[idx] += (wave * env * vol).toFloat()
            }
        }
        // Lead melody: a seeded walk over the chord's scale tones.
        val scale = intArrayOf(0, 2, 3, 5, 7, 8, 10)
        var deg = 0
        chords.forEachIndexed { ci, (root, minor) ->
            val third = if (minor) 3 else 4
            val chordStart = ci * barsPerChord * 4 * beat
            // pad
            for (m in listOf(root + 12, root + 12 + third, root + 19)) add(chordStart, barsPerChord * 4 * beat, m, 0.07, 1)
            for (b in 0 until barsPerChord * 4) {
                val t0 = chordStart + b * beat
                // bass on every half beat
                add(t0, beat * 0.45, root - 12, 0.22, 0)
                add(t0 + beat / 2, beat * 0.4, if (b % 2 == 0) root - 12 + 7 else root - 12, 0.15, 0)
                // soft kick-ish thump
                if (track == 1 || b % 2 == 0) {
                    val s0 = (t0 * rate).toInt()
                    for (i in 0 until (0.12 * rate).toInt()) {
                        val idx = s0 + i
                        if (idx >= out.size) break
                        val t = i.toDouble() / rate
                        out[idx] += (sin(2 * PI * (90 - 300 * t) * t) * exp(-t * 30) * 0.35).toFloat()
                    }
                }
                // melody
                if (rng.nextFloat() < if (track == 0) 0.55f else 0.7f) {
                    deg = (deg + rng.nextInt(-2, 3)).coerceIn(0, 9)
                    val m = root + 24 + scale[deg % 7] + 12 * (deg / 7)
                    val half = rng.nextBoolean()
                    add(t0, beat * 0.9, m, 0.09, 2)
                    if (half) add(t0 + beat / 2, beat * 0.5, m + if (rng.nextBoolean()) 2 else -2, 0.06, 2)
                }
            }
        }
        val peak = out.maxOf { abs(it) }.coerceAtLeast(0.01f)
        for (i in out.indices) out[i] = out[i] / peak * 0.8f
        return out
    }
}
