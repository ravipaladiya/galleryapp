package com.grow.gallery.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.grow.gallery.core.database.GalleryDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// Adds indexes on hot query columns: trash expiry sweep and vault sort-by-date.
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS index_trash_items_expiresAt ON trash_items(expiresAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_vault_items_addedAt ON vault_items(addedAt)")
    }
}

// Recreates album_media with FK (albumId → custom_albums.id, CASCADE DELETE) and an index.
private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS album_media")
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `album_media` (
                `albumId` INTEGER NOT NULL,
                `mediaId` INTEGER NOT NULL,
                `addedAt` INTEGER NOT NULL,
                PRIMARY KEY(`albumId`, `mediaId`),
                FOREIGN KEY(`albumId`) REFERENCES `custom_albums`(`id`) ON DELETE CASCADE
            )"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_album_media_albumId ON album_media(albumId)")
    }
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGalleryDatabase(@ApplicationContext context: Context): GalleryDatabase {
        return Room.databaseBuilder(
            context,
            GalleryDatabase::class.java,
            "gallery_database",
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    db.execSQL("PRAGMA foreign_keys = ON")
                }
            })
            .build()
    }

    @Provides
    @Singleton
    fun provideTrashDao(db: GalleryDatabase) = db.trashDao()

    @Provides
    @Singleton
    fun provideVaultDao(db: GalleryDatabase) = db.vaultDao()

    @Provides
    @Singleton
    fun provideAlbumDao(db: GalleryDatabase) = db.albumDao()
}
