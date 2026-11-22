package tv.medusa.app.data.local

import android.content.Context
import tv.medusa.app.domain.model.HomeSection
import tv.medusa.app.domain.model.HomeSectionKind
import tv.medusa.app.domain.model.HomeSectionLayout
import tv.medusa.app.domain.model.MediaItemSnapshotCodec
import tv.medusa.app.util.CryptoUtils
import java.io.File

/**
 * Snapshot del Home en un único fichero de la app.
 *
 * Room tarda ~1,5 s en abrirse/leer en la TV y reconstruir las tarjetas desde IDs añadía
 * varios segundos más. Leer un fichero de ~300 KB y parsearlo con [MediaItemSnapshotCodec]
 * permite pintar la Home en el primer frame sin bloquear con consultas aleatorias.
 *
 * El fichero se cifra con Keystore porque las URLs de arte de Jellyfin pueden contener
 * el token de acceso. Además se asocia a la identidad servidor/usuario: al cambiar de
 * servidor se descarta en lugar de mostrar durante 48 h la biblioteca anterior.
 */
class HomeSnapshotFileStore(context: Context) {
    private val file = File(context.filesDir, "home_snapshot_v1.dat")

    fun read(
        maxSections: Int = Int.MAX_VALUE,
        expectedIdentity: String? = null
    ): List<HomeSection> {
        return runCatching {
            if (!file.exists() || file.length() > MAX_BYTES) return emptyList()
            val raw = CryptoUtils.decryptStored(file.readText())
            if (raw.length < 8) return emptyList()

            val versionEnd = raw.indexOf('\n')
            val timeEnd = raw.indexOf('\n', versionEnd + 1)
            if (versionEnd <= 0 || timeEnd <= versionEnd) return emptyList()
            val version = raw.substring(0, versionEnd)
            if (version != "3" && version != "2" && version != "1") return emptyList()
            val timestamp = raw.substring(versionEnd + 1, timeEnd).toLongOrNull() ?: return emptyList()
            if (System.currentTimeMillis() - timestamp > MAX_AGE_MS) return emptyList()

            val identityEnd = raw.indexOf('\n', timeEnd + 1)
            if (version == "3") {
                if (identityEnd <= timeEnd) return emptyList()
                val identity = raw.substring(timeEnd + 1, identityEnd)
                if (expectedIdentity != null && identity != expectedIdentity) return emptyList()
            }
            val bodyStart = if (version == "3") identityEnd + 1 else timeEnd + 1
            val body = raw.substring(bodyStart)

            body.split('\u001C')
                .asSequence()
                .filter { it.isNotBlank() }
                .take(maxSections)
                .mapNotNull { block ->
                    val minFields = if (version == "2" || version == "3") 6 else 4
                    val fields = block.split('\u001D', limit = minFields)
                    if (fields.size < minFields) return@mapNotNull null
                    val items = MediaItemSnapshotCodec.decode(fields.last())
                    if (items.isEmpty()) return@mapNotNull null
                    val isRanked = fields[2] == "1"
                    val layout = if (version == "2" || version == "3") {
                        runCatching { HomeSectionLayout.valueOf(fields[3]) }
                            .getOrDefault(if (isRanked) HomeSectionLayout.RANKED else HomeSectionLayout.POSTER)
                    } else {
                        if (isRanked) HomeSectionLayout.RANKED else HomeSectionLayout.POSTER
                    }
                    val kind = if (version == "2" || version == "3") {
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

    fun write(sections: List<HomeSection>, identity: String) {
        if (sections.isEmpty()) return
        runCatching {
            file.parentFile?.mkdirs()
            val sb = StringBuilder(64 * 1024)
            sb.append("3\n")
            sb.append(System.currentTimeMillis()).append('\n')
            sb.append(identity).append('\n')
            for (section in sections) {
                if (sb.length > MAX_BYTES) break
                val items = MediaItemSnapshotCodec.encode(section.items)
                if (items.isEmpty()) continue
                sb.append(section.title.replace('\u001D', ' ')).append('\u001D')
                    .append(section.badge.orEmpty().replace('\u001D', ' ')).append('\u001D')
                    .append(if (section.isRanked) '1' else '0').append('\u001D')
                    .append(section.layout.name).append('\u001D')
                    .append(section.kind.name).append('\u001D')
                    .append(items).append('\u001C')
            }
            val encoded = CryptoUtils.encrypt(sb.toString())
            val tmp = File(file.parentFile, "${file.name}.tmp")
            if (tmp.exists()) tmp.delete()
            tmp.writeText(encoded)
            if (!tmp.renameTo(file)) {
                tmp.delete() // Nunca sobrescribir el fichero válido en caliente.
            }
        }
    }

    /** Elimina el snapshot al cambiar de servidor/usuario o cerrar sesión. */
    fun clear() {
        runCatching {
            if (file.exists()) file.delete()
            File(file.parentFile, "${file.name}.tmp").delete()
        }
    }

    private companion object {
        const val MAX_AGE_MS = 48L * 60 * 60 * 1000
        const val MAX_BYTES = 8L * 1024 * 1024
    }
}
