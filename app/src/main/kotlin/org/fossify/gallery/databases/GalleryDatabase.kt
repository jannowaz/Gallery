package org.fossify.gallery.databases

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.fossify.gallery.interfaces.*
import org.fossify.gallery.models.*

@Database(entities = [Directory::class, Medium::class, Widget::class, DateTaken::class, Favorite::class, MediaCollection::class, MediaCache::class, BatchJobItem::class, MediaTag::class, CompressionReviewItem::class, org.fossify.gallery.models.FileHash::class], version = 26)
abstract class GalleryDatabase : RoomDatabase() {

    abstract fun DirectoryDao(): DirectoryDao

    abstract fun MediumDao(): MediumDao

    abstract fun WidgetsDao(): WidgetsDao

    abstract fun DateTakensDao(): DateTakensDao

    abstract fun FavoritesDao(): FavoritesDao

    abstract fun CollectionDao(): CollectionDao

    abstract fun MediaCacheDao(): MediaCacheDao

    abstract fun BatchJobItemDao(): BatchJobItemDao

    abstract fun MediaTagDao(): MediaTagDao

    abstract fun CompressionReviewDao(): CompressionReviewDao

    abstract fun FileHashDao(): org.fossify.gallery.interfaces.FileHashDao

    companion object {
        private var db: GalleryDatabase? = null

        // Keeps media.date_sort_key (`date_added > 0 ? date_added : (date_taken > 0 ? date_taken :
        // last_modified)`) auto-maintained in the database itself, regardless of whether a row was
        // written via Room's own generated INSERT/UPDATE (which just carries over whatever the Kotlin
        // Medium.dateSortKey field happened to be, likely its 0L default) or raw SQL. date_added is
        // preferred so newly downloaded media sorts to the top even when its EXIF date_taken is old
        // (see Medium.dateAdded's doc). "UPDATE OF date_added, date_taken, last_modified" only fires
        // when one of those columns appears in the triggering UPDATE's SET list, and this trigger's
        // own body only ever touches date_sort_key, so it can't re-trigger itself.
        private const val DATE_SORT_KEY_EXPR =
            "CASE WHEN NEW.date_added > 0 THEN NEW.date_added WHEN NEW.date_taken > 0 THEN NEW.date_taken ELSE NEW.last_modified END"

        private fun createDateSortKeyTriggers(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS trg_media_date_sort_key_ins AFTER INSERT ON media BEGIN
                    UPDATE media SET date_sort_key = $DATE_SORT_KEY_EXPR WHERE id = NEW.id;
                END
                """
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS trg_media_date_sort_key_upd AFTER UPDATE OF date_added, date_taken, last_modified ON media BEGIN
                    UPDATE media SET date_sort_key = $DATE_SORT_KEY_EXPR WHERE id = NEW.id;
                END
                """
            )
        }

        fun getInstance(context: Context): GalleryDatabase {
            if (db == null) {
                synchronized(GalleryDatabase::class) {
                    if (db == null) {
                        db = Room.databaseBuilder(context.applicationContext, GalleryDatabase::class.java, "gallery.db")
                            .addMigrations(MIGRATION_4_5)
                            .addMigrations(MIGRATION_5_6)
                            .addMigrations(MIGRATION_6_7)
                            .addMigrations(MIGRATION_7_8)
                            .addMigrations(MIGRATION_8_9)
                            .addMigrations(MIGRATION_9_10)
                            .addMigrations(MIGRATION_10_11)
                            .addMigrations(MIGRATION_11_12)
                            .addMigrations(MIGRATION_12_13)
                            .addMigrations(MIGRATION_13_14)
                            .addMigrations(MIGRATION_14_15)
                            .addMigrations(MIGRATION_15_16)
                            .addMigrations(MIGRATION_16_17)
                            .addMigrations(MIGRATION_17_18)
                            .addMigrations(MIGRATION_18_19)
                            .addMigrations(MIGRATION_19_20)
                            .addMigrations(MIGRATION_20_21)
                            .addMigrations(MIGRATION_21_22)
                            .addMigrations(MIGRATION_22_23)
                            .addMigrations(MIGRATION_23_24)
                            .addMigrations(MIGRATION_24_25)
                            .addMigrations(MIGRATION_25_26)
                            .fallbackToDestructiveMigrationFrom(1, 2, 3)
                            // Room only runs migrations when upgrading an *existing* database - a
                            // fresh install gets its schema (including date_sort_key and its index)
                            // straight from the @Entity/@Index annotations, since that column is a
                            // real plain column now (unlike an earlier, reverted attempt at an
                            // expression index, which Room's schema validation can't represent at
                            // all). Only the two triggers that keep date_sort_key in sync aren't
                            // annotation-expressible, so a fresh install still needs them created
                            // explicitly here.
                            .addCallback(object : RoomDatabase.Callback() {
                                override fun onCreate(db: SupportSQLiteDatabase) {
                                    super.onCreate(db)
                                    createDateSortKeyTriggers(db)
                                }
                            })
                            .build()
                    }
                }
            }
            return db!!
        }

        fun destroyInstance() {
            if (db?.isOpen == true) {
                db?.close()
            }
            db = null
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE media ADD COLUMN video_duration INTEGER default 0 NOT NULL")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `widgets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `widget_id` INTEGER NOT NULL, `folder_path` TEXT NOT NULL)")
                database.execSQL("CREATE UNIQUE INDEX `index_widgets_widget_id` ON `widgets` (`widget_id`)")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `date_takens` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `full_path` TEXT NOT NULL, `filename` TEXT NOT NULL, `parent_path` TEXT NOT NULL, `date_taken` INTEGER NOT NULL, `last_fixed` INTEGER NOT NULL)")
                database.execSQL("CREATE TABLE IF NOT EXISTS `favorites` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `full_path` TEXT NOT NULL, `filename` TEXT NOT NULL, `parent_path` TEXT NOT NULL)")

                database.execSQL("CREATE UNIQUE INDEX `index_date_takens_full_path` ON `date_takens` (`full_path`)")
                database.execSQL("CREATE UNIQUE INDEX `index_favorites_full_path` ON `favorites` (`full_path`)")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE directories ADD COLUMN sort_value TEXT default '' NOT NULL")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE date_takens ADD COLUMN last_modified INTEGER default 0 NOT NULL")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE media ADD COLUMN media_store_id INTEGER default 0 NOT NULL")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `collections` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `name` TEXT NOT NULL, `included_paths` TEXT NOT NULL DEFAULT '[]', `excluded_paths` TEXT NOT NULL DEFAULT '[]', `sort_order` INTEGER NOT NULL DEFAULT 0)")
            }
        }

        private         val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE media ADD COLUMN rating INTEGER default 0 NOT NULL")
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `media_cache` (`full_path` TEXT NOT NULL, `tags` TEXT NOT NULL, `rating` INTEGER NOT NULL, `last_scanned` INTEGER NOT NULL, PRIMARY KEY(`full_path`))")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE collections ADD COLUMN tag_filter TEXT default '' NOT NULL")
                database.execSQL("ALTER TABLE collections ADD COLUMN rating_filter INTEGER default 0 NOT NULL")
                database.execSQL("ALTER TABLE collections ADD COLUMN search_query TEXT default '' NOT NULL")
            }
        }

        // Every DAO already queries these path columns with "COLLATE NOCASE" (rename, move,
        // favorite, rating, soft-delete). SQLite can only use an index when the query's
        // collating sequence matches the index's declared collation, and these unique indices
        // were created without one (defaulting to BINARY) - so every one of those writes was
        // doing a full table scan instead of an index lookup. Recreating the indices with
        // COLLATE NOCASE baked in fixes this without touching table data; Room's runtime schema
        // validation (TableInfo) does not compare index collation, only name/uniqueness/columns,
        // so this is safe to do without a full table rebuild.
        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP INDEX IF EXISTS `index_media_full_path`")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_media_full_path` ON `media` (`full_path` COLLATE NOCASE)")

                database.execSQL("DROP INDEX IF EXISTS `index_directories_path`")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_directories_path` ON `directories` (`path` COLLATE NOCASE)")

                database.execSQL("DROP INDEX IF EXISTS `index_favorites_full_path`")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_favorites_full_path` ON `favorites` (`full_path` COLLATE NOCASE)")

                database.execSQL("DROP INDEX IF EXISTS `index_date_takens_full_path`")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_date_takens_full_path` ON `date_takens` (`full_path` COLLATE NOCASE)")
            }
        }

        // Supports the Paging3 media queries (MediumDao.getMediaPagedBy*): each one filters on
        // deleted_ts and orders by one sort column, and SQLite's LIMIT/OFFSET cost is O(offset)
        // without an index to walk in order - these keep deep scrolling from degrading into a
        // full table scan per page.
        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_media_deleted_ts_date_taken_last_modified` ON `media` (`deleted_ts`, `date_taken`, `last_modified`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_media_deleted_ts_size` ON `media` (`deleted_ts`, `size`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_media_deleted_ts_rating_last_modified` ON `media` (`deleted_ts`, `rating`, `last_modified`)")
            }
        }

        // Scratch table backing MediaBatchWorker (batch rename/move/copy jobs) - see BatchJobItem.
        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `batch_job_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `job_id` TEXT NOT NULL, `source_path` TEXT NOT NULL, `target_path` TEXT NOT NULL, `created_at` INTEGER NOT NULL)"
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_batch_job_items_job_id` ON `batch_job_items` (`job_id`)")
            }
        }

        // Normalizes tags out of the comma-separated `media_cache.tags` blob into one row per
        // (file, tag). The old column matched tags with substring checks (split + contains), which
        // false-positived e.g. a tag "Auto" against a file only tagged "Autobahn", and every
        // tag-browsing/counting operation had to load every tagged file's full row into memory to
        // split and recount client-side. `media_cache` itself (rating/last_scanned) is untouched -
        // its `tags` column is left in place but unused going forward rather than risking a
        // column-drop migration for a column nothing reads anymore.
        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `media_tags` (`media_path` TEXT NOT NULL, `tag` TEXT NOT NULL, PRIMARY KEY(`media_path`, `tag`))")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_media_tags_tag` ON `media_tags` (`tag`)")

                val insertStmt = database.compileStatement("INSERT OR IGNORE INTO media_tags (media_path, tag) VALUES (?, ?)")
                database.query("SELECT full_path, tags FROM media_cache WHERE tags != ''").use { cursor ->
                    val pathCol = cursor.getColumnIndex("full_path")
                    val tagsCol = cursor.getColumnIndex("tags")
                    while (cursor.moveToNext()) {
                        val path = cursor.getString(pathCol)
                        val rawTags = cursor.getString(tagsCol) ?: continue
                        rawTags.split(",")
                            .map { org.fossify.gallery.helpers.XmpWriter.sanitizeTag(it) }
                            .filter { it.isNotBlank() }
                            .distinct()
                            .forEach { tag ->
                                insertStmt.clearBindings()
                                insertStmt.bindString(1, path)
                                insertStmt.bindString(2, tag)
                                insertStmt.executeInsert()
                            }
                    }
                }
            }
        }

        // Adds media.date_sort_key - see the field's own doc comment on Medium.kt and
        // createDateSortKeyTriggers above for the full "why" (replaces an expression-index attempt
        // that broke Room's schema validation and had to be reverted). Order matters: the column
        // must exist before it can be backfilled or indexed, and the old expression-shaped index
        // this replaces has to go since @Entity no longer declares it.
        private val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE media ADD COLUMN date_sort_key INTEGER NOT NULL DEFAULT 0")
                database.execSQL("UPDATE media SET date_sort_key = CASE WHEN date_taken > 0 THEN date_taken ELSE last_modified END")
                database.execSQL("DROP INDEX IF EXISTS `index_media_deleted_ts_date_taken_last_modified`")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_media_deleted_ts_date_sort_key` ON `media` (`deleted_ts`, `date_sort_key`)")
                createDateSortKeyTriggers(database)
            }
        }

        // Adds media.date_added and makes date_sort_key prefer it (see Medium.dateAdded's doc). The
        // date_sort_key triggers change too (new expression + they now also fire on UPDATE OF
        // date_added), so the old ones must be dropped and recreated - CREATE TRIGGER IF NOT EXISTS
        // alone would leave the stale definitions in place. Existing rows have no real date_added, so
        // they're backfilled from the current date_sort_key: their effective sort date is unchanged,
        // and only media synced *after* this upgrade (which reads the real MediaStore DATE_ADDED)
        // starts benefiting from the "newly added sorts to the top" behavior.
        private val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE media ADD COLUMN date_added INTEGER NOT NULL DEFAULT 0")
                database.execSQL("UPDATE media SET date_added = date_sort_key")
                database.execSQL("DROP TRIGGER IF EXISTS trg_media_date_sort_key_ins")
                database.execSQL("DROP TRIGGER IF EXISTS trg_media_date_sort_key_upd")
                createDateSortKeyTriggers(database)
            }
        }

        // Adds the (deleted_ts, parent_path) index backing getMediaFromPath - the per-folder query the
        // Explorer file list and drilled-into-album view now use so all tabs read the same media table
        // in the same order. Index name must match Room's auto-generated form exactly (see @Entity's
        // Index) or schema validation fails on next open.
        private val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_media_deleted_ts_parent_path` ON `media` (`deleted_ts`, `parent_path`)")
            }
        }

        // Adds the (deleted_ts, is_favorite) index backing the Favorites tab query (previously a full
        // table scan of the whole library on every load). Index name must match Room's auto-generated
        // form exactly (see the matching @Entity Index) or schema validation fails on next open.
        private val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_media_deleted_ts_is_favorite` ON `media` (`deleted_ts`, `is_favorite`)")
            }
        }

        private val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `compression_review_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `job_id` TEXT NOT NULL, `original_path` TEXT NOT NULL, `temp_result_path` TEXT NOT NULL, `original_size` INTEGER NOT NULL, `result_size` INTEGER NOT NULL, `media_type` INTEGER NOT NULL, `status` TEXT NOT NULL, `error_message` TEXT, `created_at` INTEGER NOT NULL)"
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_compression_review_items_job_id` ON `compression_review_items` (`job_id`)")
            }
        }

        private val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `file_hashes` (`full_path` TEXT NOT NULL, `size` INTEGER NOT NULL, `last_modified` INTEGER NOT NULL, `partial_hash` TEXT, `full_hash` TEXT, `phash` INTEGER, PRIMARY KEY(`full_path`))"
                )
            }
        }

        /**
         * Gives `parent_path` a NOCASE collation (see Medium.parentPath). Every DAO compares paths
         * with an explicit `COLLATE NOCASE`, which against this BINARY-collated column made
         * `index_media_deleted_ts_parent_path` unusable - EXPLAIN QUERY PLAN fell back to scanning
         * every live row, once per folder. Measured cause of a 356s cold-start CPU burn on a
         * 163k-item/~2.8k-folder library.
         *
         * A collation is part of the column definition, so SQLite has no ALTER for it and the table
         * has to be rebuilt. The CREATE below is verbatim Room's own expected schema for v25
         * (app/schemas/.../25.json, schema export enabled in this change) rather than a hand-rolled
         * reconstruction - a mismatch here would only surface as a runtime crash on a real device.
         *
         * Note this also drops the stray `DEFAULT 0` that `date_added`/`date_sort_key` carried from
         * their ALTER TABLE migrations; the entity never declared a defaultValue, so the rebuilt
         * table matches what Room expects rather than what had drifted into the DB. Both columns
         * are NOT NULL and are copied explicitly below, so no row loses a value.
         */
        private val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `media_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `filename` TEXT NOT NULL, `full_path` TEXT NOT NULL COLLATE NOCASE, `parent_path` TEXT NOT NULL COLLATE NOCASE, `last_modified` INTEGER NOT NULL, `date_taken` INTEGER NOT NULL, `size` INTEGER NOT NULL, `type` INTEGER NOT NULL, `video_duration` INTEGER NOT NULL, `is_favorite` INTEGER NOT NULL, `deleted_ts` INTEGER NOT NULL, `media_store_id` INTEGER NOT NULL, `rating` INTEGER NOT NULL, `date_added` INTEGER NOT NULL, `date_sort_key` INTEGER NOT NULL)"
                )
                database.execSQL(
                    "INSERT INTO `media_new` (`id`, `filename`, `full_path`, `parent_path`, `last_modified`, `date_taken`, `size`, `type`, `video_duration`, `is_favorite`, `deleted_ts`, `media_store_id`, `rating`, `date_added`, `date_sort_key`) " +
                        "SELECT `id`, `filename`, `full_path`, `parent_path`, `last_modified`, `date_taken`, `size`, `type`, `video_duration`, `is_favorite`, `deleted_ts`, `media_store_id`, `rating`, `date_added`, `date_sort_key` FROM `media`"
                )
                database.execSQL("DROP TABLE `media`")
                database.execSQL("ALTER TABLE `media_new` RENAME TO `media`")
                // Recreated after the rename - DROP TABLE took the old table's indices with it.
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_media_full_path` ON `media` (`full_path`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_media_deleted_ts_date_sort_key` ON `media` (`deleted_ts`, `date_sort_key`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_media_deleted_ts_size` ON `media` (`deleted_ts`, `size`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_media_deleted_ts_rating_last_modified` ON `media` (`deleted_ts`, `rating`, `last_modified`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_media_deleted_ts_parent_path` ON `media` (`deleted_ts`, `parent_path`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_media_deleted_ts_is_favorite` ON `media` (`deleted_ts`, `is_favorite`)")
                // DROP TABLE above took the date_sort_key triggers with it, not just the indices.
                // Recreate them or every row inserted after this upgrade keeps date_sort_key at its 0
                // default and sinks to the bottom of the date-sorted Media tab. (This line was missing
                // originally; MIGRATION_25_26 heals installs that already ran this without it.)
                createDateSortKeyTriggers(database)
            }
        }

        // MIGRATION_24_25's table rebuild dropped the date_sort_key triggers (SQLite drops a table's
        // triggers with DROP TABLE) but only recreated the indices, so every install that upgraded
        // through v25 lost them: newly synced media - a fresh screenshot, a download - got
        // date_sort_key = 0 and never appeared at the top of the date-sorted Media tab, even though
        // Albums (which keys off MAX(last_modified)) showed the folder immediately. Recreate the
        // triggers and rebuild date_sort_key for every existing row from its real date columns so the
        // rows written while the triggers were missing are corrected in place.
        private val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(database: SupportSQLiteDatabase) {
                createDateSortKeyTriggers(database)
                database.execSQL(
                    "UPDATE media SET date_sort_key = CASE WHEN date_added > 0 THEN date_added WHEN date_taken > 0 THEN date_taken ELSE last_modified END"
                )
            }
        }
    }
}
