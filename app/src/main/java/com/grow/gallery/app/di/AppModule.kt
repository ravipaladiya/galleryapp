package com.grow.gallery.app.di

import android.content.Context
import androidx.room.Room
import com.grow.gallery.core.database.GalleryDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

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
        ).build()
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
