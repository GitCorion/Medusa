package tv.medusa.app.util

import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener

/**
 * Gestor de mejoras de audio para ExoPlayer en Android TV:
 * 1. Modo Nocturno (Compresor de Rango Dinámico / DRC):
 *    - Realza los diálogos y frecuencias medias débiles.
 *    - Aplica limitador para evitar explosiones y picos estruendosos en altavoces de TV.
 * 2. Amplificador de Volumen (Estilo Kodi Volume Amplification):
 *    - Aumenta la ganancia para pistas 5.1 / 7.1 que al downmix a estéreo suenan muy bajas.
 *    - Utiliza LoudnessEnhancer nativo con compresión suave para no distorsionar ni saturar.
 */
@OptIn(UnstableApi::class)
class AudioEnhancementManager {

    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var currentSessionId: Int = C.AUDIO_SESSION_ID_UNSET

    /** 0: Desactivado, 1: Suave (+4 dB), 2: Fuerte (+8 dB) */
    var nightModeLevel: Int = 0
        private set

    /** 0: 0 dB (Normal), 3: +3 dB, 6: +6 dB, 10: +10 dB, 15: +15 dB */
    var volumeBoostDb: Int = 0
        private set

    fun attachToPlayer(exoPlayer: ExoPlayer) {
        val sessionId = exoPlayer.audioSessionId
        if (sessionId != C.AUDIO_SESSION_ID_UNSET && sessionId > 0) {
            initSession(sessionId)
        }
        exoPlayer.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioSessionIdChanged(eventTime: AnalyticsListener.EventTime, audioSessionId: Int) {
                if (audioSessionId != C.AUDIO_SESSION_ID_UNSET && audioSessionId > 0 && audioSessionId != currentSessionId) {
                    initSession(audioSessionId)
                }
            }
        })
    }

    private fun initSession(sessionId: Int) {
        if (currentSessionId == sessionId && loudnessEnhancer != null) return
        release()
        try {
            val enhancer = LoudnessEnhancer(sessionId)
            applyGains(enhancer)
            loudnessEnhancer = enhancer
            currentSessionId = sessionId
            Log.d(TAG, "LoudnessEnhancer conectado exitosamente a audioSessionId: $sessionId")
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo inicializar LoudnessEnhancer para la sesión $sessionId: ${e.message}")
            loudnessEnhancer = null
            currentSessionId = C.AUDIO_SESSION_ID_UNSET
        }
    }

    fun setNightMode(level: Int) {
        nightModeLevel = level.coerceIn(0, 2)
        updateEnhancer()
    }

    fun setVolumeBoost(db: Int) {
        volumeBoostDb = db.coerceIn(0, 20)
        updateEnhancer()
    }

    private fun updateEnhancer() {
        val enhancer = loudnessEnhancer ?: return
        applyGains(enhancer)
    }

    private fun applyGains(enhancer: LoudnessEnhancer) {
        try {
            // Modo Nocturno (DRC):
            // 0: 0 dB
            // 1 (Suave): +4 dB (400 mB) de ganancia sobre señales débiles con limitador de picos
            // 2 (Fuerte): +8 dB (800 mB) de compresión intensa para noche profunda
            val nightGainMb = when (nightModeLevel) {
                1 -> 400
                2 -> 800
                else -> 0
            }

            // Amplificador de Volumen estilo Kodi:
            // Cada 1 dB = 100 mB (milisbelios)
            // 0 dB: 0 mB (Normal)
            // +3 dB: 300 mB
            // +6 dB: 600 mB (Doble de presión sonora percibida)
            // +10 dB: 1000 mB
            // +15 dB: 1500 mB
            val boostGainMb = volumeBoostDb * 100

            val totalGainMb = nightGainMb + boostGainMb
            if (totalGainMb > 0) {
                enhancer.setTargetGain(totalGainMb)
                if (!enhancer.enabled) {
                    enhancer.enabled = true
                }
            } else {
                if (enhancer.enabled) {
                    enhancer.enabled = false
                }
            }
            Log.d(TAG, "Audio enhancement aplicado: nightMode=$nightModeLevel, volumeBoost=${volumeBoostDb}dB -> totalTargetGain=${totalGainMb}mB (enabled=${totalGainMb > 0})")
        } catch (e: Exception) {
            Log.w(TAG, "Error aplicando ganancias a LoudnessEnhancer: ${e.message}")
        }
    }

    fun release() {
        try {
            loudnessEnhancer?.enabled = false
            loudnessEnhancer?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error liberando LoudnessEnhancer: ${e.message}")
        } finally {
            loudnessEnhancer = null
            currentSessionId = C.AUDIO_SESSION_ID_UNSET
        }
    }

    companion object {
        private const val TAG = "AudioEnhancementMgr"
    }
}
