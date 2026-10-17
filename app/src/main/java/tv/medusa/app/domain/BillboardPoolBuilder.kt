package tv.medusa.app.domain

import tv.medusa.app.domain.model.HomeSection
import tv.medusa.app.domain.model.MediaItem

/**
 * Generador inteligente del escaparate principal (Billboard / HeroBanner) al estilo Netflix / Max.
 *
 * Sigue una lógica 100% de streaming contemporáneo:
 * El escaparate destaca exclusivamente NOVEDADES Y TENDENCIAS:
 * 1. Top 10 en España hoy (Tendencias del día en tu país disponibles en tu biblioteca)
 * 2. Añadido recientemente (Nuevas incorporaciones a tu servidor Jellyfin)
 * 3. Populares en Plataformas (Los buques insignia de Netflix, Prime Video, HBO Max, Disney+, Apple TV+)
 * 4. Tendencias de Trakt.tv (Lo más visto globalmente)
 *
 * Se excluyen expresamente del escaparate principal:
 * - Listas privadas de consumo personal ("Continuar Viendo", "Watchlist")
 * - Listas pasivas de catálogo estático basadas en valoraciones históricas ("Grandes Producciones", "Joyas", etc.)
 *
 * Esto evita el problema de llenar la portada con películas antiguas de archivo solo por tener notas altas en IMDb.
 */
object BillboardPoolBuilder {

    fun buildBillboardPool(sections: List<HomeSection>, seed: Long = 0L): List<MediaItem> {
        if (sections.isEmpty()) return emptyList()

        // 1. Identificar secciones dinámicas de actualidad y novedades
        val top10Section = sections.firstOrNull { it.title.contains("Top 10", ignoreCase = true) }
        val recentSection = sections.firstOrNull { it.title.contains("Añadido recientemente", ignoreCase = true) }
        val platformSections = sections.filter { it.title.startsWith("Populares en ") }
        val traktTrendingSection = sections.firstOrNull { it.title.contains("Tendencias", ignoreCase = true) }

        // Extraer candidatos válidos (con imagen horizontal y preferiblemente no reproducidos)
        val top10Candidates = top10Section?.items?.filter { isValidCandidate(it) }?.take(4).orEmpty()
        val recentCandidates = recentSection?.items?.filter { isValidCandidate(it) }?.take(4).orEmpty()
        val platformCandidates = platformSections
            .flatMap { it.items.filter { item -> isValidCandidate(item) }.take(1) }
            .take(3)
        val traktCandidates = traktTrendingSection?.items?.filter { isValidCandidate(it) }?.take(2).orEmpty()

        // 2. Intercalar candidatos de actualidad para máxima frescura y variedad:
        // Top 10 -> Novedad reciente -> Plataforma -> Trakt -> Top 10 -> Novedad...
        val candidatePool = mutableListOf<MediaItem>()
        val maxLen = maxOf(
            top10Candidates.size,
            recentCandidates.size,
            platformCandidates.size,
            traktCandidates.size
        )

        for (i in 0 until maxLen) {
            if (i < top10Candidates.size) candidatePool.add(top10Candidates[i])
            if (i < recentCandidates.size) candidatePool.add(recentCandidates[i])
            if (i < platformCandidates.size) candidatePool.add(platformCandidates[i])
            if (i < traktCandidates.size) candidatePool.add(traktCandidates[i])
        }

        var distinct = candidatePool.distinctBy { it.id }

        // 3. Fallback en caso de arranque en frío o servidor con pocos títulos dinámicos:
        // Nutrir el escaparate con más novedades del servidor ("Añadido recientemente")
        if (distinct.size < 4 && recentSection != null) {
            val fallbackRecents = recentSection.items
                .filter { isValidCandidate(it) }
                .distinctBy { it.id }
            distinct = (distinct + fallbackRecents).distinctBy { it.id }
        }

        // Si aún así no hay suficientes candidatos dinámicos, acudir al resto de secciones dinámicas
        if (distinct.size < 4) {
            val dynamicFallback = (platformSections + listOfNotNull(top10Section, traktTrendingSection))
                .flatMap { it.items }
                .filter { isValidCandidate(it) }
                .distinctBy { it.id }
            distinct = (distinct + dynamicFallback).distinctBy { it.id }
        }

        // Fallback de emergencia solo si no existiera ninguna sección de novedades o tendencias
        if (distinct.isEmpty()) {
            val emergencyFallback = sections
                .filter { it.title != "Continuar Viendo" && it.title != "Tu Watchlist de Trakt.tv" }
                .flatMap { it.items }
                .filter { !it.backdropUrl.isNullOrBlank() }
                .distinctBy { it.id }
            return emergencyFallback.take(6)
        }

        // 4. Rotación anti-fatiga con semilla diaria
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
        // Exigir imagen de fondo HD y descartar películas/series ya terminadas
        return !item.backdropUrl.isNullOrBlank() && !item.isPlayed
    }
}
