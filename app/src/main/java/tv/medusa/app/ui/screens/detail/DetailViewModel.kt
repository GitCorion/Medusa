package tv.medusa.app.ui.screens.detail

import tv.medusa.app.data.local.ACCENT_CYAN
import tv.medusa.app.data.local.DEFAULT_TMDB_API_KEY
import tv.medusa.app.data.remote.NetworkClientFactory
import tv.medusa.app.data.remote.jellyfin.JellyfinApiService
import tv.medusa.app.data.remote.jellyfin.JellyfinDeviceProfileBuilder
import tv.medusa.app.data.remote.jellyfin.JellyfinMediaSourceDto
import tv.medusa.app.data.remote.jellyfin.JellyfinPlaybackSourceResolver
import tv.medusa.app.data.remote.jellyfin.MedusaVersionDto
import tv.medusa.app.data.remote.ApiConfig
import tv.medusa.app.data.repository.buildJellyfinAuthHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import tv.medusa.app.R
import tv.medusa.app.data.local.UserPreferencesRepository
import tv.medusa.app.data.local.db.JellyfinDao
import tv.medusa.app.data.local.db.JellyfinMediaEntity
import tv.medusa.app.data.repository.MediaRepository
import tv.medusa.app.domain.model.EpisodeItem
import tv.medusa.app.domain.model.MediaItem
import tv.medusa.app.domain.model.MediaSource
import tv.medusa.app.domain.model.SeasonItem
import tv.medusa.app.domain.model.SeriesStatus
import tv.medusa.app.domain.usecase.FilterToLibraryUseCase
import tv.medusa.app.util.JellyfinImageUtils
import tv.medusa.app.util.toOptimizedMediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data class Success(
        val entity: JellyfinMediaEntity,
        val posterUrl: String?,
        val backdropUrl: String?,
        val logoUrl: String? = null,
        val trailerUrl: String? = null,
        val themeSongUrl: String? = null,
        val baseUrl: String? = null,
        val isFavorite: Boolean = false,
        val isPlayed: Boolean = false,
        val buttonStyle: String = "ICONS_ONLY",
        val accentColor: String = ACCENT_CYAN,
        val seriesStatus: SeriesStatus? = null,
        val seasons: List<SeasonItem> = emptyList(),
        val episodes: List<EpisodeItem> = emptyList(),
        val selectedSeasonId: String? = null,
        val nextUpEpisode: EpisodeItem? = null,
        val isLoadingEpisodes: Boolean = false,
        val similarItems: List<MediaItem> = emptyList(),
        val genreItems: List<MediaItem> = emptyList(),
        val genreName: String? = null,
        val collectionItems: List<MediaItem> = emptyList(),
        val isCollection: Boolean = false,
        val playedEpisodes: Int? = null,
        val availableVersions: List<JellyfinMediaSourceDto> = emptyList(),
        val medusaVersions: List<MedusaVersionDto> = emptyList(),
        val selectedVersionId: String? = null,
        val recommendedVersionId: String? = null
    ) : DetailUiState
    data class Error(val message: String) : DetailUiState
}

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val jellyfinDao: JellyfinDao,
    private val mediaRepository: MediaRepository,
    private val filterToLibraryUseCase: FilterToLibraryUseCase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    val isMonochrome: StateFlow<Boolean> = userPreferencesRepository.isMonochrome

    fun loadDetail(itemId: String) {
        viewModelScope.launch {
            try {
                val prefs = userPreferencesRepository.userPreferencesFlow.first()
                val baseUrl = prefs.jellyfinServerUrl
                val token = prefs.jellyfinAccessToken
                val local = jellyfinDao.getItemById(itemId)

                // 1. Si está en BD local, pintar de INMEDIATO (0 ms) sin pantalla de carga
                var initialEntity = local
                if (initialEntity != null) {
                    var backdropUrl = JellyfinImageUtils.getDetailBackdropUrl(
                        baseUrl = baseUrl,
                        itemId = initialEntity.id,
                        imageTag = initialEntity.backdropImageTag,
                        token = token
                    )
                    // Si es episodio, resolver serie y backdrop del padre
                    if (initialEntity.type.equals("Episode", ignoreCase = true) && !initialEntity.seriesId.isNullOrBlank()) {
                        val parentSeries = jellyfinDao.getItemById(initialEntity.seriesId)
                        if (parentSeries != null) {
                            if (initialEntity.seriesName.isNullOrBlank()) {
                                initialEntity = initialEntity.copy(seriesName = parentSeries.title)
                            }
                            if (backdropUrl == null && !parentSeries.backdropImageTag.isNullOrBlank()) {
                                backdropUrl = JellyfinImageUtils.getDetailBackdropUrl(
                                    baseUrl = baseUrl,
                                    itemId = parentSeries.id,
                                    imageTag = parentSeries.backdropImageTag,
                                    token = token
                                )
                            }
                        }
                    }

                    val posterUrl = JellyfinImageUtils.getPosterUrl(
                        baseUrl = baseUrl,
                        itemId = initialEntity.id,
                        imageTag = initialEntity.primaryImageTag,
                        token = token
                    )
                    val logoUrl = JellyfinImageUtils.getLogoUrl(
                        baseUrl = baseUrl,
                        itemId = initialEntity.id,
                        token = token
                    )
                    val isTv = initialEntity.type.equals("Series", ignoreCase = true) || initialEntity.type.equals("Episode", ignoreCase = true)

                    _uiState.value = DetailUiState.Success(
                        entity = initialEntity,
                        posterUrl = posterUrl,
                        backdropUrl = backdropUrl,
                        logoUrl = logoUrl,
                        baseUrl = baseUrl,
                        isFavorite = initialEntity.isFavorite,
                        isPlayed = initialEntity.isEffectiveSeriesPlayed(),
                        buttonStyle = prefs.buttonStyle,
                        accentColor = prefs.accentColor,
                        isLoadingEpisodes = isTv
                    )
                } else {
                    _uiState.value = DetailUiState.Loading
                }

                // 1.5. Si no está por ID directo pero tiene prefijo tmdb-, comprobar si existe localmente
                if (initialEntity == null && itemId.startsWith("tmdb-")) {
                    val tmdbIdStr = itemId.removePrefix("tmdb-")
                    initialEntity = mediaRepository.findLocal(tmdbId = tmdbIdStr, imdbId = null)
                }

                // 2. Resolver entidad: si no estaba en local ni resuelto, obtener de red de Jellyfin
                val entity: JellyfinMediaEntity = if (initialEntity != null) {
                    initialEntity
                } else {
                    // Sin límite, un servidor lento dejaba la ficha en "Cargando…" para siempre.
                    val remoteResult = withTimeoutOrNull(20_000L) {
                        mediaRepository.getItemDetail(
                            serverUrl = baseUrl,
                            userId = prefs.jellyfinUserId,
                            token = token,
                            itemId = itemId
                        )
                    }
                    if (remoteResult == null) {
                        _uiState.value = DetailUiState.Error(context.getString(R.string.detail_load_timeout))
                        return@launch
                    }
                    val remote = remoteResult.getOrNull()
                    remote ?: run {
                        _uiState.value = DetailUiState.Error(context.getString(R.string.detail_not_found))
                        return@launch
                    }
                }

                var finalEntity = entity
                val isTv = finalEntity.type.equals("Series", ignoreCase = true) || finalEntity.type.equals("Episode", ignoreCase = true)
                val tmdbLong = finalEntity.tmdbId?.toLongOrNull()

                // Si no teníamos entidad local previa, pintar Success con la entidad remota ahora
                if (initialEntity == null) {
                    val posterUrl = JellyfinImageUtils.getPosterUrl(
                        baseUrl = baseUrl,
                        itemId = finalEntity.id,
                        imageTag = finalEntity.primaryImageTag,
                        token = token
                    )
                    var backdropUrl = JellyfinImageUtils.getDetailBackdropUrl(
                        baseUrl = baseUrl,
                        itemId = finalEntity.id,
                        imageTag = finalEntity.backdropImageTag,
                        token = token
                    )
                    if (finalEntity.type.equals("Episode", ignoreCase = true) && !finalEntity.seriesId.isNullOrBlank()) {
                        val parentSeries = jellyfinDao.getItemById(finalEntity.seriesId)
                        if (parentSeries != null) {
                            if (finalEntity.seriesName.isNullOrBlank()) {
                                finalEntity = finalEntity.copy(seriesName = parentSeries.title)
                            }
                            if (backdropUrl == null && !parentSeries.backdropImageTag.isNullOrBlank()) {
                                backdropUrl = JellyfinImageUtils.getDetailBackdropUrl(
                                    baseUrl = baseUrl,
                                    itemId = parentSeries.id,
                                    imageTag = parentSeries.backdropImageTag,
                                    token = token
                                )
                            }
                        }
                    }
                    val logoUrl = JellyfinImageUtils.getLogoUrl(
                        baseUrl = baseUrl,
                        itemId = finalEntity.id,
                        token = token
                    )

                    _uiState.value = DetailUiState.Success(
                        entity = finalEntity,
                        posterUrl = posterUrl,
                        backdropUrl = backdropUrl,
                        logoUrl = logoUrl,
                        baseUrl = baseUrl,
                        isFavorite = finalEntity.isFavorite,
                        isPlayed = finalEntity.isEffectiveSeriesPlayed(),
                        buttonStyle = prefs.buttonStyle,
                        accentColor = prefs.accentColor,
                        isLoadingEpisodes = isTv
                    )
                }

                // 2.5 Enriquecer overview en segundo plano SIN BLOQUEAR la UI ni los demás loaders
                launch {
                    try {
                        var enrichedEntity = finalEntity
                        var updated = false

                        // Si falta overview en local, pedir a Jellyfin
                        if (enrichedEntity.overview.isNullOrBlank()) {
                            val remoteDetail = mediaRepository.getItemDetail(
                                serverUrl = baseUrl,
                                userId = prefs.jellyfinUserId,
                                token = token,
                                itemId = itemId
                            ).getOrNull()
                            if (remoteDetail != null && !remoteDetail.overview.isNullOrBlank()) {
                                enrichedEntity = enrichedEntity.copy(
                                    overview = remoteDetail.overview,
                                    genres = if (enrichedEntity.genres.isNullOrBlank()) remoteDetail.genres else enrichedEntity.genres
                                )
                                updated = true
                            }
                        }

                        // Si aún falta overview, intentar con TMDB
                        if (enrichedEntity.overview.isNullOrBlank() && tmdbLong != null && prefs.hasTmdbAccess) {
                            val tmdbOverview: String? = if (isTv) {
                                mediaRepository.getTmdbTvDetail(prefs.tmdbApiKey, tmdbLong)?.overview
                            } else {
                                mediaRepository.getTmdbMovieDetail(prefs.tmdbApiKey, tmdbLong)?.overview
                            }
                            if (!tmdbOverview.isNullOrBlank()) {
                                enrichedEntity = enrichedEntity.copy(overview = tmdbOverview)
                                updated = true
                            }
                        }

                        if (updated) {
                            jellyfinDao.insertOrUpdate(enrichedEntity)
                            (_uiState.value as? DetailUiState.Success)?.let { curr ->
                                _uiState.value = curr.copy(entity = enrichedEntity)
                            }
                        }
                    } catch (_: Exception) {}
                }

                // 3. Cargar en paralelo sin bloquear la pantalla: Trailer, Series/Episodios, Recomendaciones, Colecciones
                // Trailer
                if (tmdbLong != null && prefs.hasTmdbAccess) {
                    launch {
                        val trailer = mediaRepository.getTmdbTrailerUrl(prefs.tmdbApiKey, tmdbLong, isTv = isTv)
                        if (trailer != null) {
                            (_uiState.value as? DetailUiState.Success)?.let { curr ->
                                _uiState.value = curr.copy(trailerUrl = trailer)
                            }
                        }
                    }
                }

                // Tema de audio (Banda sonora de la serie o película)
                if (prefs.themeSongsAudioEnabled && baseUrl.isNotBlank()) {
                    launch {
                        try {
                            val queryItemId = if (finalEntity.type.equals("Episode", ignoreCase = true) || finalEntity.type.equals("Season", ignoreCase = true)) {
                                finalEntity.seriesId ?: finalEntity.id
                            } else {
                                finalEntity.id
                            }
                            val themeSong = mediaRepository.getThemeSongUrl(
                                serverUrl = baseUrl,
                                token = token,
                                itemId = queryItemId,
                                inheritFromParent = true
                            )
                            if (themeSong != null) {
                                (_uiState.value as? DetailUiState.Success)?.let { curr ->
                                    if (curr.entity.id == finalEntity.id) {
                                        _uiState.value = curr.copy(themeSongUrl = themeSong)
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }

                // Series / Temporadas / Episodios
                if (isTv && finalEntity.type.equals("Series", ignoreCase = true)) {
                    launch {
                        val statusDeferred = async {
                            mediaRepository.getSeriesStatus(prefs.tmdbApiKey, tmdbLong, null)
                        }
                        val seasonsDeferred = async {
                            mediaRepository.getSeasons(baseUrl, prefs.jellyfinUserId, token, finalEntity.id)
                        }
                        val nextUpDeferred = async {
                            mediaRepository.getNextUpEpisode(baseUrl, prefs.jellyfinUserId, token, finalEntity.id)
                        }
                        val episodeStatsDeferred = async {
                            mediaRepository.getSeriesEpisodeStats(baseUrl, prefs.jellyfinUserId, token, finalEntity.id)
                        }

                        val seasons = seasonsDeferred.await()
                        val nextUp = nextUpDeferred.await()
                        val selectedSeasonId = if (nextUp != null) {
                            seasons.firstOrNull { it.seasonNumber == nextUp.seasonNumber }?.id
                        } else null ?: (seasons.firstOrNull { it.seasonNumber >= 1 }?.id ?: seasons.firstOrNull()?.id)

                        val episodes = if (selectedSeasonId != null) {
                            mediaRepository.getEpisodes(baseUrl, prefs.jellyfinUserId, token, finalEntity.id, selectedSeasonId)
                        } else emptyList()
                        val nextUpEpisode = nextUp ?: episodes.firstOrNull()
                        val episodeStats = episodeStatsDeferred.await()
                        val seriesStatus = statusDeferred.await()

                        (_uiState.value as? DetailUiState.Success)?.let { curr ->
                            val playedCount = episodeStats?.playedUniqueEpisodes ?: 0
                            val updatedEntity = if (episodeStats != null) {
                                curr.entity.copy(
                                    totalItemCount = episodeStats.totalUniqueEpisodes,
                                    unplayedItemCount = episodeStats.unplayedUniqueEpisodes,
                                    isPlayed = episodeStats.unplayedUniqueEpisodes == 0 && episodeStats.totalUniqueEpisodes > 0
                                )
                            } else curr.entity
                            _uiState.value = curr.copy(
                                entity = updatedEntity,
                                playedEpisodes = playedCount,
                                seriesStatus = seriesStatus,
                                seasons = seasons,
                                episodes = episodes,
                                selectedSeasonId = selectedSeasonId,
                                nextUpEpisode = nextUpEpisode,
                                isLoadingEpisodes = false,
                                isPlayed = updatedEntity.isEffectiveSeriesPlayed()
                            )
                        }
                        nextUpEpisode?.id?.let { targetEpId ->
                            launch {
                                loadPlayableVersions(
                                    targetItemId = targetEpId,
                                    baseUrl = baseUrl,
                                    userId = prefs.jellyfinUserId,
                                    token = token
                                )
                            }
                        }
                    }
                } else if (isTv && finalEntity.type.equals("Episode", ignoreCase = true) && !finalEntity.seriesId.isNullOrBlank()) {
                    launch {
                        loadPlayableVersions(
                            targetItemId = finalEntity.id,
                            baseUrl = baseUrl,
                            userId = prefs.jellyfinUserId,
                            token = token
                        )
                    }
                    launch {
                        val sId = finalEntity.seriesId
                        val seasons = mediaRepository.getSeasons(baseUrl, prefs.jellyfinUserId, token, sId)
                        val epSeasonNum = finalEntity.seasonNumber ?: 1
                        val selectedSeasonId = seasons.firstOrNull { it.seasonNumber == epSeasonNum }?.id ?: seasons.firstOrNull()?.id
                        val episodes = if (selectedSeasonId != null) {
                            mediaRepository.getEpisodes(baseUrl, prefs.jellyfinUserId, token, sId, selectedSeasonId)
                        } else emptyList()
                        (_uiState.value as? DetailUiState.Success)?.let { curr ->
                            _uiState.value = curr.copy(
                                seasons = seasons,
                                episodes = episodes,
                                selectedSeasonId = selectedSeasonId,
                                isLoadingEpisodes = false
                            )
                        }
                    }
                } else {
                    launch {
                        loadPlayableVersions(
                            targetItemId = finalEntity.id,
                            baseUrl = baseUrl,
                            userId = prefs.jellyfinUserId,
                            token = token
                        )
                    }
                }

                // Recomendaciones y género
                launch {
                    try {
                        var similarItems: List<MediaItem> = emptyList()
                        val effectiveTmdbKey = prefs.tmdbApiKey.takeIf { it.isNotBlank() } ?: DEFAULT_TMDB_API_KEY
                        if (tmdbLong != null && prefs.hasTmdbAccess) {
                            val tmdbRecs = mediaRepository.getTmdbRecommendations(effectiveTmdbKey, tmdbLong, isTv).getOrDefault(emptyList())
                            if (tmdbRecs.isNotEmpty()) {
                                val matchedRecs = filterToLibraryUseCase.filterTmdbItems(
                                    tmdbItems = tmdbRecs,
                                    serverUrl = baseUrl,
                                    userId = prefs.jellyfinUserId,
                                    token = token,
                                    maxCandidates = 30
                                ).filter { it.id != finalEntity.id }
                                similarItems = matchedRecs.take(15).map {
                                    it.toMediaItem(baseUrl, token, MediaSource.TMDB_RECOMMENDATION)
                                }
                            }
                        }

                        val firstGenre = finalEntity.genres?.split(",", ";")?.firstOrNull()?.trim()
                        var genreItems: List<MediaItem> = emptyList()
                        if (!firstGenre.isNullOrBlank()) {
                            val sameGenreEntities = (if (isTv) mediaRepository.getSeriesByGenre(firstGenre, 30) else mediaRepository.getMoviesByGenre(firstGenre, 30))
                                .filter { candidate ->
                                    candidate.id != finalEntity.id &&
                                            similarItems.none { s -> s.id == candidate.id } &&
                                            (!candidate.backdropImageTag.isNullOrEmpty() || !candidate.overview.isNullOrBlank())
                                }
                                .take(15)
                            genreItems = sameGenreEntities.map {
                                it.toMediaItem(baseUrl, token, MediaSource.JELLYFIN)
                            }
                        }

                        (_uiState.value as? DetailUiState.Success)?.let { curr ->
                            _uiState.value = curr.copy(
                                similarItems = similarItems,
                                genreItems = genreItems,
                                genreName = firstGenre
                            )
                        }
                    } catch (_: Exception) {}
                }

                // Colecciones / BoxSets
                val isCollection = finalEntity.type.equals("BoxSet", ignoreCase = true) ||
                        finalEntity.type.equals("CollectionFolder", ignoreCase = true) ||
                        finalEntity.type.equals("Playlist", ignoreCase = true) ||
                        finalEntity.title.contains("Colección", ignoreCase = true) ||
                        finalEntity.title.contains("Collection", ignoreCase = true)

                if (isCollection) {
                    launch {
                        var colEntities = mediaRepository.getCollectionItems(baseUrl, prefs.jellyfinUserId, token, finalEntity.id)
                        if (colEntities.isEmpty()) {
                            val cleanName = finalEntity.title
                                .replace(" - Colección", "", ignoreCase = true)
                                .replace(" Colección", "", ignoreCase = true)
                                .replace(" Collection", "", ignoreCase = true)
                                .trim()
                            if (cleanName.isNotBlank()) {
                                colEntities = mediaRepository.searchLocalMedia(cleanName, limit = 20)
                                    .filter { it.id != finalEntity.id && !it.type.equals("BoxSet", ignoreCase = true) }
                            }
                        }
                        val collectionItems = colEntities.map { it.toMediaItem(baseUrl, token, MediaSource.JELLYFIN) }
                        (_uiState.value as? DetailUiState.Success)?.let { curr ->
                            _uiState.value = curr.copy(
                                collectionItems = collectionItems,
                                isCollection = true
                            )
                        }
                    }
                }

            } catch (e: Exception) {
                if (_uiState.value !is DetailUiState.Success) {
                    _uiState.value = DetailUiState.Error(e.localizedMessage ?: context.getString(R.string.detail_error_loading))
                }
            }
        }
    }

    fun selectVersion(mediaSourceId: String) {
        val curr = _uiState.value as? DetailUiState.Success ?: return
        if (curr.availableVersions.none { it.id == mediaSourceId }) return
        _uiState.value = curr.copy(selectedVersionId = mediaSourceId)
    }

    private suspend fun loadPlayableVersions(
        targetItemId: String,
        baseUrl: String,
        userId: String,
        token: String
    ) {
        if (targetItemId.isBlank() || baseUrl.isBlank() || userId.isBlank()) return
        runCatching {
            val api = NetworkClientFactory.createJellyfinService(baseUrl, JellyfinApiService::class.java)
            val authHeader = buildJellyfinAuthHeader(token = token)
            val detail = api.getItemDetail(authHeader = authHeader, userId = userId, itemId = targetItemId)
            val sources = detail.mediaSources
            if (sources.isEmpty()) return@runCatching

            val supports4K = withContext(Dispatchers.Default) { JellyfinDeviceProfileBuilder.supportsDevice4kHevc() }
            val supportsDv = withContext(Dispatchers.Default) { JellyfinDeviceProfileBuilder.supportsDeviceDolbyVision() }
            val maxWidth = if (supports4K) 3840 else 1920

            val medusaStatus = runCatching {
                val caps = api.getMedusaCapabilities().body()
                if (caps?.isSupported == true) {
                    api.getMedusaStatus(
                        authHeader = authHeader,
                        itemId = targetItemId,
                        maxWidth = maxWidth,
                        supports4k = supports4K,
                        supportsDv = supportsDv
                    ).body()
                } else null
            }.getOrNull()

            val addonRecommendedId = medusaStatus?.recommendedMediaSourceId
                ?.takeIf { recId -> sources.any { it.id == recId } }
            val recommendedId = addonRecommendedId
                ?: JellyfinPlaybackSourceResolver.selectPreferredMediaSource(
                    mediaSources = sources,
                    preferredMediaSourceId = null,
                    supports4K = supports4K,
                    supportsDolbyVision = supportsDv
                )?.id

            (_uiState.value as? DetailUiState.Success)?.let { curr ->
                val keptSelection = curr.selectedVersionId?.takeIf { sel -> sources.any { it.id == sel } }
                _uiState.value = curr.copy(
                    availableVersions = sources,
                    medusaVersions = medusaStatus?.versions ?: emptyList(),
                    recommendedVersionId = recommendedId,
                    selectedVersionId = keptSelection ?: recommendedId
                )
            }
        }
    }

    fun selectSeason(seasonId: String) {
        val state = _uiState.value as? DetailUiState.Success ?: return
        if (state.selectedSeasonId == seasonId) return

        _uiState.value = state.copy(selectedSeasonId = seasonId, isLoadingEpisodes = true)
        viewModelScope.launch {
            try {
                val prefs = userPreferencesRepository.userPreferencesFlow.first()
                val episodes = mediaRepository.getEpisodes(
                    serverUrl = prefs.jellyfinServerUrl,
                    userId = prefs.jellyfinUserId,
                    token = prefs.jellyfinAccessToken,
                    seriesId = state.entity.id,
                    seasonId = seasonId
                )
                _uiState.value = (_uiState.value as? DetailUiState.Success)?.copy(
                    episodes = episodes,
                    isLoadingEpisodes = false
                ) ?: _uiState.value
            } catch (e: Exception) {
                _uiState.value = (_uiState.value as? DetailUiState.Success)?.copy(isLoadingEpisodes = false) ?: _uiState.value
            }
        }
    }

    fun toggleFavorite() {
        val state = _uiState.value as? DetailUiState.Success ?: return
        val newFavStatus = !state.isFavorite
        _uiState.value = state.copy(isFavorite = newFavStatus)

        viewModelScope.launch {
            try {
                val prefs = userPreferencesRepository.userPreferencesFlow.first()
                mediaRepository.toggleFavorite(
                    serverUrl = prefs.jellyfinServerUrl,
                    userId = prefs.jellyfinUserId,
                    token = prefs.jellyfinAccessToken,
                    itemId = state.entity.id,
                    makeFavorite = newFavStatus
                )
                if (state.entity.type.equals("Episode", ignoreCase = true) && !state.entity.seriesId.isNullOrBlank()) {
                    mediaRepository.toggleFavorite(
                        serverUrl = prefs.jellyfinServerUrl,
                        userId = prefs.jellyfinUserId,
                        token = prefs.jellyfinAccessToken,
                        itemId = state.entity.seriesId!!,
                        makeFavorite = newFavStatus
                    )
                }
            } catch (e: Exception) {
                // Revert on error
                _uiState.value = state.copy(isFavorite = !newFavStatus)
            }
        }
    }

    fun togglePlayed() {
        val state = _uiState.value as? DetailUiState.Success ?: return
        val newPlayedStatus = !state.isPlayed
        val updatedEntity = state.entity.copy(
            isPlayed = newPlayedStatus,
            playbackPositionTicks = 0L,
            unplayedItemCount = if (newPlayedStatus) 0 else state.entity.totalItemCount
        )
        _uiState.value = state.copy(
            isPlayed = newPlayedStatus,
            entity = updatedEntity
        )

        viewModelScope.launch {
            try {
                val prefs = userPreferencesRepository.userPreferencesFlow.first()
                mediaRepository.togglePlayed(
                    serverUrl = prefs.jellyfinServerUrl,
                    userId = prefs.jellyfinUserId,
                    token = prefs.jellyfinAccessToken,
                    itemId = state.entity.id,
                    makePlayed = newPlayedStatus
                )
            } catch (e: Exception) {
                // Revert on error
                _uiState.value = state.copy(
                    isPlayed = !newPlayedStatus,
                    entity = state.entity
                )
            }
        }
    }

    private fun JellyfinMediaEntity.toMediaItem(baseUrl: String, token: String, source: MediaSource): MediaItem {
        return toOptimizedMediaItem(baseUrl, token, source)
    }

    private fun JellyfinMediaEntity.isEffectiveSeriesPlayed(): Boolean {
        if (type.equals("Series", ignoreCase = true) && totalItemCount != null && totalItemCount <= 0) {
            return false
        }
        return isPlayed || (unplayedItemCount == 0 && (totalItemCount ?: 0) > 0)
    }
}
