package tv.medusa.app.data.repository

import tv.medusa.app.data.local.db.JellyfinDao
import tv.medusa.app.data.local.db.JellyfinMediaEntity
import tv.medusa.app.domain.usecase.FakeJellyfinDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingFakeJellyfinDao(
    private val delegate: FakeJellyfinDao = FakeJellyfinDao()
) : JellyfinDao by delegate {
    var getMoviesCalled = false
    var getMoviesRestrictedCalled = false
    var getSeriesCalled = false
    var getSeriesRestrictedCalled = false
    var getTopMoviesCalled = false
    var getTopMoviesRestrictedCalled = false

    fun setLocalItems(items: List<JellyfinMediaEntity>) = delegate.setLocalItems(items)

    override suspend fun getMovies(): List<JellyfinMediaEntity> {
        getMoviesCalled = true
        return delegate.getMovies()
    }

    override suspend fun getMoviesRestricted(): List<JellyfinMediaEntity> {
        getMoviesRestrictedCalled = true
        return delegate.getMoviesRestricted()
    }

    override suspend fun getSeries(): List<JellyfinMediaEntity> {
        getSeriesCalled = true
        return delegate.getSeries()
    }

    override suspend fun getSeriesRestricted(): List<JellyfinMediaEntity> {
        getSeriesRestrictedCalled = true
        return delegate.getSeriesRestricted()
    }

    override suspend fun getTopMoviesLocal(limit: Int): List<JellyfinMediaEntity> {
        getTopMoviesCalled = true
        return delegate.getTopMoviesLocal(limit)
    }

    override suspend fun getTopMoviesLocalRestricted(limit: Int): List<JellyfinMediaEntity> {
        getTopMoviesRestrictedCalled = true
        return delegate.getTopMoviesLocalRestricted(limit)
    }
}

class LocalMediaRepositoryTest {

    @Test
    fun getLocalMovies_callsUnrestrictedQuery_whenNoRestrictions() = runTest {
        val fakeDao = TrackingFakeJellyfinDao()
        val fakeAllowedDao = FakeAllowedMediaDao()
        val policy = MediaAccessPolicy(allowedMediaDao = fakeAllowedDao)
        val repo = LocalMediaRepository(fakeDao, policy)

        repo.getLocalMovies()

        assertTrue(fakeDao.getMoviesCalled)
        assertTrue(!fakeDao.getMoviesRestrictedCalled)
    }

    @Test
    fun getLocalMovies_callsRestrictedQuery_whenRestrictionsCoverCatalog() = runTest {
        val fakeDao = TrackingFakeJellyfinDao()
        fakeDao.setLocalItems(listOf(JellyfinMediaEntity(id = "m1", title = "M1", type = "Movie")))
        val fakeAllowedDao = FakeAllowedMediaDao()
        val policy = MediaAccessPolicy(allowedMediaDao = fakeAllowedDao)
        // Lista completa (cubre el catálogo): el filtro es un no-op y se usa la query restringida.
        policy.saveAllowedIds("user-1", setOf("m1"))
        val repo = LocalMediaRepository(fakeDao, policy)

        repo.getLocalMovies()

        assertTrue(fakeDao.getMoviesRestrictedCalled)
        assertTrue(!fakeDao.getMoviesCalled)
    }

    @Test
    fun getLocalMovies_ignoresStalePartialAllowedList() = runTest {
        val fakeDao = TrackingFakeJellyfinDao()
        fakeDao.setLocalItems(
            listOf(
                JellyfinMediaEntity(id = "m1", title = "M1", type = "Movie"),
                JellyfinMediaEntity(id = "m2", title = "M2", type = "Movie"),
                JellyfinMediaEntity(id = "m3", title = "M3", type = "Movie")
            )
        )
        val fakeAllowedDao = FakeAllowedMediaDao()
        val policy = MediaAccessPolicy(allowedMediaDao = fakeAllowedDao)
        // Lista de permitidos desincronizada (1 de 3): filtrar ocultaría casi todo → se ignora.
        policy.saveAllowedIds("user-1", setOf("m1"))
        val repo = LocalMediaRepository(fakeDao, policy)

        repo.getLocalMovies()

        assertTrue(fakeDao.getMoviesCalled)
        assertTrue(!fakeDao.getMoviesRestrictedCalled)
    }

    @Test
    fun getRecentlyPlayedLocal_returnsMostRecentlyWatchedFirst_andOnlyWatched() = runTest {
        val fakeDao = FakeJellyfinDao()
        fakeDao.setLocalItems(
            listOf(
                JellyfinMediaEntity(id = "vista-antes", title = "Vista Antes", type = "Movie", productionYear = 2020, isPlayed = true),
                JellyfinMediaEntity(id = "sin-ver", title = "Sin Ver", type = "Movie", productionYear = 2021, isPlayed = false),
                JellyfinMediaEntity(id = "vista-despues", title = "Vista Después", type = "Movie", productionYear = 2022, isPlayed = true)
            )
        )
        val repo = LocalMediaRepository(fakeDao, MediaAccessPolicy(allowedMediaDao = FakeAllowedMediaDao()))

        val result = repo.getRecentlyPlayedLocal(limit = 10)

        // El último insertado es el visto más recientemente; los no vistos no aparecen.
        assertEquals(listOf("vista-despues", "vista-antes"), result.map { it.id })
    }

    @Test
    fun getTopMoviesLocal_delegatesCorrectly_basedOnRestrictions() = runTest {        val fakeDao = TrackingFakeJellyfinDao()
        val fakeAllowedDao = FakeAllowedMediaDao()
        val policy = MediaAccessPolicy(allowedMediaDao = fakeAllowedDao)
        val repo = LocalMediaRepository(fakeDao, policy)

        repo.getTopMoviesLocal(20)
        assertTrue(fakeDao.getTopMoviesCalled)

        val fakeDaoRestricted = TrackingFakeJellyfinDao()
        fakeDaoRestricted.setLocalItems(listOf(JellyfinMediaEntity(id = "m1", title = "M1", type = "Movie")))
        policy.saveAllowedIds("user-1", setOf("m1"))
        val repoRestricted = LocalMediaRepository(fakeDaoRestricted, policy)

        repoRestricted.getTopMoviesLocal(20)
        assertTrue(fakeDaoRestricted.getTopMoviesRestrictedCalled)
    }
}
