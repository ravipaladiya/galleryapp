package com.grow.gallery.app.di

import android.content.Context
import androidx.room.Room
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
        ).addMigrations(MIGRATION_1_2).build()
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
