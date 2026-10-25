package tv.medusa.app.util

import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.tanh

/**
 * Gestor de mejoras de audio para ExoPlayer en Android TV:
 * 1. AudioGainProcessor (Nativo en el pipeline de decodificación de ExoPlayer):
 *    - Opera directamente sobre muestras PCM decodificadas antes de enviarse al AudioTrack.
 *    - Garantiza amplificación de volumen 100% efectiva en cualquier televisor/dispositivo sin
 *      depender de efectos del sistema HAL que suelen estar desactivados o fallar en Smart TVs.
 *    - Incluye soft-knee limiter para evitar distorsión o clipeo digital al subir la ganancia.
 *    - Implementa compresión dinámica para el Modo Nocturno (realce de diálogos débiles).
 * 2. LoudnessEnhancer (Efecto del sistema Android como complemento si la sesión de audio lo soporta).
 */
@OptIn(UnstableApi::class)
class AudioEnhancementManager {

    val audioProcessor = AudioGainProcessor()

    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var currentSessionId: Int = C.AUDIO_SESSION_ID_UNSET

    /** 0: Desactivado, 1: Suave (+4 dB), 2: Fuerte (+8 dB) */
    var nightModeLevel: Int = 0
        private set

    /** 0 dB (Normal) a 20 dB (Máximo) */
    var volumeBoostDb: Int = 0
        private set

    fun attachToPlayer(exoPlayer: ExoPlayer) {
        val sessionId = try {
            exoPlayer.audioSessionId
        } catch (_: Throwable) {
            C.AUDIO_SESSION_ID_UNSET
        }
        if (sessionId != C.AUDIO_SESSION_ID_UNSET && sessionId > 0) {
            currentSessionId = sessionId
            updateEnhancer()
        }
        try {
            exoPlayer.addAnalyticsListener(object : AnalyticsListener {
                override fun onAudioSessionIdChanged(eventTime: AnalyticsListener.EventTime, audioSessionId: Int) {
                    if (audioSessionId != C.AUDIO_SESSION_ID_UNSET && audioSessionId > 0 && audioSessionId != currentSessionId) {
                        release()
                        currentSessionId = audioSessionId
                        updateEnhancer()
                    }
                }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "No se pudo registrar AnalyticsListener para AudioSessionId: ${t.message}")
        }
    }

    fun setNightMode(level: Int) {
        nightModeLevel = level.coerceIn(0, 2)
        audioProcessor.setParameters(volumeBoostDb, nightModeLevel)
        updateEnhancer()
    }

    fun setVolumeBoost(db: Int) {
        volumeBoostDb = db.coerceIn(0, 20)
        audioProcessor.setParameters(volumeBoostDb, nightModeLevel)
        updateEnhancer()
    }

    private fun calculateTotalGainMb(): Int {
        val nightGainMb = when (nightModeLevel) {
            1 -> 400  // +4 dB
            2 -> 800  // +8 dB
            else -> 0
        }
        val boostGainMb = volumeBoostDb * 100 // 1 dB = 100 mB
        return nightGainMb + boostGainMb
    }

    private fun updateEnhancer() {
        val sessionId = currentSessionId
        val totalGainMb = calculateTotalGainMb()

        if (totalGainMb <= 0 || sessionId == C.AUDIO_SESSION_ID_UNSET || sessionId <= 0) {
            release()
            return
        }

        try {
            var enhancer = loudnessEnhancer
            if (enhancer == null) {
                enhancer = LoudnessEnhancer(sessionId)
                loudnessEnhancer = enhancer
            }
            enhancer.setTargetGain(totalGainMb)
            if (!enhancer.enabled) {
                enhancer.enabled = true
            }
            Log.d(TAG, "Audio enhancement activo: nightMode=$nightModeLevel, volumeBoost=${volumeBoostDb}dB -> totalTargetGain=${totalGainMb}mB")
        } catch (t: Throwable) {
            Log.w(TAG, "No se pudo inicializar LoudnessEnhancer para la sesión $sessionId: ${t.message}")
            release()
        }
    }

    fun release() {
        try {
            loudnessEnhancer?.enabled = false
            loudnessEnhancer?.release()
        } catch (e: Throwable) {
            Log.w(TAG, "Error liberando LoudnessEnhancer: ${e.message}")
        } finally {
            loudnessEnhancer = null
        }
    }

    companion object {
        private const val TAG = "AudioEnhancementMgr"
    }
}

/**
 * Procesador de audio en tiempo real para ExoPlayer:
 * Modifica las muestras de audio PCM antes del renderizado en hardware,
 * permitiendo amplificación suave y sin distorsión (Volume Boost)
 * así como compresión de rango dinámico para modo nocturno.
 */
@UnstableApi
class AudioGainProcessor : BaseAudioProcessor() {

    @Volatile
    private var volumeBoostDb: Int = 0

    @Volatile
    private var nightModeLevel: Int = 0

    @Volatile
    private var gainMultiplier: Float = 1.0f

    fun setParameters(boostDb: Int, nightMode: Int) {
        volumeBoostDb = boostDb.coerceIn(0, 20)
        nightModeLevel = nightMode.coerceIn(0, 2)

        val totalDb = volumeBoostDb + when (nightModeLevel) {
            1 -> 4
            2 -> 8
            else -> 0
        }

        gainMultiplier = if (totalDb > 0) {
            10.0.pow(totalDb.toDouble() / 20.0).toFloat()
        } else {
            1.0f
        }
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        return when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT, C.ENCODING_PCM_FLOAT -> inputAudioFormat
            else -> AudioProcessor.AudioFormat.NOT_SET
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val mult = gainMultiplier
        val night = nightModeLevel

        // Bypass directo sin procesamiento si la ganancia es 1.0 y no hay modo nocturno activo
        if (mult <= 1.001f && night == 0) {
            val output = replaceOutputBuffer(remaining)
            output.put(inputBuffer)
            output.flip()
            return
        }

        val output = replaceOutputBuffer(remaining)
        val encoding = inputAudioFormat.encoding

        if (encoding == C.ENCODING_PCM_16BIT) {
            processPcm16(inputBuffer, output, mult, night)
        } else if (encoding == C.ENCODING_PCM_FLOAT) {
            processPcmFloat(inputBuffer, output, mult, night)
        } else {
            output.put(inputBuffer)
        }

        output.flip()
    }

    private fun processPcm16(
        input: ByteBuffer,
        output: ByteBuffer,
        gain: Float,
        nightMode: Int
    ) {
        input.order(ByteOrder.nativeOrder())
        output.order(ByteOrder.nativeOrder())

        while (input.remaining() >= 2) {
            val sample = input.getShort()
            var sampleNorm = sample / 32768.0f

            // Compresión de rango dinámico nocturno (realza diálogos y suaviza picos)
            if (nightMode > 0) {
                val absNorm = abs(sampleNorm)
                if (absNorm < 0.25f) {
                    val boost = if (nightMode == 2) 1.5f else 1.25f
                    sampleNorm *= boost
                } else if (absNorm > 0.70f) {
                    val compress = if (nightMode == 2) 0.80f else 0.90f
                    sampleNorm = sign(sampleNorm) * (0.70f + (absNorm - 0.70f) * compress)
                }
            }

            // Aplicar ganancia lineal
            val boosted = sampleNorm * gain

            // Soft limiter con curva suave tipo tanh para prevenir clipeo
            val limited = if (boosted > 0.92f) {
                0.92f + 0.08f * tanh(((boosted - 0.92f) / 0.08f).toDouble()).toFloat()
            } else if (boosted < -0.92f) {
                -0.92f - 0.08f * tanh(((-boosted - 0.92f) / 0.08f).toDouble()).toFloat()
            } else {
                boosted
            }

            val outShort = (limited * 32767.0f).toInt().coerceIn(-32768, 32767).toShort()
            output.putShort(outShort)
        }

        while (input.hasRemaining()) {
            output.put(input.get())
        }
    }

    private fun processPcmFloat(
        input: ByteBuffer,
        output: ByteBuffer,
        gain: Float,
        nightMode: Int
    ) {
        input.order(ByteOrder.nativeOrder())
        output.order(ByteOrder.nativeOrder())

        while (input.remaining() >= 4) {
            var sample = input.getFloat()

            if (nightMode > 0) {
                val absNorm = abs(sample)
                if (absNorm < 0.25f) {
                    val boost = if (nightMode == 2) 1.5f else 1.25f
                    sample *= boost
                } else if (absNorm > 0.70f) {
                    val compress = if (nightMode == 2) 0.80f else 0.90f
                    sample = sign(sample) * (0.70f + (absNorm - 0.70f) * compress)
                }
            }

            val boosted = sample * gain
            val limited = if (boosted > 0.92f) {
                0.92f + 0.08f * tanh(((boosted - 0.92f) / 0.08f).toDouble()).toFloat()
            } else if (boosted < -0.92f) {
                -0.92f - 0.08f * tanh(((-boosted - 0.92f) / 0.08f).toDouble()).toFloat()
            } else {
                boosted
            }

            output.putFloat(limited.coerceIn(-1.0f, 1.0f))
        }

        while (input.hasRemaining()) {
            output.put(input.get())
        }
    }
}


