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
        updateEnhancer()
    }

    fun setVolumeBoost(db: Int) {
        volumeBoostDb = db.coerceIn(0, 20)
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

        // Si la ganancia total es 0 o la sesión no es válida, no inicializamos ningún efecto
        // para garantizar que la decodificación y el audio passthrough jamás sufran interferencia.
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

