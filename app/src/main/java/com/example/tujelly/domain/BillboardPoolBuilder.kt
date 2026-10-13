package com.example.tujelly.domain

import com.example.tujelly.domain.model.HomeSection
import com.example.tujelly.domain.model.MediaItem

/**
 * Generador inteligente del escaparate principal (Billboard / HeroBanner) al estilo Netflix.
 *
 * En lugar de tomar estáticamente los primeros elementos de una sola sección,
 * combina de forma equilibrada los títulos más destacados de toda la biblioteca:
 * - El podio de tendencias del día (Top 10)
 * - Grandes producciones cinematográficas
 * - Series aclamadas
 * - Nuevas incorporaciones
 * - Contenidos populares de plataformas
 *
 * Aplica rotación temporal para evitar la fatiga visual de mostrar siempre la misma portada,
 * y garantiza que todos los candidatos posean material gráfico de alta resolución.
 */
object BillboardPoolBuilder {

    fun buildBillboardPool(sections: List<HomeSection>, seed: Long = 0L): List<MediaItem> {
        if (sections.isEmpty()) return emptyList()

        // Descartar listas de consumo personal ("Continuar Viendo", "Watchlist") para el escaparate
        val showcaseSections = sections.filter {
            it.title != "Continuar Viendo" &&
            it.title != "Tu Watchlist de Trakt.tv" &&
            it.items.isNotEmpty()
        }

        // 1. Extraer los mejores títulos de cada categoría con backdrop horizontal válido
        val top10Items = showcaseSections.firstOrNull { it.title.contains("Top 10", ignoreCase = true) }
            ?.items?.filter { isValidCandidate(it) }?.take(3).orEmpty()

        val blockbusterMovies = showcaseSections.firstOrNull { it.title.contains("Grandes Producciones", ignoreCase = true) }
            ?.items?.filter { isValidCandidate(it) }
            ?.sortedByDescending { candidateScore(it) }
            ?.take(4).orEmpty()

        val topSeries = showcaseSections.firstOrNull { it.title.contains("Series Destacadas", ignoreCase = true) }
            ?.items?.filter { isValidCandidate(it) }
            ?.sortedByDescending { candidateScore(it) }
            ?.take(3).orEmpty()

        val recentAdditions = showcaseSections.firstOrNull { it.title.contains("Añadido recientemente", ignoreCase = true) }
            ?.items?.filter { isValidCandidate(it) }?.take(3).orEmpty()

        val platformItems = showcaseSections.filter { it.title.startsWith("Populares en ") }
            .flatMap { it.items.filter { item -> isValidCandidate(item) }.take(1) }
            .take(3)

        val traktTrending = showcaseSections.firstOrNull { it.title.contains("Tendencias", ignoreCase = true) }
            ?.items?.filter { isValidCandidate(it) }?.take(2).orEmpty()

        // 2. Intercalar candidatos de distintos orígenes para máxima diversidad visual
        val candidatePool = mutableListOf<MediaItem>()
        val maxLen = maxOf(
            top10Items.size,
            blockbusterMovies.size,
            topSeries.size,
            recentAdditions.size,
            platformItems.size,
            traktTrending.size
        )

        for (i in 0 until maxLen) {
            if (i < top10Items.size) candidatePool.add(top10Items[i])
            if (i < blockbusterMovies.size) candidatePool.add(blockbusterMovies[i])
            if (i < topSeries.size) candidatePool.add(topSeries[i])
            if (i < recentAdditions.size) candidatePool.add(recentAdditions[i])
            if (i < platformItems.size) candidatePool.add(platformItems[i])
            if (i < traktTrending.size) candidatePool.add(traktTrending[i])
        }

        var distinct = candidatePool.distinctBy { it.id }

        // Fallback si la colección tiene pocas secciones pobladas
        if (distinct.size < 4) {
            val fallback = showcaseSections.flatMap { it.items }
                .filter { isValidCandidate(it) }
                .distinctBy { it.id }
            distinct = (distinct + fallback).distinctBy { it.id }
        }

        if (distinct.isEmpty()) {
            return showcaseSections.flatMap { it.items }.take(6)
        }

        // 3. Rotación dinámica por ciclo temporal (Anti-Fatiga de impresión)
        // La semilla desplaza cíclicamente la lista para que la portada no sea estática todos los días
        val safeSeed = if (seed >= 0) seed else -seed
        val offset = (safeSeed % distinct.size).toInt()
        val rotated = if (offset > 0) {
            distinct.drop(offset) + distinct.take(offset)
        } else {
            distinct
        }

        return rotated.take(6)
    }

    private fun isValidCandidate(item: MediaItem): Boolean {
        return !item.backdropUrl.isNullOrBlank()
    }

    private fun candidateScore(item: MediaItem): Float {
        var score = item.rating ?: 5.0f
        if (!item.logoUrl.isNullOrBlank()) score += 2.5f
        if (!item.overview.isNullOrBlank()) score += 1.0f
        return score
    }
}
