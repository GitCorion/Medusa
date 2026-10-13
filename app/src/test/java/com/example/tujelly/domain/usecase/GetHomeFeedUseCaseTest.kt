package com.example.tujelly.domain.usecase

import com.example.tujelly.data.local.UserPreferences
import com.example.tujelly.data.local.db.JellyfinMediaEntity
import com.example.tujelly.data.repository.MediaRepository
import com.example.tujelly.domain.model.HomeSection
import com.example.tujelly.domain.model.MediaItem
import com.example.tujelly.domain.model.MediaSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GetHomeFeedUseCaseTest {

    private lateinit var fakeDao: FakeJellyfinDao
    private lateinit var repository: MediaRepository
    private lateinit var filterToLibraryUseCase: FilterToLibraryUseCase
    private lateinit var useCase: GetHomeFeedUseCase

    private val validPrefs = UserPreferences(
        jellyfinServerUrl = "http://192.168.1.50:8096",
        jellyfinUserId = "user-abc",
        jellyfinAccessToken = "token-xyz"
    )

    @Before
    fun setUp() {
        GetHomeFeedUseCase.invalidateFeedCache()
        fakeDao = FakeJellyfinDao()
        repository = MediaRepository(fakeDao)
        filterToLibraryUseCase = FilterToLibraryUseCase(repository)
        useCase = GetHomeFeedUseCase(
            mediaRepository = repository,
            filterToLibraryUseCase = filterToLibraryUseCase
        )
    }

    @Test
    fun `emits empty list when user preferences are blank`() = runTest {
        val blankPrefs = UserPreferences(
            jellyfinServerUrl = "",
            jellyfinUserId = "",
            jellyfinAccessToken = ""
        )

        val result = useCase(blankPrefs).first()
        assertTrue("Debe emitir lista vacía cuando no hay credenciales", result.isEmpty())
    }

    @Test
    fun `emits cached feed immediately when cache is warm`() = runTest {
        // Establecer un estado de caché previo llamando al flujo con datos y alimentándolo
        val cachedSection = HomeSection(
            title = "Continuar Viendo",
            items = listOf(
                MediaItem(id = "1", title = "Pelicula A", source = MediaSource.JELLYFIN),
                MediaItem(id = "2", title = "Pelicula B", source = MediaSource.JELLYFIN)
            )
        )
        // Poblamos la base de datos con al menos 10 items para no activar quickInit
        val sampleItems = (1..12).map { index ->
            JellyfinMediaEntity(
                id = "item-$index",
                title = "Movie $index",
                type = "Movie",
                productionYear = 2024,
                communityRating = 8.5f
            )
        }
        fakeDao.setLocalItems(sampleItems)

        val initialEmission = useCase(validPrefs).first()
        assertNotNull(initialEmission)
        assertTrue(initialEmission.isNotEmpty())

        // La segunda llamada con forceRefresh = false debe devolver inmediatamente el feed
        val secondEmission = useCase(validPrefs, forceRefresh = false).first()
        assertEquals(initialEmission.size, secondEmission.size)
        assertEquals(initialEmission.first().title, secondEmission.first().title)
    }

    @Test
    fun `channelFlow emits local sections progressively without throwing Flow invariant exceptions`() = runTest {
        val movies = (1..15).map { index ->
            JellyfinMediaEntity(
                id = "movie-$index",
                title = "Película Estelar $index",
                type = "Movie",
                productionYear = 2023,
                communityRating = 8.0f + (index % 2)
            )
        }
        val series = (1..10).map { index ->
            JellyfinMediaEntity(
                id = "series-$index",
                title = "Serie Notable $index",
                type = "Series",
                productionYear = 2022,
                communityRating = 8.2f
            )
        }
        val continueWatching = listOf(
            JellyfinMediaEntity(
                id = "cw-1",
                title = "Capítulo en Curso",
                type = "Episode",
                seriesId = "series-1",
                seriesName = "Serie Notable 1",
                playbackPositionTicks = 500000000L,
                isPlayed = false
            )
        )

        fakeDao.setLocalItems(movies + series + continueWatching)

        // Obtenemos la primera emisión local del channelFlow
        val emittedSections = useCase(validPrefs).first()

        assertFalse("El feed generado no debe estar vacío", emittedSections.isEmpty())

        val titles = emittedSections.map { it.title }
        assertTrue(
            "Debe incluir Continuar Viendo o Grandes Producciones o Series",
            titles.contains("Continuar Viendo") ||
                    titles.contains("Grandes Producciones de tu colección") ||
                    titles.contains("Series Destacadas")
        )
    }

    @Test
    fun `sectionPriority respects established ordering hierarchy`() {
        assertEquals(0, GetHomeFeedUseCase.sectionPriority("Continuar Viendo"))
        assertEquals(10, GetHomeFeedUseCase.sectionPriority("Top 10 Películas Global"))
        assertEquals(20, GetHomeFeedUseCase.sectionPriority("Añadido recientemente"))
        assertEquals(30, GetHomeFeedUseCase.sectionPriority("Porque viste Matrix"))
        assertEquals(40, GetHomeFeedUseCase.sectionPriority("Populares en Netflix"))
        assertEquals(41, GetHomeFeedUseCase.sectionPriority("Populares en Prime Video"))
        assertEquals(42, GetHomeFeedUseCase.sectionPriority("Populares en Disney+"))
        assertEquals(70, GetHomeFeedUseCase.sectionPriority("Grandes Producciones de tu colección"))
        assertEquals(80, GetHomeFeedUseCase.sectionPriority("Series Destacadas"))
        assertEquals(90, GetHomeFeedUseCase.sectionPriority("Películas Recomendadas"))
        assertEquals(110, GetHomeFeedUseCase.sectionPriority("Joyas de tu colección"))
        assertEquals(200, GetHomeFeedUseCase.sectionPriority("Otra sección desconocida"))

        // La prioridad de 'Continuar Viendo' (0) debe ser menor (más prioritario) que 'Series Destacadas' (80)
        assertTrue(
            GetHomeFeedUseCase.sectionPriority("Continuar Viendo") <
                    GetHomeFeedUseCase.sectionPriority("Series Destacadas")
        )
    }
}
