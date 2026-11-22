package org.fossify.gallery.databases

import androidx.room.Room
import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented Room migration tests covering every exported schema step from v25 through
 * [GalleryDatabase.DB_VERSION] (v32), including data preservation, default backfills, table
 * rebuilds, covering index upgrades, and SQLite trigger integrity.
 */
@RunWith(AndroidJUnit4::class)
class GalleryDatabaseMigrationAndroidTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        GalleryDatabase::class.java,
    )

    private fun migration(startVersion: Int, endVersion: Int): Migration =
        GalleryDatabase.ALL_MIGRATIONS.single {
            it.startVersion == startVersion && it.endVersion == endVersion
        }

    private fun insertV25Medium(
        db: SupportSQLiteDatabase,
        filename: String,
        fullPath: String,
        parentPath: String,
        lastModified: Long,
        dateTaken: Long,
        dateAdded: Long,
        dateSortKey: Long = 0L,
    ) {
        db.execSQL(
            """
            INSERT INTO media (
                filename, full_path, parent_path, last_modified, date_taken, size, type,
                video_duration, is_favorite, deleted_ts, media_store_id, rating, date_added, date_sort_key
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(
                filename,
                fullPath,
                parentPath,
                lastModified,
                dateTaken,
                2048L,
                1,
                0,
                0,
                0L,
                101L,
                4,
                dateAdded,
                dateSortKey,
            ),
        )
    }

    private fun insertV25Collection(
        db: SupportSQLiteDatabase,
        name: String,
        includedPaths: String = "[\"/storage/emulated/0/DCIM/Camera\"]",
    ) {
        db.execSQL(
            """
            INSERT INTO collections (
                name, included_paths, excluded_paths, tag_filter, rating_filter, search_query, sort_order
            ) VALUES (?, ?, '[]', 'vacation', 3, '', 1)
            """.trimIndent(),
            arrayOf<Any>(name, includedPaths),
        )
    }

    @Test
    fun migrate25To26_rebuildsDateSortKeyAndRestoresTriggers() {
        val dbName = "migration-25-26.db"
        helper.createDatabase(dbName, 25).use { db ->
            insertV25Medium(
                db = db,
                filename = "added.jpg",
                fullPath = "/storage/emulated/0/DCIM/added.jpg",
                parentPath = "/storage/emulated/0/DCIM",
                lastModified = 1000L,
                dateTaken = 2000L,
                dateAdded = 3000L,
                dateSortKey = 0L,
            )
            insertV25Medium(
                db = db,
                filename = "taken.jpg",
                fullPath = "/storage/emulated/0/DCIM/taken.jpg",
                parentPath = "/storage/emulated/0/DCIM",
                lastModified = 1000L,
                dateTaken = 2500L,
                dateAdded = 0L,
                dateSortKey = 0L,
            )
            insertV25Medium(
                db = db,
                filename = "modified.jpg",
                fullPath = "/storage/emulated/0/DCIM/modified.jpg",
                parentPath = "/storage/emulated/0/DCIM",
                lastModified = 1800L,
                dateTaken = 0L,
                dateAdded = 0L,
                dateSortKey = 0L,
            )
        }

        helper.runMigrationsAndValidate(dbName, 26, true, migration(25, 26)).use { db ->
            assertEquals(3000L, queryDateSortKey(db, "/storage/emulated/0/DCIM/added.jpg"))
            assertEquals(2500L, queryDateSortKey(db, "/storage/emulated/0/DCIM/taken.jpg"))
            assertEquals(1800L, queryDateSortKey(db, "/storage/emulated/0/DCIM/modified.jpg"))

            // Verify AFTER INSERT trigger computes date_sort_key automatically on newly inserted rows.
            insertV25Medium(
                db = db,
                filename = "fresh.jpg",
                fullPath = "/storage/emulated/0/DCIM/fresh.jpg",
                parentPath = "/storage/emulated/0/DCIM",
                lastModified = 4000L,
                dateTaken = 5000L,
                dateAdded = 6000L,
                dateSortKey = 0L,
            )
            assertEquals(6000L, queryDateSortKey(db, "/storage/emulated/0/DCIM/fresh.jpg"))

            // Verify AFTER UPDATE trigger keeps date_sort_key in sync when date_added changes.
            db.execSQL(
                "UPDATE media SET date_added = 9000 WHERE full_path = ?",
                arrayOf("/storage/emulated/0/DCIM/fresh.jpg"),
            )
            assertEquals(9000L, queryDateSortKey(db, "/storage/emulated/0/DCIM/fresh.jpg"))
        }
    }

    @Test
    fun migrate26To27_addsSmartCollectionFilterColumns() {
        val dbName = "migration-26-27.db"
        helper.createDatabase(dbName, 26).use { db ->
            insertV25Collection(db, "Summer")
        }

        helper.runMigrationsAndValidate(dbName, 27, true, migration(26, 27)).use { db ->
            db.query("SELECT name, min_size, date_range, type_filter FROM collections WHERE name = 'Summer'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Summer", cursor.getString(0))
                assertEquals(0L, cursor.getLong(1))
                assertEquals(0, cursor.getInt(2))
                assertEquals(0, cursor.getInt(3))
            }
        }
    }

    @Test
    fun migrate27To28_addsManualCollectionMediaPaths() {
        val dbName = "migration-27-28.db"
        helper.createDatabase(dbName, 27).use { db ->
            db.execSQL(
                """
                INSERT INTO collections (
                    name, included_paths, excluded_paths, tag_filter, rating_filter, search_query,
                    sort_order, min_size, date_range, type_filter
                ) VALUES ('ManualTest', '[]', '[]', '', 0, '', 0, 0, 0, 0)
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(dbName, 28, true, migration(27, 28)).use { db ->
            db.query("SELECT media_paths FROM collections WHERE name = 'ManualTest'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("[]", cursor.getString(0))
            }
        }
    }

    @Test
    fun migrate28To29_createsMediaReviewStatesTable() {
        val dbName = "migration-28-29.db"
        helper.createDatabase(dbName, 28).close()

        helper.runMigrationsAndValidate(dbName, 29, true, migration(28, 29)).use { db ->
            db.execSQL(
                "INSERT INTO media_review_states (media_path, status, updated_at) VALUES (?, ?, ?)",
                arrayOf<Any>("/storage/emulated/0/DCIM/IMG_001.JPG", "KEPT", 1700000000L),
            )
            db.query("SELECT status, updated_at FROM media_review_states WHERE media_path = '/storage/emulated/0/dcim/img_001.jpg'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("KEPT", cursor.getString(0))
                assertEquals(1700000000L, cursor.getLong(1))
            }
        }
    }

    @Test
    fun migrate29To30_createsMediaPersonScansTable() {
        val dbName = "migration-29-30.db"
        helper.createDatabase(dbName, 29).close()

        helper.runMigrationsAndValidate(dbName, 30, true, migration(29, 30)).use { db ->
            db.execSQL(
                """
                INSERT INTO media_person_scans (
                    media_path, folder_path, content_signature, result, person_coverage, scanned_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any>(
                    "/storage/emulated/0/DCIM/portrait.jpg",
                    "/storage/emulated/0/DCIM",
                    "sig-123",
                    "HAS_PERSON",
                    0.42f,
                    1700000500L,
                ),
            )
            db.query("SELECT result, content_signature FROM media_person_scans WHERE media_path = '/storage/emulated/0/DCIM/portrait.jpg'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("HAS_PERSON", cursor.getString(0))
                assertEquals("sig-123", cursor.getString(1))
            }
        }
    }

    @Test
    fun migrate30To31_rebuildsMediaPersonScansWithIsKeptDefaultZero() {
        val dbName = "migration-30-31.db"
        helper.createDatabase(dbName, 30).use { db ->
            db.execSQL(
                """
                INSERT INTO media_person_scans (
                    media_path, folder_path, content_signature, result, person_coverage, scanned_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any>(
                    "/storage/emulated/0/DCIM/no_person.jpg",
                    "/storage/emulated/0/DCIM",
                    "sig-456",
                    "NO_PERSON",
                    0.0f,
                    1700000999L,
                ),
            )
        }

        helper.runMigrationsAndValidate(dbName, 31, true, migration(30, 31)).use { db ->
            db.query("SELECT folder_path, result, is_kept, scanned_at FROM media_person_scans WHERE media_path = '/storage/emulated/0/DCIM/no_person.jpg'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("/storage/emulated/0/DCIM", cursor.getString(0))
                assertEquals("NO_PERSON", cursor.getString(1))
                assertEquals(0, cursor.getInt(2))
                assertEquals(1700000999L, cursor.getLong(3))
            }
        }
    }

    @Test
    fun migrate31To32_replacesParentPathIndexWithDateSortKeyIndex() {
        val dbName = "migration-31-32.db"
        helper.createDatabase(dbName, 31).use { db ->
            insertV25Medium(
                db = db,
                filename = "older.jpg",
                fullPath = "/storage/emulated/0/DCIM/Camera/older.jpg",
                parentPath = "/storage/emulated/0/DCIM/Camera",
                lastModified = 1000L,
                dateTaken = 1000L,
                dateAdded = 1000L,
                dateSortKey = 0L,
            )
            insertV25Medium(
                db = db,
                filename = "newer.jpg",
                fullPath = "/storage/emulated/0/DCIM/Camera/newer.jpg",
                parentPath = "/storage/emulated/0/DCIM/Camera",
                lastModified = 2000L,
                dateTaken = 2000L,
                dateAdded = 5000L,
                dateSortKey = 0L,
            )
        }

        helper.runMigrationsAndValidate(dbName, 32, true, migration(31, 32)).use { db ->
            assertEquals(5000L, queryDateSortKey(db, "/storage/emulated/0/DCIM/Camera/newer.jpg"))
            db.query(
                "SELECT full_path FROM media WHERE deleted_ts = 0 AND parent_path = '/storage/emulated/0/dcim/camera' ORDER BY date_sort_key DESC LIMIT 1"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("/storage/emulated/0/DCIM/Camera/newer.jpg", cursor.getString(0))
            }
        }
    }

    @Test
    fun migrateAll_25To32_preservesMediaAndCollectionsAndValidatesFinalSchema() {
        val dbName = "migration-all-25-32.db"
        helper.createDatabase(dbName, 25).use { db ->
            insertV25Medium(
                db = db,
                filename = "sunset.jpg",
                fullPath = "/storage/emulated/0/DCIM/Camera/sunset.jpg",
                parentPath = "/storage/emulated/0/DCIM/Camera",
                lastModified = 1700000000L,
                dateTaken = 1700000100L,
                dateAdded = 1700000200L,
                dateSortKey = 0L,
            )
            insertV25Collection(db, "FavoritesBackup")
        }

        helper.runMigrationsAndValidate(
            dbName,
            GalleryDatabase.DB_VERSION,
            true,
            *GalleryDatabase.ALL_MIGRATIONS,
        ).use { db ->
            assertEquals(1700000200L, queryDateSortKey(db, "/storage/emulated/0/DCIM/Camera/sunset.jpg"))
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val roomDb = Room.databaseBuilder(context, GalleryDatabase::class.java, dbName)
            .addMigrations(*GalleryDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val media = roomDb.MediumDao().getMediaFromPath("/storage/emulated/0/DCIM/Camera")
            assertEquals(1, media.size)
            assertEquals("sunset.jpg", media.first().name)
            assertEquals(1700000200L, media.first().dateSortKey)

            val aggregates = roomDb.MediumDao().getDirectoryAggregates()
            assertEquals(1, aggregates.size)
            assertEquals("/storage/emulated/0/DCIM/Camera/sunset.jpg", aggregates.first().thumbnail)

            val collections = roomDb.CollectionDao().getAll()
            assertEquals(1, collections.size)
            assertEquals("FavoritesBackup", collections.first().name)
            assertEquals("[]", collections.first().mediaPaths)
            assertEquals(0L, collections.first().minSize)
        } finally {
            roomDb.close()
            context.deleteDatabase(dbName)
        }
    }

    private fun queryDateSortKey(db: SupportSQLiteDatabase, fullPath: String): Long {
        db.query("SELECT date_sort_key FROM media WHERE full_path = ?", arrayOf(fullPath)).use { cursor ->
            assertTrue("Expected row for $fullPath", cursor.moveToFirst())
            return cursor.getLong(0)
        }
    }
}
