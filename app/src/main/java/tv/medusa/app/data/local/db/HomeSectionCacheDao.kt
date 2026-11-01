package tv.medusa.app.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface HomeSectionCacheDao {

    @Query("SELECT * FROM home_section_cache WHERE sectionKey = :key")
    suspend fun getSection(key: String): HomeSectionCacheEntity?

    @Query("SELECT * FROM home_section_cache ORDER BY sortOrder ASC, rowid ASC")
    suspend fun getAllSections(): List<HomeSectionCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(section: HomeSectionCacheEntity)

    @Query("DELETE FROM home_section_cache WHERE sectionKey = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM home_section_cache")
    suspend fun clearAll()
}
