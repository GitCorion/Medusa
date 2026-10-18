package tv.medusa.app.data.repository

import tv.medusa.app.data.local.db.JellyfinMediaEntity

/**
 * Mezcla de la fila "Continuar viendo": [Resume] (lo que está a medias) + [NextUp] (el siguiente
 * capítulo de cada serie), ordenada por **fecha real** de última reproducción.
 *
 * Reglas:
 * - La fecha de un ítem es la suya propia (`lastPlayedTicks`). Si el servidor no la manda —caso
 *   típico de [NextUp], que son episodios todavía sin reproducir— se **hereda la de su serie**
 *   desde [seriesDates]. Nunca se inventa una fecha "ahora": eso hacía que todos los ítems sin
 *   fecha se colocaran por delante de los que sí la tienen.
 * - Los ítems sin ninguna fecha conocida conservan el orden que devolvió el servidor y quedan
 *   **por debajo** de los que sí la tienen.
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
        ranked += RankedContinueWatchingItem(item, item.resolvedLastPlayed(seriesDates), SOURCE_RESUME, index)
    }
    nextUp.forEachIndexed { index, item ->
        ranked += RankedContinueWatchingItem(item, item.resolvedLastPlayed(seriesDates), SOURCE_NEXT_UP, index)
    }

    val dated = ranked
        .filter { it.date > 0L }
        .sortedWith(
            compareByDescending<RankedContinueWatchingItem> { it.date }
                .thenBy { it.source }
                .thenBy { it.position }
        )
    val undated = ranked
        .filter { it.date <= 0L }
        .sortedWith(compareBy<RankedContinueWatchingItem> { it.source }.thenBy { it.position })

    val seenKeys = HashSet<String>(dated.size + undated.size)
    return (dated + undated)
        .map { it.entity }
        .filter { seenKeys.add(it.seriesId ?: it.id) }
}

/**
 * Versión para guardar en Room de lo que acaba de llegar del servidor: pone en cada ítem su fecha
 * real o, si no la trae, la heredada de su serie. Los que no tienen ninguna se dejan tal cual (0),
 * para no escribir una fecha inventada. La fecha que ya había se conserva con
 * `JellyfinDao.preserveKnownLastPlayedDates`.
 */
internal fun continueWatchingEntitiesToPersist(
    items: List<JellyfinMediaEntity>,
    seriesDates: Map<String, Long>
): List<JellyfinMediaEntity> = items.map { item ->
    if (item.lastPlayedTicks > 0L) {
        item
    } else {
        val inherited = item.resolvedLastPlayed(seriesDates)
        if (inherited > 0L) item.copy(lastPlayedTicks = inherited) else item
    }
}

/**
 * Fechas conocidas por **serie**: primero las que traen los propios ítems de [resume] (lo más
 * fiable: es la fecha del capítulo que dejaste a medias), y para las series que solo aparecen en
 * NextUp la de su fila ya guardada en Room ([knownFromDb]).
 */
internal fun seriesLastPlayedDates(
    resume: List<JellyfinMediaEntity>,
    nextUp: List<JellyfinMediaEntity>,
    knownFromDb: Map<String, Long> = emptyMap()
): Map<String, Long> {
    val dates = HashMap<String, Long>()
    resume.forEach { item ->
        val seriesId = item.seriesId ?: return@forEach
        if (item.lastPlayedTicks > 0L) {
            dates[seriesId] = maxOf(dates[seriesId] ?: 0L, item.lastPlayedTicks)
        }
    }
    nextUp.forEach { item ->
        val seriesId = item.seriesId ?: return@forEach
        if (!dates.containsKey(seriesId)) {
            knownFromDb[seriesId]?.takeIf { it > 0L }?.let { dates[seriesId] = it }
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
