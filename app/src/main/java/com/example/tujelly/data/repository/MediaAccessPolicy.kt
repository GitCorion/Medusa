package com.example.tujelly.data.repository

import com.example.tujelly.data.local.UserPreferencesRepository
import com.example.tujelly.data.local.db.JellyfinMediaEntity
import kotlinx.coroutines.flow.firstOrNull
import java.io.File

/**
 * Política de acceso al catálogo (restricciones parentales).
 *
 * Mantiene en un fichero de `filesDir` el conjunto de IDs permitidos por usuario
 * (`allowed_ids_<userId>.txt`) y expone el filtrado resultante al resto de repositorios.
 *
 * Extraído de `MediaRepository` (antes `getAllowedIdsSet` / `saveAllowedIds` / `filterAllowed`).
 */
class MediaAccessPolicy(
    private val userPreferencesRepository: UserPreferencesRepository? = null
) {
    private var cachedAllowedIds: Set<String>? = null
    private var cachedAllowedUserId: String? = null

    suspend fun getAllowedIdsSet(): Set<String>? {
        val userId = userPreferencesRepository?.userPreferencesFlow?.firstOrNull()?.jellyfinUserId ?: return null
        if (userId.isBlank()) return null

        if (cachedAllowedUserId == userId && cachedAllowedIds != null) {
            return cachedAllowedIds
        }

        val ctx = userPreferencesRepository?.applicationContext ?: return null
        val file = File(ctx.filesDir, "allowed_ids_$userId.txt")
        if (file.exists()) {
            val lines = file.readLines().toSet()
            cachedAllowedIds = lines
            cachedAllowedUserId = userId
            return cachedAllowedIds
        }
        return null
    }

    fun saveAllowedIds(userId: String, ids: Set<String>) {
        val ctx = userPreferencesRepository?.applicationContext ?: return
        val file = File(ctx.filesDir, "allowed_ids_$userId.txt")
        file.writeText(ids.joinToString("\n"))
        cachedAllowedIds = ids
        cachedAllowedUserId = userId
    }
}

/**
 * Filtra la lista según las restricciones parentales actuales. Si no hay restricciones
 * configuradas (o el usuario es desconocido) devuelve la lista tal cual.
 */
suspend fun List<JellyfinMediaEntity>.filterAllowed(
    policy: MediaAccessPolicy
): List<JellyfinMediaEntity> {
    val allowed = policy.getAllowedIdsSet() ?: return this
    return this.filter { allowed.contains(it.id) }
}
