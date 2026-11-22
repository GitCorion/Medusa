package tv.medusa.app.data.repository

import tv.medusa.app.data.local.db.JellyfinMediaEntity

/**
 * Mezcla de la fila "Continuar viendo": [Resume] (lo que está a medias) + [NextUp] (el siguiente
 * capítulo de cada serie), ordenada por **fecha real** de última reproducción.
 *
 * Reglas:
 * - La fecha de un ítem es la suya propia (`lastPlayedTicks`). Si el servidor no la manda —caso
 *   típico de [NextUp], que son episodios todavía sin reproducir— se hereda de la fecha de su serie,
 *   calculada respetando el orden de recencia que devuelve el servidor para [NextUp].
 * - Para las series en [NextUp], la lista devuelta por Jellyfin (`NextUp[0], NextUp[1], ...`) ya viene
 *   ordenada por el servidor por fecha de reproducción más reciente de cada serie. Las fechas se
 *   ajustan de forma monótona para preservar dicho orden de recencia del servidor sin desordenar por
 *   fechas obsoletas ni degradar ítems sin fecha al final.
 * - En empate de fecha gana [Resume] (el capítulo que dejaste a medias) frente al siguiente capítulo
 *   de la misma serie.
 * - Se desduplica por serie (`seriesId ?: id`): cada serie aparece una sola vez.
 */
internal fun mergeContinueWatching(
    resume: List<JellyfinMediaEntity>,
    nextUp: List<JellyfinMediaEntity>,
    seriesDates: Map<String, Long> = emptyMap()
): List<JellyfinMediaEntity> {
    val ranked = ArrayList<RankedContinueWatchingItem>(resume.size + nextUp.size)
    resume.forEachIndexed { index, item ->
        val date = item.resolvedLastPlayed(seriesDates)
        val entity = if (date > 0L && item.lastPlayedTicks != date) item.copy(lastPlayedTicks = date) else item
        ranked += RankedContinueWatchingItem(entity, date, SOURCE_RESUME, index)
    }
    nextUp.forEachIndexed { index, item ->
        val date = item.resolvedLastPlayed(seriesDates)
        val entity = if (date > 0L && item.lastPlayedTicks != date) item.copy(lastPlayedTicks = date) else item
        ranked += RankedContinueWatchingItem(entity, date, SOURCE_NEXT_UP, index)
    }

    val dated = ranked
        .filter { it.date > 0L }
        .sortedWith(
            compareByDescending<RankedContinueWatchingItem> { it.date }
                .thenBy { it.position }
        )
    val undated = ranked
        .filter { it.date <= 0L }
        .sortedWith(compareBy<RankedContinueWatchingItem> { it.position })

    val seenKeys = HashSet<String>(dated.size + undated.size)
    return (dated + undated)
        .map { it.entity }
        .filter { seenKeys.add(it.seriesId ?: it.id) }
}

/**
 * Versión para guardar en Room de lo que acaba de llegar del servidor: pone en cada ítem su fecha
 * real o, si no la trae, la heredada de su serie. La fecha que ya había se conserva con
 * `JellyfinDao.preserveKnownLastPlayedDates`.
 */
internal fun continueWatchingEntitiesToPersist(
    items: List<JellyfinMediaEntity>,
    seriesDates: Map<String, Long>
): List<JellyfinMediaEntity> = items.map { item ->
    val resolved = item.resolvedLastPlayed(seriesDates)
    if (resolved > 0L && item.lastPlayedTicks != resolved) {
        item.copy(lastPlayedTicks = resolved)
    } else {
        item
    }
}

/**
 * Fechas conocidas por **serie**:
 * 1. Primero se extraen las fechas conocidas para cada serie desde [resume], los propios ítems de [nextUp]
 *    y la base de datos ([knownFromDb]).
 * 2. Jellyfin devuelve [nextUp] (`nextUp[0]`, `nextUp[1]`, ...) ordenado strictly por la recencia de
 *    reproducción de cada serie (la serie vista más recientemente es la primera). Se aplica un ajuste
 *    monótono bidireccional sobre la secuencia de series para garantizar que:
 *    - Ninguna serie en NextUp se quede sin fecha (0).
 *    - El orden de recencia enviado por el servidor prevalezca sobre fechas antiguas o desfasadas almacenadas
 *      previamente en la base de datos local.
 */
internal fun seriesLastPlayedDates(
    resume: List<JellyfinMediaEntity>,
    nextUp: List<JellyfinMediaEntity>,
    knownFromDb: Map<String, Long> = emptyMap(),
    recentlyPlayed: List<JellyfinMediaEntity> = emptyList(),
    nowMs: Long = System.currentTimeMillis(),
    /** false para las fechas que se van a persistir: "ahora" es solo una clave de ordenación. */
    allowSyntheticPriority: Boolean = true
): Map<String, Long> {
    val dates = HashMap<String, Long>()

    // 1. Fechas reales desde el pasado: episodios terminados en Jellyfin
    recentlyPlayed.forEach { item ->
        val seriesId = item.seriesId ?: if (item.type.equals("Series", ignoreCase = true)) item.id else null
        if (seriesId != null && item.lastPlayedTicks > 0L) {
            dates[seriesId] = maxOf(dates[seriesId] ?: 0L, item.lastPlayedTicks)
        }
    }

    // 2. Fechas reales desde el pasado: episodios a medias en Resume
    resume.forEach { item ->
        val seriesId = item.seriesId ?: return@forEach
        if (item.lastPlayedTicks > 0L) {
            dates[seriesId] = maxOf(dates[seriesId] ?: 0L, item.lastPlayedTicks)
        }
    }

    // 3. Fechas reales desde el pasado: guardadas en Room
    nextUp.forEach { item ->
        val seriesId = item.seriesId ?: return@forEach
        if (!dates.containsKey(seriesId)) {
            val known = if (item.lastPlayedTicks > 0L) item.lastPlayedTicks else knownFromDb[seriesId]
            if (known != null && known > 0L) {
                dates[seriesId] = known
            }
        }
    }

    // 4. Si el episodio de NextUp es un CAPÍTULO NUEVO o TEMPORADA NUEVA:
    // (p. ej. Season > 1 y Episode == 1, o fecha de estreno posterior a la última reproducción):
    // Se prioriza al frente para que el usuario no se pierda la nueva temporada ni un capítulo nuevo.
    val maxResumeTime = resume.maxOfOrNull { it.lastPlayedTicks } ?: 0L
    val priorityTopTime = maxOf(nowMs, maxResumeTime + 1000L)

    if (allowSyntheticPriority) {
        nextUp.forEach { item ->
            val seriesId = item.seriesId ?: return@forEach
            val lastPlayed = dates[seriesId] ?: 0L
            if (item.isNewSeasonOrPremiere(lastPlayed, nowMs)) {
                val epPremiere = item.premiereDateMs?.takeIf { it in (lastPlayed + 1)..nowMs }
                val resolvedDate = epPremiere ?: priorityTopTime
                dates[seriesId] = maxOf(dates[seriesId] ?: 0L, resolvedDate)
            }
        }
    }

    // 5. Para series en NextUp que no tienen fecha conocida y no son capítulos nuevos:
    // Se ordenan en el PASADO (por debajo de cualquier reproducción real conocida)
    val seriesList = nextUp.mapNotNull { it.seriesId }.distinct()
    if (seriesList.isEmpty()) return dates

    val minKnownDate = (dates.values + resume.map { it.lastPlayedTicks } + recentlyPlayed.map { it.lastPlayedTicks })
        .filter { it > 0L }
        .minOrNull()
        ?: (nowMs - 30L * 86400_000L)

    var fallbackBase = maxOf(1L, minKnownDate - 1000L)

    for (seriesId in seriesList) {
        if ((dates[seriesId] ?: 0L) <= 0L) {
            dates[seriesId] = fallbackBase
            fallbackBase = maxOf(1L, fallbackBase - 1000L)
        }
    }

    return dates
}

/**
 * Determina si este episodio de NextUp representa una novedad relevante (nueva temporada o
 * capítulo recién emitido/añadido) que deba priorizarse al frente de la fila para no perderla.
 */
internal fun JellyfinMediaEntity.isNewSeasonOrPremiere(lastPlayedDate: Long, nowMs: Long = System.currentTimeMillis()): Boolean {
    if (!type.equals("Episode", ignoreCase = true)) return false

    // 1. Temporada nueva: Season > 1 y Episode == 1 (ej: T2:E1 de una serie terminada antes)
    val isNewSeason = (seasonNumber ?: 1) > 1 && (episodeNumber == null || episodeNumber <= 1)
    if (isNewSeason) return true

    // 2. Episodio emitido o añadido posteriormente a la última reproducción de la serie
    if (premiereDateMs != null && lastPlayedDate > 0L && premiereDateMs > lastPlayedDate) {
        return true
    }

    // 3. Estreno muy reciente (últimos 14 días)
    val fourteenDaysAgo = nowMs - 14L * 86_400_000L
    if (premiereDateMs != null && premiereDateMs > fourteenDaysAgo) {
        return true
    }

    return false
}

/** Fecha real de última reproducción del ítem, o la heredada de su serie, o 0 si no se sabe. */
internal fun JellyfinMediaEntity.resolvedLastPlayed(seriesDates: Map<String, Long>): Long = when {
    lastPlayedTicks > 0L -> lastPlayedTicks
    seriesId != null -> seriesDates[seriesId] ?: 0L
    else -> 0L
}

private const val SOURCE_RESUME = 0
private const val SOURCE_NEXT_UP = 1

private data class RankedContinueWatchingItem(
    val entity: JellyfinMediaEntity,
    val date: Long,
    val source: Int,
    val position: Int
)
