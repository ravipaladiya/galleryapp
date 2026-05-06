package com.grow.gallery.core.media

import android.app.PendingIntent
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
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

    /**
     * Emits Unit whenever the device's image or video collection changes.
     * Collect this in a ViewModel and debounce to drive reactive reloads.
     */
    fun observeMediaChanges(): Flow<Unit> = callbackFlow {
        val handler = Handler(Looper.getMainLooper())
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        contentResolver.registerContentObserver(imageCollection, true, observer)
        contentResolver.registerContentObserver(videoCollection, true, observer)
        awaitClose { contentResolver.unregisterContentObserver(observer) }
    }.flowOn(Dispatchers.IO)

    suspend fun loadMedia(query: MediaQuery): List<MediaGroup> = withContext(Dispatchers.IO) {
        val items = mutableListOf<MediaItem>()
        if (query.filter != MediaFilter.VIDEOS) items.addAll(queryImages(query))
        if (query.filter != MediaFilter.PHOTOS) items.addAll(queryVideos(query))

        val sorted = when (query.sortOrder) {
            SortOrder.NEWEST -> items.sortedByDescending { it.dateTaken ?: it.dateAdded }
            SortOrder.OLDEST -> items.sortedBy { it.dateTaken ?: it.dateAdded }
            SortOrder.SIZE_DESC -> items.sortedByDescending { it.size }
            SortOrder.SIZE_ASC -> items.sortedBy { it.size }
        }

        val filtered = if (query.filter == MediaFilter.FAVORITES) sorted.filter { it.isFavorite }
        else sorted

        groupByDate(filtered)
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
                        val cal = java.util.Calendar.getInstance().apply { timeInMillis = ts }
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
        val cal = java.util.Calendar.getInstance().apply {
            timeInMillis = timeMs
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
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

        val results = mutableListOf<MediaItem>()
        contentResolver.query(
            imageCollection, projection,
            buildSelection(query, isVideo = false),
            buildSelectionArgs(query),
            buildSortOrder(query),
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

        val results = mutableListOf<MediaItem>()
        contentResolver.query(
            videoCollection, projection,
            buildSelection(query, isVideo = true),
            buildSelectionArgs(query),
            buildSortOrder(query),
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
                val current = buckets[bucketId]
                if (current == null) {
                    val mediaId = cursor.getLong(idCol)
                    val coverUri = ContentUris.withAppendedId(imageCollection, mediaId)
                    buckets[bucketId] = Triple(bucketName, coverUri, 1)
                } else {
                    buckets[bucketId] = Triple(current.first, current.second, current.third + 1)
                }
            }
        }

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

    /**
     * On Android R+ (API 30+): returns a PendingIntent the screen must launch via
     * StartIntentSenderForResult — the system shows a confirmation dialog.
     * On older devices: deletes directly and returns null.
     */
    suspend fun prepareDelete(items: List<MediaItem>): PendingIntent? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaStore.createDeleteRequest(contentResolver, items.map { it.uri })
        } else {
            items.forEach { contentResolver.delete(it.uri, null, null) }
            null
        }
    }

    /** Legacy direct delete — only valid on pre-R devices. */
    suspend fun deleteMedia(items: List<MediaItem>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            items.forEach { contentResolver.delete(it.uri, null, null) }
            Result.success(Unit)
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

    suspend fun loadMediaByIds(ids: Set<Long>): List<MediaItem> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyList()
        val allImages = queryImages(MediaQuery())
        val allVideos = queryVideos(MediaQuery())
        (allImages + allVideos)
            .filter { it.id in ids }
            .sortedByDescending { it.dateTaken ?: it.dateAdded }
    }

    data class StorageByType(
        val photoBytes: Long,
        val videoBytes: Long,
    )

    suspend fun computeStorageByType(): StorageByType = withContext(Dispatchers.IO) {
        val images = queryImages(MediaQuery())
        val videos = queryVideos(MediaQuery())
        StorageByType(
            photoBytes = images.sumOf { it.size },
            videoBytes = videos.sumOf { it.size },
        )
    }

    data class CleanerCategories(
        val screenshots: List<MediaItem>,
        val burstPhotos: List<MediaItem>,
        val largeMedia: List<MediaItem>,
    )

    suspend fun getCleanerCategories(): CleanerCategories = withContext(Dispatchers.IO) {
        val allImages = queryImages(MediaQuery())
        val allVideos = queryVideos(MediaQuery())
        val allItems = allImages + allVideos

        val screenshots = allItems.filter {
            it.displayName.lowercase().contains("screenshot") ||
                    it.bucketName.lowercase().contains("screenshot")
        }

        // Detect burst/duplicate shots: same bucket, within 3 seconds of another photo
        val bursts = mutableListOf<MediaItem>()
        val sortedImages = allImages.sortedBy { it.dateTaken ?: it.dateAdded }
        for (i in 1 until sortedImages.size) {
            val prev = sortedImages[i - 1]
            val curr = sortedImages[i]
            val prevTs = prev.dateTaken ?: prev.dateAdded
            val currTs = curr.dateTaken ?: curr.dateAdded
            if (curr.bucketId == prev.bucketId && (currTs - prevTs).let { it in 0..3 }) {
                if (!bursts.contains(curr)) bursts.add(curr)
            }
        }

        val largeMedia = allItems.filter { it.size > 50_000_000L }

        CleanerCategories(
            screenshots = screenshots,
            burstPhotos = bursts,
            largeMedia = largeMedia,
        )
    }

    private fun buildSelection(query: MediaQuery, isVideo: Boolean): String? {
        val parts = mutableListOf<String>()
        if (query.albumId != null) {
            val col = if (isVideo) MediaStore.Video.Media.BUCKET_ID else MediaStore.Images.Media.BUCKET_ID
            parts.add("$col = ${query.albumId}")
        }
        if (query.searchQuery.isNotBlank()) {
            val col = if (isVideo) MediaStore.Video.Media.DISPLAY_NAME else MediaStore.Images.Media.DISPLAY_NAME
            parts.add("$col LIKE ?")
        }
        if (query.hideScreenshots && !isVideo) {
            val nameCol = MediaStore.Images.Media.DISPLAY_NAME
            val bucketCol = MediaStore.Images.Media.BUCKET_DISPLAY_NAME
            parts.add("(LOWER($nameCol) NOT LIKE 'screenshot%' AND LOWER($bucketCol) NOT LIKE '%screenshot%')")
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" AND ")
    }

    private fun buildSelectionArgs(query: MediaQuery): Array<String>? =
        if (query.searchQuery.isNotBlank()) arrayOf("%${query.searchQuery}%") else null

    private fun buildSortOrder(query: MediaQuery): String = when (query.sortOrder) {
        SortOrder.NEWEST -> "${MediaStore.MediaColumns.DATE_ADDED} DESC"
        SortOrder.OLDEST -> "${MediaStore.MediaColumns.DATE_ADDED} ASC"
        SortOrder.SIZE_DESC -> "${MediaStore.MediaColumns.SIZE} DESC"
        SortOrder.SIZE_ASC -> "${MediaStore.MediaColumns.SIZE} ASC"
    }
}
