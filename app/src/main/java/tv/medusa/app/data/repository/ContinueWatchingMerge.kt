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
    recentlyPlayed: List<JellyfinMediaEntity> = emptyList()
): Map<String, Long> {
    val dates = HashMap<String, Long>()

    // 1. Fechas reales desde los ítems recientemente reproducidos en Jellyfin (episodios terminados)
    recentlyPlayed.forEach { item ->
        val seriesId = item.seriesId ?: if (item.type.equals("Series", ignoreCase = true)) item.id else null
        if (seriesId != null && item.lastPlayedTicks > 0L) {
            dates[seriesId] = maxOf(dates[seriesId] ?: 0L, item.lastPlayedTicks)
        }
    }

    // 2. Fechas desde el Resume (lo que el usuario dejó a medias en el servidor)
    resume.forEach { item ->
        val seriesId = item.seriesId ?: return@forEach
        if (item.lastPlayedTicks > 0L) {
            dates[seriesId] = maxOf(dates[seriesId] ?: 0L, item.lastPlayedTicks)
        }
    }

    // 3. Fechas desde NextUp e historia en Room
    nextUp.forEach { item ->
        val seriesId = item.seriesId ?: return@forEach
        if (!dates.containsKey(seriesId)) {
            val known = if (item.lastPlayedTicks > 0L) item.lastPlayedTicks else knownFromDb[seriesId]
            if (known != null && known > 0L) {
                dates[seriesId] = known
            }
        }
    }

    // Lista de series de NextUp en el orden exacto de recencia del servidor
    val seriesList = nextUp.mapNotNull { it.seriesId }.distinct()
    if (seriesList.isEmpty()) return dates

    val maxResume = resume.maxOfOrNull { it.lastPlayedTicks } ?: 0L
    val maxRecent = recentlyPlayed.maxOfOrNull { it.lastPlayedTicks } ?: 0L
    val maxKnown = dates.values.maxOrNull() ?: 0L
    val baseTime = listOf(maxResume, maxRecent, maxKnown).filter { it > 0L }.maxOrNull()
        ?: (System.currentTimeMillis() - 3600_000L)

    // Si la primera serie de NextUp (la más reciente del servidor) no tiene fecha, asignarle baseTime
    val topSeries = seriesList[0]
    if ((dates[topSeries] ?: 0L) <= 0L) {
        dates[topSeries] = baseTime
    }

    // Paso 1: Pasada hacia atrás. Si la serie N tiene fecha de reproducción más reciente que la serie N-1,
    // significa que la serie N-1 (que el servidor colocó por delante) se vio aún más recientemente
    // (p. ej. vista en otro dispositivo sin haber guardado todavía la fecha en Room).
    for (i in seriesList.size - 1 downTo 1) {
        val currentSeries = seriesList[i]
        val prevSeries = seriesList[i - 1]
        val currentDate = dates[currentSeries] ?: 0L
        val prevDate = dates[prevSeries] ?: 0L

        if (currentDate > 0L && prevDate <= currentDate) {
            dates[prevSeries] = currentDate + 1000L
        }
    }

    // Paso 2: Pasada hacia adelante. Garantizar que cada serie siguiente en NextUp sea monótonamente
    // menor a la serie anterior (respetando la precedencia de recencia del servidor) y que ninguna
    // serie en NextUp quede con fecha 0.
    for (i in 0 until seriesList.size - 1) {
        val currentSeries = seriesList[i]
        val nextSeries = seriesList[i + 1]
        val currentDate = dates[currentSeries] ?: baseTime
        val nextDate = dates[nextSeries] ?: 0L

        if (nextDate <= 0L || nextDate >= currentDate) {
            dates[nextSeries] = maxOf(1L, currentDate - 1000L)
        }
    }

    return dates
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
