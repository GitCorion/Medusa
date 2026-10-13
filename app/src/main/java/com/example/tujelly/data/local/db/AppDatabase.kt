package com.example.tujelly.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [JellyfinMediaEntity::class, TmdbVoteCacheEntity::class, HomeSectionCacheEntity::class],
    version = 11,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun jellyfinDao(): JellyfinDao
    abstract fun tmdbVoteCacheDao(): TmdbVoteCacheDao
    abstract fun homeSectionCacheDao(): HomeSectionCacheDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE jellyfin_media ADD COLUMN totalItemCount INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE jellyfin_media ADD COLUMN unplayedItemCount INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE jellyfin_media ADD COLUMN tags TEXT DEFAULT NULL")
            }
        }

        // Migración que faltaba — evita que fallbackToDestructiveMigration() borre la BD
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // No-op: el schema no cambió entre v7 y v8
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS tmdb_vote_cache (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        tmdbId INTEGER NOT NULL,
                        isTv INTEGER NOT NULL,
                        voteAverage REAL NOT NULL,
                        voteCount INTEGER NOT NULL,
                        cachedAt INTEGER NOT NULL
                    )"""
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_tmdb_vote_cache_tmdbId_isTv ON tmdb_vote_cache (tmdbId, isTv)"
                )
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_jellyfin_media_type_communityRating ON jellyfin_media (type, communityRating)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_jellyfin_media_seriesId_type ON jellyfin_media (seriesId, type)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_jellyfin_media_playbackPositionTicks ON jellyfin_media (playbackPositionTicks)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_jellyfin_media_productionYear ON jellyfin_media (productionYear)")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS home_section_cache (
                        sectionKey TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        itemIds TEXT NOT NULL,
                        badge TEXT DEFAULT NULL,
                        isRanked INTEGER NOT NULL DEFAULT 0,
                        updatedAt INTEGER NOT NULL DEFAULT 0
                    )"""
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tujelly_database"
                )
                    .addMigrations(
                        MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8,
                        MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11
                    )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
