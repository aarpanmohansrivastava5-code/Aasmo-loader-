package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        DownloadEntity::class,
        BookmarkEntity::class,
        HistoryEntity::class,
        SiteRuleEntity::class,
        FilterRuleEntity::class,
        ExtractionHistoryEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun historyDao(): HistoryDao
    abstract fun siteRuleDao(): SiteRuleDao
    abstract fun filterRuleDao(): FilterRuleDao
    abstract fun extractionHistoryDao(): ExtractionHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(db, "downloads", "originalUrl", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "downloads", "batchId", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "downloads", "retryCount", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "downloads", "maxRetries", "INTEGER NOT NULL DEFAULT 2")

                addColumnIfMissing(db, "bookmarks", "folderName", "TEXT NOT NULL DEFAULT 'General'")
                addColumnIfMissing(db, "bookmarks", "sortOrder", "INTEGER NOT NULL DEFAULT 0")

                addColumnIfMissing(db, "history", "domain", "TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(db, "downloads", "originalUrl", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "downloads", "batchId", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "downloads", "retryCount", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "downloads", "maxRetries", "INTEGER NOT NULL DEFAULT 2")
                addColumnIfMissing(db, "bookmarks", "folderName", "TEXT NOT NULL DEFAULT 'General'")
                addColumnIfMissing(db, "bookmarks", "sortOrder", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "history", "domain", "TEXT NOT NULL DEFAULT ''")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `site_rules` (
                        `host` TEXT NOT NULL,
                        `adShieldEnabled` INTEGER NOT NULL DEFAULT 1,
                        `allowListed` INTEGER NOT NULL DEFAULT 0,
                        `desktopMode` INTEGER NOT NULL DEFAULT 0,
                        `allowJavascript` INTEGER NOT NULL DEFAULT 1,
                        `allowLocation` INTEGER NOT NULL DEFAULT 0,
                        `allowCamera` INTEGER NOT NULL DEFAULT 0,
                        `allowMicrophone` INTEGER NOT NULL DEFAULT 0,
                        `allowPopups` INTEGER NOT NULL DEFAULT 0,
                        `updatedAt` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`host`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `filter_rules` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `pattern` TEXT NOT NULL,
                        `isAllowRule` INTEGER NOT NULL DEFAULT 0,
                        `category` TEXT NOT NULL DEFAULT 'CUSTOM',
                        `enabled` INTEGER NOT NULL DEFAULT 1
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `extraction_history` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `inputQuery` TEXT NOT NULL,
                        `resolvedUrl` TEXT NOT NULL,
                        `platformId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `itemCount` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
            }
        }

        private fun addColumnIfMissing(
            db: SupportSQLiteDatabase,
            table: String,
            column: String,
            definition: String,
        ) {
            val cursor = db.query("PRAGMA table_info(`$table`)")
            val exists = cursor.use {
                val nameIdx = it.getColumnIndex("name")
                var found = false
                while (it.moveToNext()) {
                    if (nameIdx >= 0 && it.getString(nameIdx).equals(column, ignoreCase = true)) {
                        found = true
                        break
                    }
                }
                found
            }
            if (!exists) {
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `$column` $definition")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "aasmo_loader.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
