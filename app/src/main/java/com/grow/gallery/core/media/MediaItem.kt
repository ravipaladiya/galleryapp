package com.grow.gallery.core.media

import android.net.Uri
import kotlinx.serialization.Serializable

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val thumbnailUri: Uri? = null,
    val displayName: String,
    val mimeType: String,
    val size: Long,
    val width: Int = 0,
    val height: Int = 0,
    val dateAdded: Long,
    val dateTaken: Long? = null,
    val dateModified: Long,
    val bucketId: Long = 0,
    val bucketName: String = "",
    val duration: Long? = null,
    val isFavorite: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val orientation: Int = 0,
    val isInTrash: Boolean = false,
    val trashExpiry: Long? = null,
) {
    val isVideo: Boolean get() = mimeType.startsWith("video/")
    val isPhoto: Boolean get() = mimeType.startsWith("image/")

    val formattedSize: String get() {
        return when {
            size >= 1_000_000_000 -> "%.1f GB".format(size / 1_000_000_000.0)
            size >= 1_000_000 -> "%.1f MB".format(size / 1_000_000.0)
            size >= 1_000 -> "%.0f KB".format(size / 1_000.0)
            else -> "$size B"
        }
    }

    val resolution: String get() = if (width > 0 && height > 0) "${width}×${height}" else ""
}

data class MediaGroup(
    val label: String,
    val items: List<MediaItem>,
)

data class Album(
    val id: Long,
    val name: String,
    val coverUri: Uri?,
    val mediaCount: Int,
    val isSystemAlbum: Boolean = true,
    val bucketId: Long = 0,
)

enum class SortOrder { NEWEST, OLDEST, SIZE_DESC, SIZE_ASC }
enum class MediaFilter { ALL, PHOTOS, VIDEOS, FAVORITES }

data class MediaQuery(
    val albumId: Long? = null,
    val sortOrder: SortOrder = SortOrder.NEWEST,
    val filter: MediaFilter = MediaFilter.ALL,
    val searchQuery: String = "",
)
