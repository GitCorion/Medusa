package tv.medusa.app.domain.usecase

import tv.medusa.app.data.local.DEFAULT_TMDB_API_KEY
import tv.medusa.app.data.local.DEFAULT_TRAKT_CLIENT_ID
import tv.medusa.app.data.local.UserPreferencesRepository
import tv.medusa.app.data.repository.MediaRepository
import tv.medusa.app.domain.model.HomeSection
import tv.medusa.app.domain.model.MediaItem
import tv.medusa.app.domain.model.MediaSource
import tv.medusa.app.domain.model.SeasonalEvent
import tv.medusa.app.domain.model.SeasonalEventsManager
import tv.medusa.app.data.local.db.JellyfinMediaEntity
import tv.medusa.app.util.toOptimizedMediaItem
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import javax.inject.Inject

data class SeasonalFeedResult(
    val event: SeasonalEvent,
    val sections: List<HomeSection>,
    val featuredItem: MediaItem? = null
)

class GetSeasonalFeedUseCase @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val filterToLibraryUseCase: FilterToLibraryUseCase,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    suspend fun getSeasonalFeed(eventId: String): SeasonalFeedResult? {
        val event = SeasonalEventsManager.ALL_EVENTS.firstOrNull { it.id == eventId }
            ?: SeasonalEventsManager.getActiveEvent()
            ?: return null

        val prefs = userPreferencesRepository.userPreferencesFlow.first()
        val effectiveTmdbKey = prefs.tmdbApiKey.takeIf { it.isNotBlank() } ?: DEFAULT_TMDB_API_KEY
        val effectiveTraktClientId = prefs.traktClientId.takeIf { it.isNotBlank() } ?: DEFAULT_TRAKT_CLIENT_ID

        return coroutineScope {
            // 1. Obtener ítems de listas editoriales de Trakt (públicas / usuarios)
            val traktItemsDeferred = async {
                if (effectiveTraktClientId.isNotBlank() && event.traktCuratedLists.isNotEmpty()) {
                    val listTasks = event.traktCuratedLists.map { (user, listId) ->
                        async {
                            runCatching {
                                mediaRepository.getTraktCustomListItems(
                                    traktToken = prefs.traktAccessToken,
                                    clientId = effectiveTraktClientId,
                                    listId = listId,
                                    username = user
                                ).getOrDefault(emptyList())
                            }.getOrDefault(emptyList())
                        }
                    }
                    listTasks.awaitAll().flatten().distinctBy { it.ids.trakt ?: it.ids.tmdb ?: it.title }
                } else emptyList()
            }

            // 2. Descubrimiento TMDb filtrado estrictamente por géneros y keywords del evento
            val tmdbItemsDeferred = async {
                if (effectiveTmdbKey.isNotBlank()) {
                    val keywordTasks = event.tmdbKeywords.map { kw ->
                        async {
                            runCatching {
                                mediaRepository.getTmdbSeasonalDiscover(
                                    apiKey = effectiveTmdbKey,
                                    withKeywords = kw,
                                    maxPages = 2
                                ).getOrDefault(emptyList())
                            }.getOrDefault(emptyList())
                        }
                    }
                    val tmdbGenreIds = when (event.id) {
                        "halloween" -> listOf("27") // Género numérico de Terror en TMDb
                        "christmas" -> listOf("10751") // Familiar en TMDb
                        "valentines" -> listOf("10749") // Romance en TMDb
                        else -> emptyList()
                    }
                    val genreTasks = tmdbGenreIds.map { gId ->
                        async {
                            runCatching {
                                mediaRepository.getTmdbSeasonalDiscover(
                                    apiKey = effectiveTmdbKey,
                                    withGenres = gId,
                                    maxPages = 2
                                ).getOrDefault(emptyList())
                            }.getOrDefault(emptyList())
                        }
                    }
                    (keywordTasks + genreTasks).awaitAll().flatten().distinctBy { it.id }
                } else emptyList()
            }

            // 3. Escaneo en la biblioteca local con filtro estricto de relevancia temática
            val localSmartDeferred = async {
                val catalog = runCatching { mediaRepository.getAllCatalog() }.getOrDefault(emptyList())
                catalog.filter { isItemRelevantForEvent(it, event) }
            }

            val rawTraktItems = traktItemsDeferred.await()
            val rawTmdbItems = tmdbItemsDeferred.await()
            val localSmartItems = localSmartDeferred.await()

            // Filtrar las listas editoriales de Trakt y TMDb contra la biblioteca local del usuario
            val matchedTraktEntities = if (rawTraktItems.isNotEmpty()) {
                filterToLibraryUseCase.filterTraktItems(
                    traktItems = rawTraktItems,
                    serverUrl = prefs.jellyfinServerUrl,
                    userId = prefs.jellyfinUserId,
                    token = prefs.jellyfinAccessToken,
                    maxCandidates = 100
                ).filter { isItemRelevantForEvent(it, event) }
            } else emptyList()

            val matchedTmdbEntities = if (rawTmdbItems.isNotEmpty()) {
                filterToLibraryUseCase.filterTmdbItems(
                    tmdbItems = rawTmdbItems,
                    serverUrl = prefs.jellyfinServerUrl,
                    userId = prefs.jellyfinUserId,
                    token = prefs.jellyfinAccessToken,
                    maxCandidates = 100
                ).filter { isItemRelevantForEvent(it, event) }
            } else emptyList()

            android.util.Log.d("SeasonalFeed", "== FEED $eventId ==")
            android.util.Log.d("SeasonalFeed", "Trakt raw: ${rawTraktItems.size}, matched local: ${matchedTraktEntities.size}")
            android.util.Log.d("SeasonalFeed", "TMDb raw: ${rawTmdbItems.size}, matched local: ${matchedTmdbEntities.size}")
            android.util.Log.d("SeasonalFeed", "Local smart items: ${localSmartItems.size}")

            val seenIds = mutableSetOf<String>()
            val sections = mutableListOf<HomeSection>()

            // Sección 1: Selección de [Evento] (Listas editoriales de Trakt + coincidencias principales)
            val editorialEntities = (matchedTraktEntities + matchedTmdbEntities).distinctBy { it.id }
            if (editorialEntities.isNotEmpty()) {
                val editorialItems = editorialEntities.map {
                    it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.JELLYFIN)
                }
                seenIds.addAll(editorialItems.map { it.id })
                sections.add(
                    HomeSection(
                        title = event.homeSectionTitle,
                        items = editorialItems
                    )
                )
            }

            // Sección 2: De tu Colección (Resto de contenidos locales coincidentes)
            val remainingLocal = localSmartItems.filter { it.id !in seenIds }
            if (remainingLocal.isNotEmpty()) {
                val sortedLocal = remainingLocal.sortedByDescending { it.communityRating ?: 0f }
                val localItems = sortedLocal.map {
                    it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.JELLYFIN)
                }
                seenIds.addAll(localItems.map { it.id })
                sections.add(
                    HomeSection(
                        title = "De tu Colección",
                        items = localItems
                    )
                )
            }

            // Sección 3: Joyas Ocultas (Películas/series locales bien valoradas no mostradas aún)
            val hiddenGems = catalogFilterGems(localSmartItems, seenIds)
            if (hiddenGems.isNotEmpty()) {
                val hiddenGemItems = hiddenGems.map {
                    it.toMediaItem(prefs.jellyfinServerUrl, prefs.jellyfinAccessToken, MediaSource.JELLYFIN)
                }
                sections.add(
                    HomeSection(
                        title = "Joyas Ocultas",
                        items = hiddenGemItems
                    )
                )
            }

            val featured = sections.firstOrNull()?.items?.firstOrNull()

            SeasonalFeedResult(
                event = event,
                sections = sections,
                featuredItem = featured
            )
        }
    }

    /**
     * Valida estrictamente si un ítem de la biblioteca es temáticamente relevante para el evento.
     * Evita falsos positivos (como comedias dramáticas o documentales no relacionados).
     */
    private fun isItemRelevantForEvent(item: JellyfinMediaEntity, event: SeasonalEvent): Boolean {
        val genresLower = item.genres?.lowercase() ?: ""
        val titleLower = item.title.lowercase()

        return when (event.id) {
            "halloween" -> {
                val isHorrorGenre = genresLower.contains("terror") ||
                                    genresLower.contains("horror") ||
                                    genresLower.contains("slasher")

                val explicitHalloweenKeywords = listOf(
                    "halloween", "noche de brujas", "bruja", "witch", "vampiro", "vampire",
                    "fantasma", "ghost", "zombie", "zombi", "slasher", "exorcista", "exorcism",
                    "posensión", "posesión", "demonio", "demon", "hombre lobo", "werewolf",
                    "casa encantada", "espeluznante", "chucky", "freddy krueger", "jason voorhees",
                    "bitelchús", "beetlejuice", "hocus pocus", "coraline", "pesadilla antes de navidad",
                    "monster house", "la casa de cera", "campamento miasma", "scary movie"
                )

                val hasSpookyKeyword = explicitHalloweenKeywords.any { term ->
                    titleLower.contains(term)
                }

                isHorrorGenre || hasSpookyKeyword
            }
            "christmas" -> {
                val explicitChristmasKeywords = listOf(
                    "navidad", "christmas", "santa", "papá noel", "papa noel", "reyes magos",
                    "navideño", "navideña", "espíritu navideño", "grinch"
                )
                explicitChristmasKeywords.any { term ->
                    titleLower.contains(term) || genresLower.contains(term)
                }
            }
            "valentines" -> {
                val isRomanceGenre = genresLower.contains("romance") ||
                                     genresLower.contains("romántica") ||
                                     genresLower.contains("romantica")

                val explicitValentineKeywords = listOf(
                    "san valentín", "san valentin", "valentín", "valentin"
                )

                isRomanceGenre || explicitValentineKeywords.any { titleLower.contains(it) }
            }
            else -> true
        }
    }

    private fun catalogFilterGems(items: List<JellyfinMediaEntity>, seenIds: Set<String>): List<JellyfinMediaEntity> {
        return items
            .filter { it.id !in seenIds && !it.isPlayed }
            .filter { (it.communityRating ?: 0f) in 7.5f..10.0f }
            .sortedByDescending { it.communityRating ?: 0f }
    }
}

private fun JellyfinMediaEntity.toMediaItem(baseUrl: String, token: String, source: MediaSource): MediaItem =
    toOptimizedMediaItem(baseUrl, token, source)




