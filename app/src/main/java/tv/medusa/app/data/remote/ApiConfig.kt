package tv.medusa.app.data.remote

import java.util.Locale

/**
 * URLs base de servicios externos, centralizadas para evitar literales duplicados
 * repartidos por repositorios, ViewModels y workers.
 *
 * No incluye la URL del servidor Jellyfin: esa es dinámica (la aporta el usuario).
 */
object ApiConfig {
    /** TMDB (The Movie Database). */
    const val TMDB_BASE_URL = "https://api.themoviedb.org/3/"

    /** Trakt.tv. */
    const val TRAKT_BASE_URL = "https://api.trakt.tv/"

    /** Backend propio de distribución OTA (Firebase Hosting, `version.json`). */
    const val FIREBASE_OTA_BASE_URL = "https://medusa-fd388.web.app/"

    /** Base de imágenes de TMDB (pósters). */
    const val TMDB_IMAGE_BASE_W500 = "https://image.tmdb.org/t/p/w500"

    /** Base de imágenes de TMDB (backdrops). */
    const val TMDB_IMAGE_BASE_W1280 = "https://image.tmdb.org/t/p/w1280"

    /** Página pública de activación de dispositivo de Trakt. */
    const val TRAKT_ACTIVATE_URL = "https://trakt.tv/activate"

    /** Servicio público de generación de QR (el parámetro `data` se añade en el llamante). */
    const val QR_GENERATOR_BASE_URL = "https://api.qrserver.com/v1/create-qr-code/"

    /** Base de vídeos de YouTube (tráilers). */
    const val YOUTUBE_WATCH_URL = "https://www.youtube.com/watch?v="

    /** Origen permitido para incrustar el reproductor de tráilers. */
    const val YOUTUBE_EMBED_ORIGIN = "https://www.youtube-nocookie.com"

    /** Diccionario de etiquetas/géneros de Medusa alojado en GitHub. */
    const val MEDUSA_TAGS_JSON_URL =
        "https://raw.githubusercontent.com/GitCorion/Medusa/main/data/medusa_tags_es.json"

    /**
     * Idioma con el que se piden los metadatos a TMDB, alineado con el idioma de la app: español
     * si el dispositivo está en español, inglés en cualquier otro caso (los textos de la app solo
     * están traducidos a es/en, así que no tiene sentido pedir un tercer idioma a TMDB).
     *
     * Se lee en cada llamada para que un cambio de idioma del sistema se aplique sin reiniciar.
     */
    val TMDB_LANGUAGE: String
        get() = if (Locale.getDefault().language == "es") "es-ES" else "en-US"
}
