package tv.medusa.app.util

import android.content.Context
import android.media.MediaPlayer
import androidx.annotation.RawRes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Gestor de audio atmosférico para eventos estacionales (Halloween, Navidad, San Valentín).
 * Proporciona reproducción con bucle continuo, volumen reposado no invasivo (18-22%),
 * fundido de entrada (fade-in) y fundido de salida (fade-out) para evitar cortes bruscos.
 */
object SeasonalAudioManager {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    @Volatile
    private var mediaPlayer: MediaPlayer? = null

    @Volatile
    private var currentResId: Int = 0

    private var fadeJob: Job? = null
    private var currentVolume: Float = 0f

    /**
     * Inicia la reproducción atmosférica del evento si no está ya reproduciéndose ese mismo audio.
     */
    fun playAmbient(context: Context, @RawRes resId: Int, targetVolume: Float = 0.20f) {
        if (resId == 0) return

        fadeJob?.cancel()
        fadeJob = scope.launch {
            ThemeSongPlayerManager.stop(fadeOutMs = 200L)

            val existing = mediaPlayer
            if (existing != null && currentResId == resId) {
                if (!existing.isPlaying) {
                    runCatching { existing.start() }
                }
                rampVolume(from = currentVolume, to = targetVolume, durationMs = 300L)
                return@launch
            }

            // Si hay un player anterior con otro audio, apagarlo suavemente
            if (existing != null) {
                rampVolume(from = currentVolume, to = 0f, durationMs = 200L)
                runCatching {
                    existing.stop()
                    existing.release()
                }
                mediaPlayer = null
            }

            val newPlayer = runCatching {
                MediaPlayer.create(context.applicationContext, resId)
            }.getOrNull() ?: return@launch

            currentResId = resId
            mediaPlayer = newPlayer
            currentVolume = 0f

            runCatching {
                newPlayer.isLooping = true
                newPlayer.setVolume(0f, 0f)
                newPlayer.start()
            }.onFailure {
                runCatching { newPlayer.release() }
                mediaPlayer = null
                return@launch
            }

            rampVolume(from = 0f, to = targetVolume, durationMs = 500L)
        }
    }

    /**
     * Detiene la reproducción con un fundido suave de salida (fade-out).
     */
    fun stopAmbient(fadeOutMs: Long = 350L) {
        fadeJob?.cancel()
        val player = mediaPlayer ?: return

        fadeJob = scope.launch {
            if (fadeOutMs > 0L) {
                rampVolume(from = currentVolume, to = 0f, durationMs = fadeOutMs)
            }
            runCatching {
                player.stop()
                player.release()
            }
            if (mediaPlayer === player) {
                mediaPlayer = null
                currentResId = 0
                currentVolume = 0f
            }
        }
    }

    /**
     * Liberación inmediata (p.ej. al entrar a reproducción de vídeo o suspender la app).
     */
    fun release() {
        fadeJob?.cancel()
        fadeJob = null
        val player = mediaPlayer
        mediaPlayer = null
        currentResId = 0
        currentVolume = 0f
        runCatching {
            player?.stop()
            player?.release()
        }
    }

    private suspend fun rampVolume(from: Float, to: Float, durationMs: Long) {
        val steps = 10
        val stepDelay = durationMs / steps
        for (i in 1..steps) {
            val progress = i.toFloat() / steps
            val vol = from + (to - from) * progress
            currentVolume = vol
            runCatching {
                mediaPlayer?.setVolume(vol, vol)
            }
            delay(stepDelay)
        }
        currentVolume = to
        runCatching {
            mediaPlayer?.setVolume(to, to)
        }
    }
}
