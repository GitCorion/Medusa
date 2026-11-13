package tv.medusa.app.util

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tv.medusa.app.data.remote.NetworkClientFactory

/**
 * Gestor de reproducción de temas de audio (Theme Songs / Bandas Sonoras) de series y películas.
 * Utiliza ExoPlayer con la factoría OkHttpClient corporativa para soportar certificados autofirmados
 * y cabeceras de Jellyfin. Cuenta con bucle continuo, volumen suave y transiciones con fade-in / fade-out.
 */
object ThemeSongPlayerManager {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    @Volatile
    private var exoPlayer: ExoPlayer? = null

    @Volatile
    private var currentUrl: String? = null

    private var fadeJob: Job? = null
    private var currentVolume: Float = 0f

    /**
     * Inicia la reproducción de la banda sonora / tema de la serie o película con fade-in.
     */
    @OptIn(UnstableApi::class)
    fun playThemeSong(context: Context, url: String, targetVolume: Float = 0.28f) {
        if (url.isBlank()) return

        fadeJob?.cancel()
        fadeJob = scope.launch {
            val existing = exoPlayer
            if (existing != null && currentUrl == url) {
                if (!existing.isPlaying) {
                    runCatching { existing.play() }
                }
                rampVolume(from = currentVolume, to = targetVolume, durationMs = 300L)
                return@launch
            }

            // Silenciar cualquier audio estacional para evitar solapamientos
            SeasonalAudioManager.stopAmbient(fadeOutMs = 200L)

            // Si hay un reproductor previo con otra URL, apagarlo suavemente y liberarlo
            if (existing != null) {
                rampVolume(from = currentVolume, to = 0f, durationMs = 200L)
                runCatching {
                    existing.stop()
                    existing.release()
                }
                exoPlayer = null
            }

            currentUrl = url
            currentVolume = 0f

            val appContext = context.applicationContext
            val httpDataSourceFactory = OkHttpDataSource.Factory(NetworkClientFactory.okHttpClient)
            val mediaSourceFactory = DefaultMediaSourceFactory(appContext)
                .setDataSourceFactory(httpDataSourceFactory)

            val newPlayer = runCatching {
                ExoPlayer.Builder(appContext)
                    .setMediaSourceFactory(mediaSourceFactory)
                    .build()
            }.getOrNull() ?: return@launch

            val audioAttributes = AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build()

            newPlayer.setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            newPlayer.repeatMode = Player.REPEAT_MODE_ONE
            newPlayer.volume = 0f

            newPlayer.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    // Silencioso ante errores de red o códec de Jellyfin
                    release()
                }
            })

            val mediaItem = MediaItem.fromUri(url)
            newPlayer.setMediaItem(mediaItem)
            newPlayer.prepare()
            newPlayer.playWhenReady = true

            exoPlayer = newPlayer

            rampVolume(from = 0f, to = targetVolume, durationMs = 800L)
        }
    }

    /**
     * Detiene la reproducción con un fundido suave de salida (fade-out).
     */
    fun stop(fadeOutMs: Long = 400L) {
        fadeJob?.cancel()
        val player = exoPlayer ?: return

        fadeJob = scope.launch {
            if (fadeOutMs > 0L) {
                rampVolume(from = currentVolume, to = 0f, durationMs = fadeOutMs)
            }
            runCatching {
                player.stop()
                player.release()
            }
            if (exoPlayer === player) {
                exoPlayer = null
                currentUrl = null
                currentVolume = 0f
            }
        }
    }

    /**
     * Liberación inmediata (p.ej. al pulsar Play para ver vídeo o suspender la app).
     */
    fun release() {
        fadeJob?.cancel()
        fadeJob = null
        val player = exoPlayer
        exoPlayer = null
        currentUrl = null
        currentVolume = 0f
        runCatching {
            player?.stop()
            player?.release()
        }
    }

    private suspend fun rampVolume(from: Float, to: Float, durationMs: Long) {
        val steps = 10
        val stepDelay = (durationMs / steps).coerceAtLeast(10L)
        for (i in 1..steps) {
            val progress = i.toFloat() / steps
            val vol = from + (to - from) * progress
            currentVolume = vol
            runCatching {
                exoPlayer?.volume = vol
            }
            delay(stepDelay)
        }
        currentVolume = to
        runCatching {
            exoPlayer?.volume = to
        }
    }
}

