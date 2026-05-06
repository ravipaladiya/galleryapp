package com.grow.gallery.core.media

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val contentResolver: ContentResolver get() = context.contentResolver

    private val imageCollection: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }

    private val videoCollection: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    }

    fun observeMedia(query: MediaQuery): Flow<List<MediaGroup>> = flow {
        emit(loadMedia(query))
    }.flowOn(Dispatchers.IO)

    suspend fun loadMedia(query: MediaQuery): List<MediaGroup> = withContext(Dispatchers.IO) {
        val items = mutableListOf<MediaItem>()
        if (query.filter != MediaFilter.VIDEOS) {
            items.addAll(queryImages(query))
        }
        if (query.filter != MediaFilter.PHOTOS) {
            items.addAll(queryVideos(query))
        }
        val sorted = when (query.sortOrder) {
            SortOrder.NEWEST -> items.sortedByDescending { it.dateTaken ?: it.dateAdded }
            SortOrder.OLDEST -> items.sortedBy { it.dateTaken ?: it.dateAdded }
            SortOrder.SIZE_DESC -> items.sortedByDescending { it.size }
            SortOrder.SIZE_ASC -> items.sortedBy { it.size }
        }
        if (query.filter == MediaFilter.FAVORITES) {
            return@withContext groupByDate(sorted.filter { it.isFavorite })
        }
        groupByDate(sorted)
    }

    private fun groupByDate(items: List<MediaItem>): List<MediaGroup> {
        val now = System.currentTimeMillis()
        val today = startOfDay(now)
        val yesterday = today - 86_400_000L
        return items
            .groupBy { item ->
                val ts = (item.dateTaken ?: item.dateAdded) * 1000
                when {
                    ts >= today -> "Today"
                    ts >= yesterday -> "Yesterday"
                    else -> {
                        val cal = java.util.Calendar.getInstance()
                        cal.timeInMillis = ts
                        val month = cal.getDisplayName(
                            java.util.Calendar.MONTH,
                            java.util.Calendar.LONG,
                            java.util.Locale.getDefault(),
                        ) ?: ""
                        "$month ${cal.get(java.util.Calendar.YEAR)}"
                    }
                }
            }
            .map { (label, group) -> MediaGroup(label, group) }
    }

    private fun startOfDay(timeMs: Long): Long {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = timeMs
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun queryImages(query: MediaQuery): List<MediaItem> {
        val projection = buildList {
            add(MediaStore.Images.Media._ID)
            add(MediaStore.Images.Media.DISPLAY_NAME)
            add(MediaStore.Images.Media.MIME_TYPE)
            add(MediaStore.Images.Media.SIZE)
            add(MediaStore.Images.Media.WIDTH)
            add(MediaStore.Images.Media.HEIGHT)
            add(MediaStore.Images.Media.DATE_ADDED)
            add(MediaStore.Images.Media.DATE_MODIFIED)
            add(MediaStore.Images.Media.BUCKET_ID)
            add(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            add(MediaStore.Images.Media.ORIENTATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Images.Media.DATE_TAKEN)
                add(MediaStore.Images.Media.IS_FAVORITE)
            }
        }.toTypedArray()

        val selection = buildSelection(query, isVideo = false)
        val selectionArgs = buildSelectionArgs(query)
        val sortOrder = buildSortOrder(query)

        val results = mutableListOf<MediaItem>()
        contentResolver.query(
            imageCollection, projection, selection, selectionArgs, sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val dateTakenCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN) else -1
            val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val isFavCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                cursor.getColumnIndex(MediaStore.Images.Media.IS_FAVORITE) else -1

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(imageCollection, id)
                results.add(
                    MediaItem(
                        id = id,
                        uri = uri,
                        displayName = cursor.getString(nameCol) ?: "",
                        mimeType = cursor.getString(mimeCol) ?: "image/jpeg",
                        size = cursor.getLong(sizeCol),
                        width = cursor.getInt(widthCol),
                        height = cursor.getInt(heightCol),
                        dateAdded = cursor.getLong(dateAddedCol),
                        dateTaken = if (dateTakenCol >= 0) cursor.getLong(dateTakenCol) / 1000 else null,
                        dateModified = cursor.getLong(dateAddedCol),
                        bucketId = cursor.getLong(bucketIdCol),
                        bucketName = cursor.getString(bucketNameCol) ?: "",
                        isFavorite = isFavCol >= 0 && cursor.getInt(isFavCol) == 1,
                    )
                )
            }
        }
        return results
    }

    private fun queryVideos(query: MediaQuery): List<MediaItem> {
        val projection = buildList {
            add(MediaStore.Video.Media._ID)
            add(MediaStore.Video.Media.DISPLAY_NAME)
            add(MediaStore.Video.Media.MIME_TYPE)
            add(MediaStore.Video.Media.SIZE)
            add(MediaStore.Video.Media.WIDTH)
            add(MediaStore.Video.Media.HEIGHT)
            add(MediaStore.Video.Media.DATE_ADDED)
            add(MediaStore.Video.Media.DATE_MODIFIED)
            add(MediaStore.Video.Media.DURATION)
            add(MediaStore.Video.Media.BUCKET_ID)
            add(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Video.Media.DATE_TAKEN)
                add(MediaStore.Video.Media.IS_FAVORITE)
            }
        }.toTypedArray()

        val selection = buildSelection(query, isVideo = true)
        val selectionArgs = buildSelectionArgs(query)
        val sortOrder = buildSortOrder(query)

        val results = mutableListOf<MediaItem>()
        contentResolver.query(
            videoCollection, projection, selection, selectionArgs, sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val dateTakenCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                cursor.getColumnIndex(MediaStore.Video.Media.DATE_TAKEN) else -1
            val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
            val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            val isFavCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                cursor.getColumnIndex(MediaStore.Video.Media.IS_FAVORITE) else -1

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(videoCollection, id)
                results.add(
                    MediaItem(
                        id = id,
                        uri = uri,
                        displayName = cursor.getString(nameCol) ?: "",
                        mimeType = cursor.getString(mimeCol) ?: "video/mp4",
                        size = cursor.getLong(sizeCol),
                        width = cursor.getInt(widthCol),
                        height = cursor.getInt(heightCol),
                        dateAdded = cursor.getLong(dateAddedCol),
                        dateTaken = if (dateTakenCol >= 0) cursor.getLong(dateTakenCol) / 1000 else null,
                        dateModified = cursor.getLong(dateAddedCol),
                        duration = cursor.getLong(durationCol),
                        bucketId = cursor.getLong(bucketIdCol),
                        bucketName = cursor.getString(bucketNameCol) ?: "",
                        isFavorite = isFavCol >= 0 && cursor.getInt(isFavCol) == 1,
                    )
                )
            }
        }
        return results
    }

    suspend fun loadAlbums(): List<Album> = withContext(Dispatchers.IO) {
        val buckets = mutableMapOf<Long, Triple<String, Uri?, Int>>()

        // Images
        val imgProjection = arrayOf(
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media._ID,
        )
        contentResolver.query(
            imageCollection, imgProjection, null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                val bucketId = cursor.getLong(bucketIdCol)
                val bucketName = cursor.getString(bucketNameCol) ?: "Unknown"
                if (!buckets.containsKey(bucketId)) {
                    val mediaId = cursor.getLong(idCol)
                    val coverUri = ContentUris.withAppendedId(imageCollection, mediaId)
                    buckets[bucketId] = Triple(bucketName, coverUri, 0)
                }
                val current = buckets[bucketId]!!
                buckets[bucketId] = Triple(current.first, current.second, current.third + 1)
            }
        }

        // Videos
        val vidProjection = arrayOf(
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media._ID,
        )
        contentResolver.query(
            videoCollection, vidProjection, null, null, "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
            val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            while (cursor.moveToNext()) {
                val bucketId = cursor.getLong(bucketIdCol)
                val bucketName = cursor.getString(bucketNameCol) ?: "Unknown"
                val current = buckets[bucketId]
                if (current == null) {
                    val mediaId = cursor.getLong(idCol)
                    val coverUri = ContentUris.withAppendedId(videoCollection, mediaId)
                    buckets[bucketId] = Triple(bucketName, coverUri, 1)
                } else {
                    buckets[bucketId] = Triple(current.first, current.second, current.third + 1)
                }
            }
        }

        buckets.map { (id, data) ->
            Album(
                id = id,
                name = data.first,
                coverUri = data.second,
                mediaCount = data.third,
                bucketId = id,
            )
        }.sortedByDescending { it.mediaCount }
    }

    suspend fun deleteMedia(items: List<MediaItem>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val uris = items.map { it.uri }
                // On API 30+, deletion creates a pending intent that must be confirmed by user
                // We return success here; actual deletion is handled via activity result
                Result.success(Unit)
            } else {
                items.forEach { item ->
                    contentResolver.delete(item.uri, null, null)
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setFavorite(item: MediaItem, favorite: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_FAVORITE, if (favorite) 1 else 0)
                }
                contentResolver.update(item.uri, values, null, null)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        } else {
            Result.failure(UnsupportedOperationException("Favorites not supported on this Android version"))
        }
    }

    suspend fun getMediaById(id: Long, isVideo: Boolean): MediaItem? = withContext(Dispatchers.IO) {
        val query = MediaQuery()
        val items = if (isVideo) queryVideos(query) else queryImages(query)
        items.firstOrNull { it.id == id }
    }

    suspend fun searchMedia(searchQuery: String): List<MediaItem> = withContext(Dispatchers.IO) {
        if (searchQuery.isBlank()) return@withContext emptyList()
        val query = MediaQuery(searchQuery = searchQuery)
        val images = queryImages(query)
        val videos = queryVideos(query)
        (images + videos).sortedByDescending { it.dateTaken ?: it.dateAdded }
    }

    private fun buildSelection(query: MediaQuery, isVideo: Boolean): String? {
        val parts = mutableListOf<String>()
        if (query.albumId != null) {
            val bucketCol = if (isVideo) MediaStore.Video.Media.BUCKET_ID else MediaStore.Images.Media.BUCKET_ID
            parts.add("$bucketCol = ${query.albumId}")
        }
        if (query.searchQuery.isNotBlank()) {
            val nameCol = if (isVideo) MediaStore.Video.Media.DISPLAY_NAME else MediaStore.Images.Media.DISPLAY_NAME
            parts.add("$nameCol LIKE ?")
        }
        return if (parts.isEmpty()) null else parts.joinToString(" AND ")
    }

    private fun buildSelectionArgs(query: MediaQuery): Array<String>? {
        return if (query.searchQuery.isNotBlank()) {
            arrayOf("%${query.searchQuery}%")
        } else null
    }

    private fun buildSortOrder(query: MediaQuery): String {
        return when (query.sortOrder) {
            SortOrder.NEWEST -> "${MediaStore.MediaColumns.DATE_ADDED} DESC"
            SortOrder.OLDEST -> "${MediaStore.MediaColumns.DATE_ADDED} ASC"
            SortOrder.SIZE_DESC -> "${MediaStore.MediaColumns.SIZE} DESC"
            SortOrder.SIZE_ASC -> "${MediaStore.MediaColumns.SIZE} ASC"
        }
    }
}
