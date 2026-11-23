package tv.medusa.app.domain

import tv.medusa.app.domain.model.MediaItem

/**
 * Regla única de elegibilidad para la portada (HeroBanner).
 *
 * El hero es una superficie cinematográfica 16:9: exige **backdrop real** y **sinopsis real**.
 * Sin ellos el título vive en las filas del catálogo y la portada no se rellena recortando un
 * póster 2:3 ni con una placa inventada. El ClearLogo es deseable, no obligatorio: si falta, el
 * título se compone con tipografía.
 */
object HeroEligibility {

    private val PLACEHOLDER_OVERVIEWS = listOf(
        "sin descripción disponible",
        "sin sinopsis",
        "no description available",
        "no synopsis"
    )

    private const val MIN_OVERVIEW_LENGTH = 30

    fun isWorthy(item: MediaItem): Boolean {
        if (item.isPlayed) return false
        if (item.backdropUrl.isNullOrBlank()) return false
        val overview = item.overview?.trim().orEmpty()
        if (overview.length < MIN_OVERVIEW_LENGTH) return false
        val normalized = overview.lowercase()
        return PLACEHOLDER_OVERVIEWS.none { normalized.startsWith(it) }
    }
}
