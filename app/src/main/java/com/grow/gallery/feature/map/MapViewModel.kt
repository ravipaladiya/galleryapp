package com.grow.gallery.feature.map

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grow.gallery.core.media.MediaItem
import com.grow.gallery.core.media.MediaQuery
import com.grow.gallery.core.media.MediaRepository
import com.grow.gallery.core.media.SortOrder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class GeoMediaItem(
    val item: MediaItem,
    val latitude: Double,
    val longitude: Double,
)

data class MapUiState(
    val geoItems: List<GeoMediaItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val selectedItem: GeoMediaItem? = null,
)

@HiltViewModel
class MapViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    init { loadGeoMedia() }

    fun refresh() = loadGeoMedia()

    fun selectItem(item: GeoMediaItem?) {
        _uiState.update { it.copy(selectedItem = item) }
    }

    private fun loadGeoMedia() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val groups = mediaRepository.loadMedia(MediaQuery(sortOrder = SortOrder.NEWEST))
                val allItems = groups.flatMap { it.items }.filter { !it.mimeType.startsWith("video/") }

                val geoItems = withContext(Dispatchers.IO) {
                    allItems.mapNotNull { item -> readExifLocation(item) }
                }

                _uiState.update { it.copy(geoItems = geoItems, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Failed to load photo locations") }
            }
        }
    }

    private fun readExifLocation(item: MediaItem): GeoMediaItem? {
        return try {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.setRequireOriginal(item.uri)
            } else {
                item.uri
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val latLng = exif.latLong ?: return null
                val lat = latLng[0]
                val lng = latLng[1]
                // Skip items with no real location (0,0 is null-island off Africa)
                if (lat == 0.0 && lng == 0.0) return null
                GeoMediaItem(item = item, latitude = lat, longitude = lng)
            }
        } catch (_: Exception) {
            null
        }
    }
}
