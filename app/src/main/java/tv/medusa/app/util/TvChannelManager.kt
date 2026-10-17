package tv.medusa.app.util

import android.content.ContentValues
import android.content.Context
import android.media.tv.TvContract
import android.util.Log
import androidx.core.net.toUri
import tv.medusa.app.domain.model.MediaItem
import java.util.concurrent.ConcurrentHashMap

/**
 * Gestor de la fila nativa "Continuar Viendo" (Watch Next) en el Launcher de Android TV.
 */
object TvChannelManager {

    private const val TAG = "TvChannelManager"

    /** Flag de soporte: si el sistema rechaza el permiso o no existe el provider, se desactiva para toda la sesión. */
    @Volatile
    private var isWatchNextSupported: Boolean = true

    /** Evita spam de escrituras en disco: mínimo 15 s entre escrituras para el mismo ítem. */
    private val lastUpdateTimes = ConcurrentHashMap<String, Long>()

    /**
     * Actualiza o añade un elemento multimedia en la lista global "Watch Next" del sistema Android TV.
     */
    fun publishToWatchNext(
        context: Context,
        mediaItem: MediaItem,
        progressMs: Long,
        durationMs: Long
    ) {
        if (!isWatchNextSupported) return
        if (progressMs <= 0 || durationMs <= 0) return

        val now = System.currentTimeMillis()
        val lastUpdate = lastUpdateTimes[mediaItem.id] ?: 0L
        if (now - lastUpdate < 15_000L) return

        try {
            val contentUri = "content://android.media.tv/watch_next_program".toUri()
            val values = ContentValues().apply {
                put(TvContract.WatchNextPrograms.COLUMN_TYPE, TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
                put(TvContract.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID, mediaItem.id)
                put(TvContract.WatchNextPrograms.COLUMN_TITLE, mediaItem.title)
                put(TvContract.WatchNextPrograms.COLUMN_SHORT_DESCRIPTION, mediaItem.overview)
                put(TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI, mediaItem.posterUrl)
                put(TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS, now)
                put(TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS, progressMs)
                put(TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS, durationMs)
                put(TvContract.WatchNextPrograms.COLUMN_INTENT_URI, "medusa://player/${mediaItem.id}")
            }

            val where = "${TvContract.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID} = ?"
            val args = arrayOf(mediaItem.id)

            val updated = context.contentResolver.update(contentUri, values, where, args)
            if (updated == 0) {
                context.contentResolver.insert(contentUri, values)
            }
            lastUpdateTimes[mediaItem.id] = now
        } catch (e: SecurityException) {
            isWatchNextSupported = false
            Log.d(TAG, "Watch Next disabled: missing permission or provider restricted (${e.message})")
        } catch (e: Exception) {
            Log.d(TAG, "Watch Next update ignored: ${e.message}")
        }
    }
}
