package org.fossify.gallery.databases

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.fossify.gallery.interfaces.*
import org.fossify.gallery.models.*

@Database(entities = [Directory::class, Medium::class, Widget::class, DateTaken::class, Favorite::class, MediaCollection::class, MediaCache::class, BatchJobItem::class, MediaTag::class], version = 18)
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

    companion object {
        private var db: GalleryDatabase? = null

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
                            .fallbackToDestructiveMigrationFrom(1, 2, 3)
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
    }
}
