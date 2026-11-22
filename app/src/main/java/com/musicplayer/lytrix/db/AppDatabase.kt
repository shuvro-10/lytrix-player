package com.musicplayer.lytrix.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PlaytimeEntity::class,
        FavoriteSongEntity::class,
        SongMetadataEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class,
        HiddenSongEntity::class,
        WhitelistSongEntity::class,
        PodcastEntity::class,
        VideoHistoryEntity::class,
        AudioHistoryEntity::class,
        FeedbackEntity::class,
        OnlineHistoryEntity::class
    ],
    version = 14,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playtimeDao(): PlaytimeDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun songMetadataDao(): SongMetadataDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun hiddenSongDao(): HiddenSongDao
    abstract fun whitelistSongDao(): WhitelistSongDao
    abstract fun podcastDao(): PodcastDao
    abstract fun videoHistoryDao(): VideoHistoryDao
    abstract fun audioHistoryDao(): AudioHistoryDao
    abstract fun feedbackDao(): FeedbackDao
    abstract fun onlineHistoryDao(): OnlineHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE song_metadata ADD COLUMN favoriteOrder INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `downloaded_podcasts` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `publisher` TEXT NOT NULL, `audioUrl` TEXT NOT NULL, `image` TEXT NOT NULL, `durationMs` INTEGER NOT NULL, `downloadPath` TEXT NOT NULL, `language` TEXT NOT NULL, `isDownloaded` INTEGER NOT NULL, `addedTime` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `video_history` (`uriString` TEXT NOT NULL, `title` TEXT NOT NULL, `duration` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`uriString`))"
                )
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `audio_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `songId` INTEGER NOT NULL, `title` TEXT NOT NULL, `artist` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)"
                )
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `feedback_history` (`id` TEXT NOT NULL, `category` TEXT NOT NULL, `details` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `status` TEXT NOT NULL, PRIMARY KEY(`id`))"
                )
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `online_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `songId` TEXT NOT NULL, `title` TEXT NOT NULL, `artist` TEXT NOT NULL, `imageUrl` TEXT NOT NULL, `source` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `duration` INTEGER NOT NULL DEFAULT 0, `uriString` TEXT NOT NULL DEFAULT '')"
                )
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE audio_history ADD COLUMN durationMs INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "music_app_database"
                )
                    .addMigrations(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
