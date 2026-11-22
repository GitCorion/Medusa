package tv.medusa.app.domain.model

/**
 * Serialización compacta de las tarjetas que se guardan en el snapshot del Home.
 *
 * Kotlinx Serialization tardaba varios segundos en decodificar 300 tarjetas en la TV
 * (build debug, CPU A35). Este formato separado por caracteres de control se parsea con
 * `split` y evita reflexión/objetos intermedios: el objetivo es pintar la Home en <100 ms.
 *
 * Formato v2 (los registros antiguos de 19 campos siguen siendo válidos):
 * - campos separados por `\u001F`, tarjetas por `\u001E`
 * - los caracteres de control dentro de textos se sustituyen por espacios al codificar
 */
object MediaItemSnapshotCodec {
    private const val FIELD = '\u001F'
    private const val RECORD = '\u001E'

    fun encode(items: List<MediaItem>): String {
        if (items.isEmpty()) return ""
        val sb = StringBuilder(items.size * 180)
        items.forEachIndexed { index, item ->
            if (index > 0) sb.append(RECORD)
            appendField(sb, item.id)
            appendField(sb, item.title)
            appendField(sb, item.overview)
            appendField(sb, item.type)
            appendField(sb, item.posterUrl)
            appendField(sb, item.backdropUrl)
            appendField(sb, item.logoUrl)
            appendField(sb, item.rating?.toString())
            appendField(sb, item.year?.toString())
            sb.append(item.source.name)
            sb.append(FIELD)
            sb.append(item.playbackPositionTicks)
            sb.append(FIELD)
            appendField(sb, item.rank?.toString())
            sb.append(if (item.isPlayed) '1' else '0')
            sb.append(FIELD)
            sb.append(if (item.isFavorite) '1' else '0')
            sb.append(FIELD)
            appendField(sb, item.totalEpisodes?.toString())
            appendField(sb, item.playedEpisodes?.toString())
            appendField(sb, item.trailerUrl)
            appendField(sb, item.seriesId)
            appendField(sb, item.seriesName)
            appendField(sb, item.premiereDateMs?.toString())
        }
        return sb.toString()
    }

    fun decode(raw: String): List<MediaItem> {
        if (raw.isBlank()) return emptyList()
        val records = raw.split(RECORD)
        val result = ArrayList<MediaItem>(records.size)
        for (record in records) {
            if (record.isEmpty()) continue
            val f = record.split(FIELD)
            if (f.size < 19) continue
            result.add(
                MediaItem(
                    id = f[0],
                    title = f[1],
                    overview = f[2].ifBlank { null },
                    type = f[3].ifBlank { "Movie" },
                    posterUrl = f[4].ifBlank { null },
                    backdropUrl = f[5].ifBlank { null },
                    logoUrl = f[6].ifBlank { null },
                    rating = f[7].toFloatOrNull(),
                    year = f[8].toIntOrNull(),
                    source = runCatching { MediaSource.valueOf(f[9]) }.getOrDefault(MediaSource.JELLYFIN),
                    playbackPositionTicks = f[10].toLongOrNull() ?: 0L,
                    rank = f[11].toIntOrNull(),
                    isPlayed = f[12] == "1",
                    isFavorite = f[13] == "1",
                    totalEpisodes = f[14].toIntOrNull(),
                    playedEpisodes = f[15].toIntOrNull(),
                    trailerUrl = f[16].ifBlank { null },
                    seriesId = f[17].ifBlank { null },
                    seriesName = f[18].ifBlank { null },
                    premiereDateMs = f.getOrNull(19)?.toLongOrNull()
                )
            )
        }
        return result
    }

    private fun appendField(sb: StringBuilder, value: String?) {
        if (value != null) {
            for (char in value) {
                sb.append(if (char.code < 0x20) ' ' else char)
            }
        }
        sb.append(FIELD)
    }
}
