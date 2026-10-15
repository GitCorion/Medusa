package com.example.tujelly.data.repository

import com.example.tujelly.data.local.db.AllowedMediaDao
import com.example.tujelly.data.local.db.AllowedMediaIdEntity
import com.example.tujelly.data.local.db.JellyfinMediaEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeAllowedMediaDao : AllowedMediaDao {
    private val allowed = mutableSetOf<String>()
    private val restrictionsFlow = MutableStateFlow(false)

    override suspend fun replaceAll(ids: List<String>) {
        allowed.clear()
        allowed.addAll(ids)
        restrictionsFlow.value = allowed.isNotEmpty()
    }

    override suspend fun addAll(entities: List<AllowedMediaIdEntity>) {
        allowed.addAll(entities.map { it.mediaId })
        restrictionsFlow.value = allowed.isNotEmpty()
    }

    override suspend fun hasRestrictions(): Boolean = allowed.isNotEmpty()

    override fun hasRestrictionsFlow(): Flow<Boolean> = restrictionsFlow.asStateFlow()

    override suspend fun isAllowed(id: String): Boolean = allowed.contains(id)

    override suspend fun count(): Int = allowed.size

    override suspend fun insertAll(entities: List<AllowedMediaIdEntity>) {
        allowed.addAll(entities.map { it.mediaId })
        restrictionsFlow.value = allowed.isNotEmpty()
    }

    override suspend fun clearAll() {
        allowed.clear()
        restrictionsFlow.value = false
    }
}

class MediaAccessPolicyTest {

    @Test
    fun hasRestrictions_returnsFalse_whenNoDaoOrFileConfigured() = runTest {
        val policy = MediaAccessPolicy()
        assertFalse(policy.hasRestrictions())
    }

    @Test
    fun hasRestrictions_withDao_reflectsDatabaseState() = runTest {
        val fakeDao = FakeAllowedMediaDao()
        val policy = MediaAccessPolicy(allowedMediaDao = fakeDao)

        assertFalse(policy.hasRestrictions())

        policy.saveAllowedIds("user-1", setOf("m1", "m2"))
        assertTrue(policy.hasRestrictions())
        assertTrue(policy.isAllowed("m1"))
        assertTrue(policy.isAllowed("m2"))
        assertFalse(policy.isAllowed("m3"))
    }

    @Test
    fun addAllowedIds_addsItemsIncrementallyWithoutClearingExisting() = runTest {
        val fakeDao = FakeAllowedMediaDao()
        val policy = MediaAccessPolicy(allowedMediaDao = fakeDao)

        policy.saveAllowedIds("user-1", setOf("m1"))
        assertTrue(policy.isAllowed("m1"))
        assertFalse(policy.isAllowed("m2"))

        policy.addAllowedIds("user-1", listOf("m2"))
        assertTrue(policy.isAllowed("m1"))
        assertTrue(policy.isAllowed("m2"))
        assertFalse(policy.isAllowed("m3"))
    }

    @Test
    fun filterAllowed_returnsFullList_whenNoRestrictions() = runTest {
        val policy = MediaAccessPolicy()
        val list = listOf(
            createDummyEntity("m1"),
            createDummyEntity("m2"),
            createDummyEntity("m3")
        )

        val filtered = list.filterAllowed(policy)
        assertEquals(3, filtered.size)
    }

    private fun createDummyEntity(id: String): JellyfinMediaEntity {
        return JellyfinMediaEntity(
            id = id,
            title = "Title $id",
            type = "Movie",
            isPlayed = false,
            playbackPositionTicks = 0L,
            isFavorite = false
        )
    }
}
