package tv.medusa.app.domain

import tv.medusa.app.domain.model.HomeSection
import tv.medusa.app.domain.model.MediaItem

/**
 * Generador del escaparate principal (Billboard / HeroBanner).
 *
 * La cabecera es una superficie de **tendencia y novedad**, no de catálogo:
 * 1. Top 10 de tendencias del día (disponible en tu biblioteca).
 * 2. Tendencias de Trakt.tv.
 * 3. Últimos estrenos de tu biblioteca (por fecha de estreno real).
 * 4. Como último recurso, lo que estabas viendo ("Continuar Viendo").
 *
 * No se usan las filas de plataforma ni el catálogo estático ("Grandes Producciones",
 * "Series Destacadas") ni "Añadido recientemente" (fecha de alta en el servidor), porque
 * llenarían la portada de contenido antiguo. Las plataformas viven en sus propias filas y
 * pantallas de descubrimiento, donde sí se muestra el catálogo completo.
 */
object BillboardPoolBuilder {

    private const val MAX_BILLBOARD_ITEMS = 6

    fun buildBillboardPool(sections: List<HomeSection>, seed: Long = 0L): List<MediaItem> {
        if (sections.isEmpty()) return emptyList()

        val top10Section = sections.firstOrNull { it.title.contains("Top 10", ignoreCase = true) }
        val traktTrendingSection = sections.firstOrNull {
            it.title.contains("Tendencias", ignoreCase = true)
        }
        val newReleasesSection = sections.firstOrNull {
            it.title.equals("Últimos estrenos", ignoreCase = true) ||
                it.title.contains("estrenos", ignoreCase = true)
        }
        val continueWatchingSection = sections.firstOrNull { it.title == "Continuar Viendo" }

        val top10Candidates = top10Section?.items
            ?.filter(::isValidCandidate)
            ?.take(4)
            .orEmpty()
        val traktCandidates = traktTrendingSection?.items
            ?.filter(::isValidCandidate)
            ?.take(2)
            .orEmpty()
        val newReleaseCandidates = newReleasesSection?.items
            ?.filter(::isValidCandidate)
            ?.take(4)
            .orEmpty()

        // Intercalar tendencia -> Trakt -> estreno para que la portada no sea monotemática.
        val pool = mutableListOf<MediaItem>()
        val maxLen = maxOf(
            top10Candidates.size,
            traktCandidates.size,
            newReleaseCandidates.size
        )
        for (i in 0 until maxLen) {
            if (i < top10Candidates.size) pool.add(top10Candidates[i])
            if (i < traktCandidates.size) pool.add(traktCandidates[i])
            if (i < newReleaseCandidates.size) pool.add(newReleaseCandidates[i])
        }

        var distinct = pool.distinctBy { it.id }

        // Relleno con novedades reales (por fecha de estreno), nunca con catálogo antiguo.
        if (distinct.size < 4) {
            distinct = (distinct + newReleaseCandidates).distinctBy { it.id }
        }
        if (distinct.size < 4) {
            distinct = (distinct + traktCandidates + top10Candidates).distinctBy { it.id }
        }
        // Último recurso: actividad reciente del usuario.
        if (distinct.isEmpty()) {
            distinct = continueWatchingSection?.items
                .orEmpty()
                .filter(::isValidCandidate)
                .distinctBy { it.id }
        }

        if (distinct.isEmpty()) return emptyList()

        // Rotación anti-fatiga con semilla diaria.
        val safeSeed = if (seed >= 0) seed else -seed
        val offset = (safeSeed % distinct.size).toInt()
        val rotated = if (offset > 0) {
            distinct.drop(offset) + distinct.take(offset)
        } else {
            distinct
        }

        return rotated.take(MAX_BILLBOARD_ITEMS)
    }

    private fun isValidCandidate(item: MediaItem): Boolean {
        // El HeroBanner pinta póster 2:3 si no hay backdrop: no descartar títulos por eso.
        val hasImage = !item.backdropUrl.isNullOrBlank() || !item.posterUrl.isNullOrBlank()
        return hasImage && !item.isPlayed
    }
}
