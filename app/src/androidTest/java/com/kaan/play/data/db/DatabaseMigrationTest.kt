package com.kaan.play.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kaan.play.data.model.MediaType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** v6 -> v7 Room göçünün mevcut kullanıcı verilerini (geçmiş, favoriler, eklentiler, altyazı tercihi) koruduğunu doğrular. */
@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    private val dbName = "migration-test-6-7.db"
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun migrate6To7PreservesUserDataAndSupportsLiveChannelTables() = runBlocking {
        // Adım 1: Gerçek v6 şemasıyla veritabanı kur ve örnek kullanıcı verisi yaz.
        val helper = object : SQLiteOpenHelper(context, dbName, null, 6) {
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `addons` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`type` TEXT NOT NULL, `sourceUrl` TEXT NOT NULL, `addonId` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                        "`version` TEXT, `description` TEXT, `logoUrl` TEXT, `enabled` INTEGER NOT NULL, " +
                        "`manifestJson` TEXT NOT NULL, `addedAt` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_addons_sourceUrl` ON `addons` (`sourceUrl`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `plugins` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`repoId` INTEGER NOT NULL, `pluginId` TEXT NOT NULL, `name` TEXT NOT NULL, `version` TEXT, " +
                        "`description` TEXT, `filename` TEXT NOT NULL, `scriptUrl` TEXT NOT NULL, `supportedTypes` TEXT NOT NULL, " +
                        "`contentLanguage` TEXT NOT NULL, `hasSettings` INTEGER NOT NULL, `sha256` TEXT NOT NULL, " +
                        "`sizeBytes` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, `installedAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`repoId`) REFERENCES `addons`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_plugins_repoId` ON `plugins` (`repoId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_plugins_repoId_pluginId` ON `plugins` (`repoId`, `pluginId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `watch_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`tmdbId` INTEGER NOT NULL, `type` TEXT NOT NULL, `userId` TEXT NOT NULL, `profileId` TEXT NOT NULL, " +
                        "`season` INTEGER NOT NULL, `episode` INTEGER NOT NULL, `positionMs` INTEGER NOT NULL, " +
                        "`durationMs` INTEGER NOT NULL, `lastWatchedAt` INTEGER NOT NULL, `title` TEXT NOT NULL, " +
                        "`posterPath` TEXT, `pluginId` TEXT, `dataUrl` TEXT, `contentUrl` TEXT)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_watch_history_userId_profileId_tmdbId_type_season_episode` " +
                        "ON `watch_history` (`userId`, `profileId`, `tmdbId`, `type`, `season`, `episode`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_watch_history_userId_profileId_lastWatchedAt` " +
                        "ON `watch_history` (`userId`, `profileId`, `lastWatchedAt`)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `favorites` (`tmdbId` INTEGER NOT NULL, `type` TEXT NOT NULL, " +
                        "`userId` TEXT NOT NULL, `profileId` TEXT NOT NULL, `title` TEXT NOT NULL, `posterPath` TEXT, " +
                        "`addedAt` INTEGER NOT NULL, PRIMARY KEY(`userId`, `profileId`, `tmdbId`, `type`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `subtitle_language` (`contentKey` TEXT NOT NULL, `language` TEXT NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`contentKey`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `subtitle_offset` (`contentKey` TEXT NOT NULL, `subtitleKey` TEXT NOT NULL, " +
                        "`offsetMs` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`contentKey`, `subtitleKey`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_subtitle_offset_updatedAt` ON `subtitle_offset` (`updatedAt`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `downloads` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`tmdbId` INTEGER NOT NULL, `imdbId` TEXT, `type` TEXT NOT NULL, `season` INTEGER NOT NULL, " +
                        "`episode` INTEGER NOT NULL, `title` TEXT NOT NULL, `posterPath` TEXT, `sourceName` TEXT NOT NULL, " +
                        "`sourceUrl` TEXT NOT NULL, `headersJson` TEXT NOT NULL, `status` TEXT NOT NULL, " +
                        "`downloadedBytes` INTEGER NOT NULL, `totalBytes` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
                        "`subtitlesJson` TEXT NOT NULL)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_downloads_tmdbId_type_season_episode` " +
                        "ON `downloads` (`tmdbId`, `type`, `season`, `episode`)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_downloads_createdAt` ON `downloads` (`createdAt`)")
                // Room'ün v6 kimlik karması: Room open helper sürüm bilgisini user_version = 6 olarak saklar.
            }

            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }

        val v6Db = helper.writableDatabase
        v6Db.execSQL(
            "INSERT INTO `addons` (`id`,`type`,`sourceUrl`,`addonId`,`name`,`version`,`description`,`logoUrl`,`enabled`,`manifestJson`,`addedAt`) " +
                "VALUES (1,'NUVIO_ADDON','https://example.invalid/m.json','tr.live','TR Canli','1.0',NULL,NULL,1,'{}',1000)",
        )
        v6Db.execSQL(
            "INSERT INTO `watch_history` (`tmdbId`,`type`,`userId`,`profileId`,`season`,`episode`,`positionMs`,`durationMs`,`lastWatchedAt`,`title`,`posterPath`,`pluginId`,`dataUrl`,`contentUrl`) " +
                "VALUES (42,'Movie','legacy-user','legacy-profile',0,0,12000,90000,5555,'Korunan Film',NULL,NULL,NULL,NULL)",
        )
        v6Db.execSQL(
            "INSERT INTO `favorites` (`tmdbId`,`type`,`userId`,`profileId`,`title`,`posterPath`,`addedAt`) " +
                "VALUES (42,'Movie','legacy-user','legacy-profile','Korunan Favori',NULL,5556)",
        )
        v6Db.execSQL("INSERT INTO `subtitle_language` (`contentKey`,`language`,`updatedAt`) VALUES ('movie:42','tr',5557)")
        v6Db.close()
        helper.close()

        // Adım 2: Room v7 olarak MIGRATION_6_7 ile aç — Room şemayı doğrular; hata verirse başarısız olur.
        val migrated = Room.databaseBuilder(context, PlayDatabase::class.java, dbName)
            .addMigrations(PlayDatabase.MIGRATION_6_7)
            .allowMainThreadQueries()
            .build()

        try {
            val history = migrated.watchHistoryDao().observeHistory().first()
            assertEquals(1, history.size)
            assertEquals("Korunan Film", history.single().title)
            assertEquals(12_000L, history.single().positionMs)

            val favorites = migrated.favoriteDao().observeAll().first()
            assertEquals(1, favorites.size)
            assertEquals(42, favorites.single().tmdbId)
            assertEquals(MediaType.Movie, favorites.single().type)

            val addons = migrated.addonDao().observeOfType(com.kaan.play.data.db.AddonType.NUVIO_ADDON).first()
            assertEquals(1, addons.size)
            assertEquals("TR Canli", addons.single().name)

            assertEquals("tr", migrated.subtitleDao().language("movie:42"))

            // Yeni tablolar okunup yazılabilmeli
            migrated.liveChannelDao().markWatched("trt1", 99_000L)
            migrated.liveChannelDao().setFavorite("trt1", true)
            migrated.liveChannelDao().replaceOrders(listOf(LiveChannelOrderEntity("trt1", 0)))

            val activity = migrated.liveChannelDao().observeActivity().first().single()
            assertTrue(activity.isFavorite)
            assertEquals(99_000L, activity.lastWatchedAt)
            assertEquals(0, migrated.liveChannelDao().allOrders().single().sortIndex)
        } finally {
            migrated.close()
        }
    }
}
