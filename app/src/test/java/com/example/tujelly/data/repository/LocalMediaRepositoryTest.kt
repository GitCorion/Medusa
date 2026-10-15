package com.example.tujelly.data.repository

import com.example.tujelly.data.local.db.JellyfinDao
import com.example.tujelly.data.local.db.JellyfinMediaEntity
import com.example.tujelly.domain.usecase.FakeJellyfinDao
import kotlinx.coroutines.test.runTest
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
    fun getLocalMovies_callsRestrictedQuery_whenRestrictionsActive() = runTest {
        val fakeDao = TrackingFakeJellyfinDao()
        val fakeAllowedDao = FakeAllowedMediaDao()
        val policy = MediaAccessPolicy(allowedMediaDao = fakeAllowedDao)
        policy.saveAllowedIds("user-1", setOf("m1"))
        val repo = LocalMediaRepository(fakeDao, policy)

        repo.getLocalMovies()

        assertTrue(fakeDao.getMoviesRestrictedCalled)
        assertTrue(!fakeDao.getMoviesCalled)
    }

    @Test
    fun getTopMoviesLocal_delegatesCorrectly_basedOnRestrictions() = runTest {
        val fakeDao = TrackingFakeJellyfinDao()
        val fakeAllowedDao = FakeAllowedMediaDao()
        val policy = MediaAccessPolicy(allowedMediaDao = fakeAllowedDao)
        val repo = LocalMediaRepository(fakeDao, policy)

        repo.getTopMoviesLocal(20)
        assertTrue(fakeDao.getTopMoviesCalled)

        val fakeDaoRestricted = TrackingFakeJellyfinDao()
        policy.saveAllowedIds("user-1", setOf("m1"))
        val repoRestricted = LocalMediaRepository(fakeDaoRestricted, policy)

        repoRestricted.getTopMoviesLocal(20)
        assertTrue(fakeDaoRestricted.getTopMoviesRestrictedCalled)
    }
}
