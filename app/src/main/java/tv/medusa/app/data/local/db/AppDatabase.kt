package tv.medusa.app.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [JellyfinMediaEntity::class, TmdbVoteCacheEntity::class, HomeSectionCacheEntity::class, JellyfinMediaFts::class, AllowedMediaIdEntity::class],
    version = 16,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun jellyfinDao(): JellyfinDao
    abstract fun tmdbVoteCacheDao(): TmdbVoteCacheDao
    abstract fun homeSectionCacheDao(): HomeSectionCacheDao
    abstract fun allowedMediaDao(): AllowedMediaDao

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

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_jellyfin_media_type_isPlayed_communityRating ON jellyfin_media (type, isPlayed, communityRating)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_jellyfin_media_isPlayed_playbackPositionTicks ON jellyfin_media (isPlayed, playbackPositionTicks)")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Tabla virtual FTS4 con contenido externo (misma que genera Room para la entidad
                // JellyfinMediaFts) + triggers de sincronización y reconstrucción del índice.
                db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `jellyfin_media_fts` USING FTS4(`title` TEXT NOT NULL, `originalTitle` TEXT, content=`jellyfin_media`)")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_jellyfin_media_fts_BEFORE_UPDATE BEFORE UPDATE ON `jellyfin_media` BEGIN DELETE FROM `jellyfin_media_fts` WHERE `docid`=OLD.`rowid`; END")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_jellyfin_media_fts_BEFORE_DELETE BEFORE DELETE ON `jellyfin_media` BEGIN DELETE FROM `jellyfin_media_fts` WHERE `docid`=OLD.`rowid`; END")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_jellyfin_media_fts_AFTER_UPDATE AFTER UPDATE ON `jellyfin_media` BEGIN INSERT INTO `jellyfin_media_fts`(`docid`, `title`, `originalTitle`) VALUES (NEW.`rowid`, NEW.`title`, NEW.`originalTitle`); END")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_jellyfin_media_fts_AFTER_INSERT AFTER INSERT ON `jellyfin_media` BEGIN INSERT INTO `jellyfin_media_fts`(`docid`, `title`, `originalTitle`) VALUES (NEW.`rowid`, NEW.`title`, NEW.`originalTitle`); END")
                db.execSQL("INSERT INTO `jellyfin_media_fts`(`jellyfin_media_fts`) VALUES('rebuild')")
            }
        }

        /**
         * v13 → v14: columnas normalizadas (`titleLower`, `originalTitleLower`, `seriesNameLower`)
         * + índices, para que las búsquedas exactas por título usen índice en vez de un full-scan
         * con `LOWER(columna)`. El backfill usa el `LOWER()` de SQLite (ASCII) para coincidir con
         * `asciiLowercase()` del constructor de [JellyfinMediaEntity].
         */
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `allowed_media_ids` (`mediaId` TEXT NOT NULL, PRIMARY KEY(`mediaId`))"
                )
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE jellyfin_media ADD COLUMN lastPlayedTicks INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_jellyfin_media_lastPlayedTicks ON jellyfin_media (lastPlayedTicks)")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE jellyfin_media ADD COLUMN titleLower TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE jellyfin_media ADD COLUMN originalTitleLower TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE jellyfin_media ADD COLUMN seriesNameLower TEXT NOT NULL DEFAULT ''")

                db.execSQL("UPDATE jellyfin_media SET titleLower = LOWER(title)")
                db.execSQL("UPDATE jellyfin_media SET originalTitleLower = LOWER(COALESCE(originalTitle, ''))")
                db.execSQL("UPDATE jellyfin_media SET seriesNameLower = LOWER(COALESCE(seriesName, ''))")

                db.execSQL("CREATE INDEX IF NOT EXISTS index_jellyfin_media_titleLower ON jellyfin_media (titleLower)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_jellyfin_media_originalTitleLower ON jellyfin_media (originalTitleLower)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_jellyfin_media_seriesNameLower ON jellyfin_media (seriesNameLower)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "medusa_database"
                )
                    .addMigrations(
                        MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8,
                        MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12,
                        MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16
                    )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
