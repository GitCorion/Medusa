package tv.medusa.app.data.local.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Valida las migraciones de Room contra el esquema exportado en `app/schemas` sobre un
 * dispositivo/emulador. Ejecutar con: `./gradlew :app:connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private companion object {
        const val TEST_DB = "migration-test"
    }

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate13To14_backfillsLowercaseColumns() {
        // v13 con datos representativos (incluye las columnas NOT NULL obligatorias).
        helper.createDatabase(TEST_DB, 13).apply {
            execSQL(
                "INSERT INTO jellyfin_media (id, title, type, isPlayed, playbackPositionTicks, isFavorite) " +
                    "VALUES ('m1', 'Spider-Man', 'Movie', 0, 0, 0)"
            )
            execSQL(
                "INSERT INTO jellyfin_media (id, title, originalTitle, seriesName, type, isPlayed, playbackPositionTicks, isFavorite) " +
                    "VALUES ('s1', 'Serie', 'Original Series', 'Serie', 'Series', 0, 0, 0)"
            )
            close()
        }

        // Valida el esquema resultante contra el 14.json exportado y comprueba el backfill.
        val db = helper.runMigrationsAndValidate(TEST_DB, 14, true, AppDatabase.MIGRATION_13_14)

        db.query("SELECT titleLower FROM jellyfin_media WHERE id = 'm1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("spider-man", cursor.getString(0))
        }
        db.query("SELECT originalTitleLower, seriesNameLower FROM jellyfin_media WHERE id = 's1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("original series", cursor.getString(0))
            assertEquals("serie", cursor.getString(1))
        }

        // Comprobación funcional: las consultas reales de `findByTitle*` (comparando contra
        // LOWER(:param)) encuentran las filas migradas sin distinguir mayúsculas.
        db.query(
            "SELECT id FROM jellyfin_media WHERE titleLower = LOWER(?) OR originalTitleLower = LOWER(?) OR seriesNameLower = LOWER(?) LIMIT 1",
            arrayOf("SPIDER-MAN", "SPIDER-MAN", "SPIDER-MAN")
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("m1", cursor.getString(0))
        }
        db.query(
            "SELECT id FROM jellyfin_media WHERE (titleLower = LOWER(?) OR originalTitleLower = LOWER(?)) AND type = 'Series' LIMIT 1",
            arrayOf("SERIE", "SERIE")
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("s1", cursor.getString(0))
        }
    }

    @Test
    fun migrate14To15_createsAllowedMediaIdsTable() {
        helper.createDatabase(TEST_DB, 14).apply {
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 15, true, AppDatabase.MIGRATION_14_15)

        db.execSQL("INSERT INTO allowed_media_ids (mediaId) VALUES ('item-1')")
        db.query("SELECT mediaId FROM allowed_media_ids WHERE mediaId = 'item-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("item-1", cursor.getString(0))
        }
    }
}
