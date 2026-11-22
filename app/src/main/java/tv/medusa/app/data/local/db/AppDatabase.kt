package tv.medusa.app.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        JellyfinMediaEntity::class,
        TmdbVoteCacheEntity::class,
        HomeSectionCacheEntity::class,
        JellyfinMediaFts::class,
        JellyfinMediaGenresFts::class,
        AllowedMediaIdEntity::class,
        SeasonalItemEntity::class,
        SeasonalIndexMetaEntity::class,
        CollectionItemEntity::class,
        CollectionMetaEntity::class
    ],
    version = 22,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun jellyfinDao(): JellyfinDao
    abstract fun tmdbVoteCacheDao(): TmdbVoteCacheDao
    abstract fun homeSectionCacheDao(): HomeSectionCacheDao
    abstract fun allowedMediaDao(): AllowedMediaDao
    abstract fun seasonalDao(): SeasonalDao
    abstract fun collectionDao(): CollectionDao

    /** Limpia todo el catálogo en una sola transacción: nunca se ve un estado a medias. */
    @Transaction
    suspend fun clearAllCatalogData() {
        jellyfinDao().clearAll()
        allowedMediaDao().clearAll()
        homeSectionCacheDao().clearAll()
        seasonalDao().clearAll()
        collectionDao().clearAll()
    }

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

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE jellyfin_media ADD COLUMN premiereDateMs INTEGER DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_jellyfin_media_premiereDateMs ON jellyfin_media (premiereDateMs)")
            }
        }

        /**
         * v17 → v18: snapshot persistente de todas las secciones del Home (incluidas las
         * locales) + índice persistente y paginable de los especiales estacionales.
         */
        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE home_section_cache ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE home_section_cache ADD COLUMN catalogVersion TEXT NOT NULL DEFAULT ''")

                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `seasonal_items` (
                        `eventId` TEXT NOT NULL,
                        `mediaId` TEXT NOT NULL,
                        `score` REAL NOT NULL,
                        `editorial` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`eventId`, `mediaId`)
                    )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_seasonal_items_eventId_score` ON `seasonal_items` (`eventId`, `score`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_seasonal_items_eventId_editorial` ON `seasonal_items` (`eventId`, `editorial`)")

                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `seasonal_index_meta` (
                        `eventId` TEXT NOT NULL,
                        `catalogVersion` TEXT NOT NULL,
                        `itemCount` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`eventId`)
                    )"""
                )
            }
        }

        /**
         * v18 → v19: índice FTS4 de géneros. Permite buscar «terror», «navidad», «romance»
         * sin hacer un `LIKE '%…%'` sobre las 32.000 filas del catálogo al abrir un especial.
         */
        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createGenresFts(db)
            }
        }

        private fun createGenresFts(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `jellyfin_media_genres_fts` USING FTS4(`genres` TEXT, content=`jellyfin_media`)")
            db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_genres_sync_jellyfin_media_genres_fts_BEFORE_UPDATE BEFORE UPDATE ON `jellyfin_media` BEGIN DELETE FROM `jellyfin_media_genres_fts` WHERE `docid`=OLD.`rowid`; END")
            db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_genres_sync_jellyfin_media_genres_fts_BEFORE_DELETE BEFORE DELETE ON `jellyfin_media` BEGIN DELETE FROM `jellyfin_media_genres_fts` WHERE `docid`=OLD.`rowid`; END")
            db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_genres_sync_jellyfin_media_genres_fts_AFTER_UPDATE AFTER UPDATE ON `jellyfin_media` BEGIN INSERT INTO `jellyfin_media_genres_fts`(`docid`, `genres`) VALUES (NEW.`rowid`, NEW.`genres`); END")
            db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_genres_sync_jellyfin_media_genres_fts_AFTER_INSERT AFTER INSERT ON `jellyfin_media` BEGIN INSERT INTO `jellyfin_media_genres_fts`(`docid`, `genres`) VALUES (NEW.`rowid`, NEW.`genres`); END")
            db.execSQL("INSERT INTO `jellyfin_media_genres_fts`(`jellyfin_media_genres_fts`) VALUES('rebuild')")
        }

        /**
         * v19 → v20: payload JSON de las tarjetas del snapshot del Home. Evita reconstruir 200+
         * filas desde `jellyfin_media` en el arranque (varias consultas aleatorias lentas en TV).
         */
        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE home_section_cache ADD COLUMN payload TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * v20 → v21: la tabla FTS de géneros pasa a ser una entidad Room (necesaria para que
         * MigrationTestHelper valide el esquema). La tabla/triggers ya se crearon en v19.
         */
        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createGenresFts(db)
            }
        }

        /**
         * v21 → v22: índice genérico de colecciones (plataformas, tendencias, popular regional).
         * Guarda solo la pertenencia + clave de orden; los datos del título siguen en
         * `jellyfin_media`.
         */
        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `collection_items` (
                        `collectionKey` TEXT NOT NULL,
                        `mediaId` TEXT NOT NULL,
                        `rank` INTEGER,
                        `score` REAL NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`collectionKey`, `mediaId`)
                    )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_collection_items_collectionKey_score` ON `collection_items` (`collectionKey`, `score`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_collection_items_collectionKey_rank` ON `collection_items` (`collectionKey`, `rank`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_collection_items_mediaId` ON `collection_items` (`mediaId`)")

                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `collection_meta` (
                        `collectionKey` TEXT NOT NULL,
                        `region` TEXT NOT NULL,
                        `catalogVersion` TEXT NOT NULL,
                        `itemCount` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`collectionKey`)
                    )"""
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "medusa_database"
                )
                    .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            db.execSQL("PRAGMA synchronous = NORMAL;")
                        }
                    })
                    .addMigrations(
                        MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8,
                        MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12,
                        MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16,
                        MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21
                        , MIGRATION_21_22
                    )
                    // Sin fallback destructivo: una migración olvidada debe fallar en alto,
                    // no borrar silenciosamente favoritos, progreso y catálogo del usuario.
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
