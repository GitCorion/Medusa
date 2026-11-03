package tv.medusa.app.data.remote

import java.util.Locale

/**
 * URLs base de servicios externos, centralizadas para evitar literales duplicados
 * repartidos por repositorios, ViewModels y workers.
 *
 * No incluye la URL del servidor Jellyfin: esa es dinámica (la aporta el usuario).
 */
object ApiConfig {
    /** TMDB (The Movie Database) a través del proxy propio en Firebase.
     *  La clave real vive solo en el servidor (Cloud Functions + Secret Manager). */
    const val TMDB_BASE_URL = "https://medusa-fd388.web.app/api/tmdb/"

    /** Token no secreto que mantiene activas las comprobaciones de disponibilidad de TMDB. */
    const val TMDB_PROXY_TOKEN = "proxy"

    /** Trakt.tv. */
    const val TRAKT_BASE_URL = "https://api.trakt.tv/"

    /** Backend propio de distribución OTA (Firebase Hosting, `version.json`). */
    const val FIREBASE_OTA_BASE_URL = "https://medusa-fd388.web.app/"

    /** Catálogos de descubrimiento centralizados (sitio de Hosting independiente del OTA). */
    const val FIREBASE_CATALOG_BASE_URL = "https://medusa-catalogs.web.app/"

    /** Política de privacidad pública (Firebase Hosting). */
    const val PRIVACY_POLICY_URL = "https://medusa-fd388.web.app/privacy.html"

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

    /**
     * Región para la disponibilidad en plataformas de streaming (`watch_region`), tomada de la
     * configuración regional del dispositivo (`ES`, `MX`, `GB`…). Si el locale no declara país se
     * aplica el mismo criterio que [TMDB_LANGUAGE]: español → `ES`, cualquier otro → `US`.
     *
     * Es solo el valor **por defecto**: el ajuste «Región» de la pantalla de Ajustes manda en cuanto
     * el usuario lo elige (se guarda en DataStore y sobrevive al cambio de región del sistema).
     */
    val TMDB_WATCH_REGION: String
        get() {
            val country = Locale.getDefault().country
                .takeIf { it.length == 2 && it.all(Char::isLetter) }
                ?.uppercase()
            return country ?: if (Locale.getDefault().language == "es") "ES" else "US"
        }
}
