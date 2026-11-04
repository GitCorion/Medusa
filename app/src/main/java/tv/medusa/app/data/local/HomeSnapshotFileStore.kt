package tv.medusa.app.data.local

import android.content.Context
import tv.medusa.app.domain.model.HomeSection
import tv.medusa.app.domain.model.HomeSectionKind
import tv.medusa.app.domain.model.HomeSectionLayout
import tv.medusa.app.domain.model.MediaItemSnapshotCodec
import java.io.File

/**
 * Snapshot del Home en un único fichero de la app.
 *
 * Room tarda ~1,5 s en abrirse/leer en la TV y reconstruir las tarjetas desde IDs añadía
 * varios segundos más. Leer un fichero de ~300 KB y parsearlo con [MediaItemSnapshotCodec]
 * permite pintar la Home en el primer frame sin bloquear con consultas aleatorias.
 *
 * El fichero es una caché: si falta, está corrupto o supera [MAX_AGE_MS], se ignora y se
 * reconstruye desde Room/red. Nunca es la fuente de verdad.
 */
class HomeSnapshotFileStore(context: Context) {
    private val file = File(context.filesDir, "home_snapshot_v1.dat")

    fun read(maxSections: Int = Int.MAX_VALUE): List<HomeSection> {
        return runCatching {
            if (!file.exists()) return emptyList()
            val raw = file.readText()
            if (raw.length < 8) return emptyList()

            val versionEnd = raw.indexOf('\n')
            val timeEnd = raw.indexOf('\n', versionEnd + 1)
            if (versionEnd <= 0 || timeEnd <= versionEnd) return emptyList()
            val version = raw.substring(0, versionEnd)
            if (version != "1" && version != "2") return emptyList()
            val timestamp = raw.substring(versionEnd + 1, timeEnd).toLongOrNull() ?: return emptyList()
            if (System.currentTimeMillis() - timestamp > MAX_AGE_MS) return emptyList()

            val body = raw.substring(timeEnd + 1)
            body.split('\u001C')
                .asSequence()
                .take(maxSections)
                .filter { it.isNotBlank() }
                .mapNotNull { block ->
                    val minFields = if (version == "2") 6 else 4
                    val fields = block.split('\u001D', limit = minFields)
                    if (fields.size < minFields) return@mapNotNull null
                    val items = MediaItemSnapshotCodec.decode(fields.last())
                    if (items.isEmpty()) return@mapNotNull null
                    val isRanked = fields[2] == "1"
                    val layout = if (version == "2") {
                        runCatching { HomeSectionLayout.valueOf(fields[3]) }
                            .getOrDefault(if (isRanked) HomeSectionLayout.RANKED else HomeSectionLayout.POSTER)
                    } else {
                        if (isRanked) HomeSectionLayout.RANKED else HomeSectionLayout.POSTER
                    }
                    val kind = if (version == "2") {
                        runCatching { HomeSectionKind.valueOf(fields[4]) }
                            .getOrDefault(HomeSectionKind.CUSTOM)
                    } else {
                        HomeSectionKind.CUSTOM
                    }
                    HomeSection(
                        title = fields[0],
                        items = items,
                        badge = fields[1].ifBlank { null },
                        isRanked = isRanked,
                        kind = kind,
                        layout = layout
                    )
                }
                .toList()
        }.getOrDefault(emptyList())
    }

    fun write(sections: List<HomeSection>) {
        if (sections.isEmpty()) return
        runCatching {
            file.parentFile?.mkdirs()
            val sb = StringBuilder(64 * 1024)
            sb.append("2\n")
            sb.append(System.currentTimeMillis()).append('\n')
            for (section in sections) {
                val items = MediaItemSnapshotCodec.encode(section.items)
                if (items.isEmpty()) continue
                sb.append(section.title).append('\u001D')
                    .append(section.badge.orEmpty()).append('\u001D')
                    .append(if (section.isRanked) '1' else '0').append('\u001D')
                    .append(section.layout.name).append('\u001D')
                    .append(section.kind.name).append('\u001D')
                    .append(items).append('\u001C')
            }
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(sb.toString())
            if (!tmp.renameTo(file)) {
                file.writeText(sb.toString())
                tmp.delete()
            }
        }
    }

    private companion object {
        const val MAX_AGE_MS = 48L * 60 * 60 * 1000
    }
}
