package com.example.tujelly.data.local.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSectionCacheTest {

    @Test
    fun `isExpired returns false for newly created entity`() {
        val entity = HomeSectionCacheEntity(
            sectionKey = "platform_8",
            title = "Populares en Netflix",
            itemIds = "id1,id2,id3",
            updatedAt = System.currentTimeMillis()
        )

        assertFalse(entity.isExpired(HomeSectionCacheEntity.TTL_PLATFORMS))
        assertFalse(entity.isExpired(HomeSectionCacheEntity.TTL_MAX_STALE))
    }

    @Test
    fun `isExpired returns true after TTL expires`() {
        val tenHoursAgo = System.currentTimeMillis() - (10L * 3600 * 1000)
        val entity = HomeSectionCacheEntity(
            sectionKey = "platform_8",
            title = "Populares en Netflix",
            itemIds = "id1,id2,id3",
            updatedAt = tenHoursAgo
        )

        // 10 hours has NOT expired for 24h platform TTL
        assertFalse(entity.isExpired(HomeSectionCacheEntity.TTL_PLATFORMS))

        // 10 hours HAS expired for a short 6h TTL
        assertTrue(entity.isExpired(6L * 3600 * 1000))

        // 50 hours has expired for TTL_MAX_STALE (48h)
        val fiftyHoursAgo = System.currentTimeMillis() - (50L * 3600 * 1000)
        val ancientEntity = entity.copy(updatedAt = fiftyHoursAgo)
        assertTrue(ancientEntity.isExpired(HomeSectionCacheEntity.TTL_MAX_STALE))
    }

    @Test
    fun `fake dao correctly upserts and retrieves cached sections`() {
        val map = mutableMapOf<String, HomeSectionCacheEntity>()
        val fakeDao = object : HomeSectionCacheDao {
            override suspend fun getSection(key: String): HomeSectionCacheEntity? = map[key]
            override suspend fun getAllSections(): List<HomeSectionCacheEntity> = map.values.toList()
            override suspend fun upsert(section: HomeSectionCacheEntity) { map[section.sectionKey] = section }
            override suspend fun delete(key: String) { map.remove(key) }
            override suspend fun clearAll() { map.clear() }
        }

        kotlinx.coroutines.runBlocking {
            fakeDao.upsert(
                HomeSectionCacheEntity(
                    sectionKey = "platform_netflix",
                    title = "Populares en Netflix",
                    itemIds = "item_1,item_2"
                )
            )

            val retrieved = fakeDao.getSection("platform_netflix")
            assertNotNull(retrieved)
            assertEquals("Populares en Netflix", retrieved?.title)
            assertEquals("item_1,item_2", retrieved?.itemIds)

            fakeDao.delete("platform_netflix")
            assertNull(fakeDao.getSection("platform_netflix"))
        }
    }
}
