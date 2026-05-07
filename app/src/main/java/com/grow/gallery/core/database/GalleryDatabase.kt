package com.grow.gallery.core.database

import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(
    tableName = "trash_items",
    indices = [Index("expiresAt")],
)
data class TrashItem(
    @PrimaryKey val mediaId: Long,
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val size: Long,
    val originalPath: String,
    val deletedAt: Long,
    val expiresAt: Long,
    val thumbnailPath: String? = null,
)

@Entity(
    tableName = "vault_items",
    indices = [Index("addedAt")],
)
data class VaultItem(
    @PrimaryKey val mediaId: Long,
    val encryptedUri: String,
    val displayName: String,
    val mimeType: String,
    val addedAt: Long = System.currentTimeMillis(),
    val thumbnailPath: String? = null,
)

@Entity(tableName = "custom_albums")
data class CustomAlbum(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val coverMediaId: Long? = null,
)

@Entity(
    tableName = "album_media",
    primaryKeys = ["albumId", "mediaId"],
    foreignKeys = [ForeignKey(
        entity = CustomAlbum::class,
        parentColumns = ["id"],
        childColumns = ["albumId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("albumId")],
)
data class AlbumMediaCrossRef(
    val albumId: Long,
    val mediaId: Long,
    val addedAt: Long = System.currentTimeMillis(),
)

@Dao
interface TrashDao {
    @Query("SELECT * FROM trash_items ORDER BY deletedAt DESC")
    suspend fun getAllTrashItems(): List<TrashItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrashItem(item: TrashItem)

    @Delete
    suspend fun deleteTrashItem(item: TrashItem)

    @Query("DELETE FROM trash_items WHERE mediaId = :mediaId")
    suspend fun deleteById(mediaId: Long)

    @Query("DELETE FROM trash_items WHERE expiresAt < :now")
    suspend fun deleteExpired(now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM trash_items WHERE mediaId = :mediaId")
    suspend fun getById(mediaId: Long): TrashItem?

    @Query("DELETE FROM trash_items")
    suspend fun deleteAll()
}

@Dao
interface VaultDao {
    @Query("SELECT * FROM vault_items ORDER BY addedAt DESC")
    suspend fun getAllVaultItems(): List<VaultItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVaultItem(item: VaultItem)

    @Delete
    suspend fun deleteVaultItem(item: VaultItem)

    @Query("DELETE FROM vault_items WHERE mediaId = :mediaId")
    suspend fun deleteById(mediaId: Long)

    @Query("SELECT COUNT(*) FROM vault_items")
    suspend fun getCount(): Int
}

@Dao
interface AlbumDao {
    @Query("SELECT * FROM custom_albums ORDER BY createdAt DESC")
    suspend fun getAllAlbums(): List<CustomAlbum>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbum(album: CustomAlbum): Long

    @Delete
    suspend fun deleteAlbum(album: CustomAlbum)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addMediaToAlbum(ref: AlbumMediaCrossRef)

    @Query("DELETE FROM album_media WHERE albumId = :albumId AND mediaId = :mediaId")
    suspend fun removeMediaFromAlbum(albumId: Long, mediaId: Long)

    @Query("SELECT mediaId FROM album_media WHERE albumId = :albumId")
    suspend fun getMediaIdsForAlbum(albumId: Long): List<Long>

    @Query("SELECT COUNT(*) FROM album_media WHERE albumId = :albumId")
    suspend fun getMediaCount(albumId: Long): Int
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add expiresAt index to trash_items
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_trash_items_expiresAt` ON `trash_items` (`expiresAt`)")

        // Add addedAt index to vault_items
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_vault_items_addedAt` ON `vault_items` (`addedAt`)")

        // Recreate album_media with FK + albumId index
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `album_media_new` (
                `albumId` INTEGER NOT NULL,
                `mediaId` INTEGER NOT NULL,
                `addedAt` INTEGER NOT NULL,
                PRIMARY KEY(`albumId`, `mediaId`),
                FOREIGN KEY(`albumId`) REFERENCES `custom_albums`(`id`) ON DELETE CASCADE
            )"""
        )
        database.execSQL("INSERT INTO `album_media_new` SELECT * FROM `album_media`")
        database.execSQL("DROP TABLE `album_media`")
        database.execSQL("ALTER TABLE `album_media_new` RENAME TO `album_media`")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_album_media_albumId` ON `album_media` (`albumId`)")
    }
}

@Database(
    entities = [TrashItem::class, VaultItem::class, CustomAlbum::class, AlbumMediaCrossRef::class],
    version = 2,
    exportSchema = true,
)
abstract class GalleryDatabase : RoomDatabase() {
    abstract fun trashDao(): TrashDao
    abstract fun vaultDao(): VaultDao
    abstract fun albumDao(): AlbumDao
}
