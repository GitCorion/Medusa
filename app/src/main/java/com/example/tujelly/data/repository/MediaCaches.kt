package com.example.tujelly.data.repository

import com.example.tujelly.data.local.db.JellyfinMediaEntity
import com.example.tujelly.data.remote.tmdb.TmdbItemDto
import java.util.concurrent.ConcurrentHashMap

/**
 * Caché en memoria genérica con TTL por defecto y cota de tamaño.
 *
 * - Al insertar por encima de [maxSize] se purgan primero las entradas caducadas y, si aún se
 *   supera el límite, se desaloja la más antigua.
 * - [timeSource] permite inyectar un reloj en tests.
 *
 * Ver §2.7 de MEMORIA_ANALISIS_RENDIMIENTO.md.
 */
class TtlCache<K : Any, V : Any>(
    private val defaultTtlMillis: Long,
    private val maxSize: Int = 512,
    private val timeSource: () -> Long = { System.currentTimeMillis() }
) {
    private data class CacheEntry<V>(val data: V, val timestamp: Long)
    private val map = ConcurrentHashMap<K, CacheEntry<V>>()

    val size: Int get() = map.size

    fun get(key: K): V? {
        val entry = map[key] ?: return null
        if (timeSource() - entry.timestamp > defaultTtlMillis) {
            map.remove(key)
            return null
        }
        return entry.data
    }

    fun put(key: K, data: V) {
        if (map.size >= maxSize && !map.containsKey(key)) {
            purgeExpired()
            if (map.size >= maxSize) {
                map.minByOrNull { it.value.timestamp }?.let { map.remove(it.key) }
            }
        }
        map[key] = CacheEntry(data, timeSource())
    }

    fun remove(key: K) {
        map.remove(key)
    }

    fun clear() {
        map.clear()
    }

    /** Elimina todas las entradas caducadas. */
    fun purgeExpired() {
        val now = timeSource()
        map.entries.removeIf { now - it.value.timestamp > defaultTtlMillis }
    }
}

/**
 * Cachés de resultados remotos compartidas por los repositorios del catálogo.
 *
 * Antes vivían en el `companion object` de `MediaRepository`; se extraen aquí para que
 * cada repositorio especializado (TMDB, Jellyfin remoto) use solo las suyas sin arrastrar
 * la clase completa. El estado sigue siendo global (equivalente al diseño anterior).
 */
object MediaCaches {
    private const val WEEK_MS = 7 * 24 * 3600 * 1000L

    // TMDB
    val tmdbTopRatedCache = TtlCache<String, List<TmdbItemDto>>(WEEK_MS)
    val tmdbTrendingDayCache = TtlCache<String, List<TmdbItemDto>>(WEEK_MS)
    val tmdbTrendingCache = TtlCache<String, List<TmdbItemDto>>(WEEK_MS)
    val tmdbRecommendationsCache = TtlCache<String, List<TmdbItemDto>>(WEEK_MS)
    val tmdbGenreCache = TtlCache<String, List<TmdbItemDto>>(WEEK_MS)
    val tmdbProviderCache = TtlCache<String, List<TmdbItemDto>>(WEEK_MS)

    // Jellyfin remoto
    val serverTopMoviesCache = TtlCache<String, List<JellyfinMediaEntity>>(15 * 60 * 1000L)
    val serverTopSeriesCache = TtlCache<String, List<JellyfinMediaEntity>>(15 * 60 * 1000L)
    val serverContinueWatchingCache = TtlCache<String, List<JellyfinMediaEntity>>(3 * 60 * 1000L)
    val serverLatestCache = TtlCache<String, List<JellyfinMediaEntity>>(10 * 60 * 1000L)
    val serverRecentlyPlayedCache = TtlCache<String, List<JellyfinMediaEntity>>(10 * 60 * 1000L)

    fun invalidateServerCaches() {
        serverContinueWatchingCache.clear()
        serverRecentlyPlayedCache.clear()
        serverLatestCache.clear()
        serverTopMoviesCache.clear()
        serverTopSeriesCache.clear()
    }
}
