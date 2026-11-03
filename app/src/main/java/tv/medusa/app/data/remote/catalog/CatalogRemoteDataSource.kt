package tv.medusa.app.data.remote.catalog

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import tv.medusa.app.data.remote.ApiConfig
import tv.medusa.app.data.remote.tmdb.TmdbItemDto
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Descarga los catálogos de descubrimiento ya preparados desde Firebase Hosting
 * (`/catalogs/<REGION>/...`), en lugar de pedir miles de páginas a TMDB desde cada dispositivo.
 *
 * Formato compacto de cada fichero: `{ "v": 1, "items": [[id, isTv, popularity, title, year], ...] }`.
 * El índice de región (`index.json`) indica los ficheros disponibles y su versión de contenido.
 */
@Singleton
class CatalogRemoteDataSource @Inject constructor() {

    data class Manifest(
        val version: Int,
        val region: String,
        val updatedAt: Long,
        val platforms: Map<String, String>,
        val trendingDay: String?,
        val popular: String?,
        val netflixTop10: String?
    )

    data class NetflixTop10Entry(
        val rank: Int,
        val tmdbId: Long,
        val title: String?,
        val isTv: Boolean
    )

    data class NetflixTop10(
        val week: String?,
        val films: List<NetflixTop10Entry>,
        val tv: List<NetflixTop10Entry>
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Cliente corto para disparar la generación on-demand sin bloquear el arranque. */
    private val triggerClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val requestedRegions = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    suspend fun fetchManifest(region: String): Manifest? = withContext(Dispatchers.IO) {
        val regionKey = region.uppercase()
        val manifest = runCatching {
            val raw = get("$regionKey/index.json")
            if (raw == null) {
                null
            } else {
                val obj = JSONObject(raw)
                val platformsObj = obj.optJSONObject("platforms") ?: JSONObject()
                val platforms = buildMap {
                    val keys = platformsObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        put(key, platformsObj.optString(key))
                    }
                }
                Manifest(
                    version = obj.optInt("v", 1),
                    region = obj.optString("region", regionKey),
                    updatedAt = obj.optLong("updatedAt", 0L),
                    platforms = platforms,
                    trendingDay = obj.optString("trending_day").ifBlank { null },
                    popular = obj.optString("popular").ifBlank { null },
                    netflixTop10 = obj.optString("netflix_top10").ifBlank { null }
                )
            }
        }.onFailure {
            Log.w("CatalogRemote", "manifest $regionKey: ${it.message}")
        }.getOrNull()

        // Región sin catálogo pre-generado: pedimos al backend que la genere (best effort).
        // Esta sesión sigue con el fallback del proxy; en el siguiente arranque ya descarga
        // el catálogo estático.
        if (manifest == null && requestedRegions.add(regionKey)) {
            requestGeneration(regionKey)
        }
        manifest
    }

    private fun requestGeneration(region: String) {
        runCatching {
            val url = ApiConfig.FIREBASE_OTA_BASE_URL.trimEnd('/') +
                "/api/catalog/request?region=$region"
            Log.d("CatalogRemote", "requestGeneration $url")
            triggerClient.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w("CatalogRemote", "requestGeneration HTTP ${response.code}")
                }
            }
        }
    }

    suspend fun fetchItems(region: String, relativePath: String): List<TmdbItemDto>? =
        withContext(Dispatchers.IO) {
            val regionKey = region.uppercase()
            runCatching {
                val raw = get("$regionKey/$relativePath") ?: return@runCatching null
                val array = JSONObject(raw).optJSONArray("items") ?: return@runCatching emptyList()
                val out = ArrayList<TmdbItemDto>(array.length())
                for (i in 0 until array.length()) {
                    val item = array.optJSONArray(i) ?: continue
                    val id = item.optLong(0)
                    if (id <= 0L) continue
                    val isTv = item.optInt(1) == 1
                    val popularity = item.optDouble(2, 0.0)
                    val title = item.optString(3).ifBlank { null }
                    val year = if (item.isNull(4)) null else item.optInt(4).takeIf { it > 0 }
                    out.add(
                        TmdbItemDto(
                            id = id,
                            title = if (!isTv) title else null,
                            name = if (isTv) title else null,
                            releaseDate = if (!isTv && year != null) "$year-01-01" else null,
                            firstAirDate = if (isTv && year != null) "$year-01-01" else null,
                            popularity = popularity
                        )
                    )
                }
                out
            }.onFailure {
                Log.w("CatalogRemote", "items $regionKey/$relativePath: ${it.message}")
            }.getOrNull()
        }

    suspend fun fetchNetflixTop10(region: String, relativePath: String): NetflixTop10? =
        withContext(Dispatchers.IO) {
            val regionKey = region.uppercase()
            runCatching {
                val raw = get("$regionKey/$relativePath") ?: return@runCatching null
                val obj = JSONObject(raw)

                fun parse(array: org.json.JSONArray?): List<NetflixTop10Entry> {
                    if (array == null) return emptyList()
                    val out = ArrayList<NetflixTop10Entry>(array.length())
                    for (i in 0 until array.length()) {
                        val item = array.optJSONObject(i) ?: continue
                        val tmdbId = item.optLong("tmdbId", 0L)
                        if (tmdbId <= 0L) continue
                        out.add(
                            NetflixTop10Entry(
                                rank = item.optInt("rank", i + 1),
                                tmdbId = tmdbId,
                                title = item.optString("title").ifBlank { null },
                                isTv = item.optString("type").equals("tv", ignoreCase = true)
                            )
                        )
                    }
                    return out
                }

                NetflixTop10(
                    week = obj.optString("week").ifBlank { null },
                    films = parse(obj.optJSONArray("films")),
                    tv = parse(obj.optJSONArray("tv"))
                )
            }.onFailure {
                Log.w("CatalogRemote", "netflix_top10 $regionKey: ${it.message}")
            }.getOrNull()
        }

    private fun get(relativePath: String): String? {
        val url = ApiConfig.FIREBASE_CATALOG_BASE_URL.trimEnd('/') + "/" + relativePath
        Log.d("CatalogRemote", "GET $url")
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w("CatalogRemote", "HTTP ${response.code} $url")
                return null
            }
            return response.body?.string()
        }
    }
}
