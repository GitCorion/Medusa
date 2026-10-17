package tv.medusa.app.data.repository

import tv.medusa.app.data.local.UserPreferencesRepository
import tv.medusa.app.data.local.db.AllowedMediaDao
import tv.medusa.app.data.local.db.AllowedMediaIdEntity
import tv.medusa.app.data.local.db.JellyfinMediaEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import java.io.File

/**
 * Política de acceso al catálogo (restricciones parentales).
 *
 * Mantiene en la tabla SQL `allowed_media_ids` (y en un fichero de `filesDir`
 * como respaldo) el conjunto de IDs permitidos por usuario. Expone comprobaciones
 * SQL directas para evitar mantener en memoria un `Set<String>` con decenas de miles
 * de elementos.
 */
class MediaAccessPolicy(
    private val userPreferencesRepository: UserPreferencesRepository? = null,
    private val allowedMediaDao: AllowedMediaDao? = null
) {
    private var cachedAllowedIds: Set<String>? = null
    private var cachedAllowedUserId: String? = null
    @Volatile
    private var cachedHasRestrictions: Boolean? = null

    /**
     * Devuelve `true` si el usuario actual tiene restricciones parentales activas.
     * Si no hay restricciones, devuelve `false` inmediatamente sin necesidad de
     * cargar listas de IDs en RAM.
     */
    suspend fun hasRestrictions(): Boolean {
        cachedHasRestrictions?.let { return it }

        if (allowedMediaDao != null) {
            val hasInDb = allowedMediaDao.hasRestrictions()
            if (hasInDb) {
                cachedHasRestrictions = true
                return true
            }
            // Primera vez tras migración v14→v15: si la tabla está vacía, importar desde fichero legacy
            val imported = maybeImportLegacyFile()
            if (imported) {
                val hasAfterImport = allowedMediaDao.hasRestrictions()
                cachedHasRestrictions = hasAfterImport
                return hasAfterImport
            }
            cachedHasRestrictions = false
            return false
        }

        // Fallback cuando no hay DAO (tests unitarios sin DB)
        val has = getAllowedIdsSet() != null
        cachedHasRestrictions = has
        return has
    }

    /**
     * Emite si existen restricciones activas de forma reactiva (Room Flow).
     */
    fun hasRestrictionsFlow(): Flow<Boolean> {
        return allowedMediaDao?.hasRestrictionsFlow() ?: flowOf(cachedHasRestrictions ?: (cachedAllowedIds != null))
    }

    /**
     * Comprueba si un ítem específico está permitido.
     */
    suspend fun isAllowed(id: String): Boolean {
        if (!hasRestrictions()) return true
        if (allowedMediaDao != null) {
            return allowedMediaDao.isAllowed(id)
        }
        return getAllowedIdsSet()?.contains(id) ?: true
    }

    suspend fun getAllowedIdsSet(): Set<String>? {
        val userId = userPreferencesRepository?.userPreferencesFlow?.firstOrNull()?.jellyfinUserId ?: return null
        if (userId.isBlank()) return null

        if (cachedAllowedUserId == userId && cachedAllowedIds != null) {
            return cachedAllowedIds
        }

        val ctx = userPreferencesRepository.applicationContext ?: return null
        val file = File(ctx.filesDir, "allowed_ids_$userId.txt")
        if (file.exists()) {
            val lines = file.readLines().filter { it.isNotBlank() }.toSet()
            cachedAllowedIds = lines
            cachedAllowedUserId = userId
            return cachedAllowedIds
        }
        return null
    }

    /**
     * Guarda la lista completa de IDs permitidos tanto en la tabla SQL como en disco.
     */
    suspend fun saveAllowedIds(userId: String, ids: Set<String>) {
        cachedHasRestrictions = ids.isNotEmpty()
        cachedAllowedIds = ids
        cachedAllowedUserId = userId

        allowedMediaDao?.replaceAll(ids.toList())

        val ctx = userPreferencesRepository?.applicationContext ?: return
        val file = File(ctx.filesDir, "allowed_ids_$userId.txt")
        runCatching { file.writeText(ids.joinToString("\n")) }
    }

    /**
     * Añade IDs al conjunto permitido (usado en delta sync) de forma incremental
     * sin cargar la totalidad del catálogo en memoria.
     */
    suspend fun addAllowedIds(userId: String, newIds: Collection<String>) {
        if (newIds.isEmpty()) return
        cachedHasRestrictions = true
        allowedMediaDao?.addAll(newIds.map { AllowedMediaIdEntity(it) })

        val ctx = userPreferencesRepository?.applicationContext ?: return
        val file = File(ctx.filesDir, "allowed_ids_$userId.txt")
        if (file.exists()) {
            runCatching { file.appendText(newIds.joinToString(prefix = "\n", separator = "\n")) }
        }
        cachedAllowedIds = null
    }

    private suspend fun maybeImportLegacyFile(): Boolean {
        val userId = userPreferencesRepository?.userPreferencesFlow?.firstOrNull()?.jellyfinUserId ?: return false
        if (userId.isBlank()) return false
        val ctx = userPreferencesRepository.applicationContext ?: return false
        val file = File(ctx.filesDir, "allowed_ids_$userId.txt")
        if (file.exists()) {
            val lines = file.readLines().filter { it.isNotBlank() }
            if (lines.isNotEmpty()) {
                allowedMediaDao?.replaceAll(lines)
                return true
            }
        }
        return false
    }
}

/**
 * Filtra la lista según las restricciones parentales actuales. Si no hay restricciones
 * configuradas (o el usuario es desconocido) devuelve la lista tal cual.
 */
suspend fun List<JellyfinMediaEntity>.filterAllowed(
    policy: MediaAccessPolicy
): List<JellyfinMediaEntity> {
    if (!policy.hasRestrictions()) return this
    val allowed = policy.getAllowedIdsSet() ?: return this
    return this.filter { allowed.contains(it.id) }
}
