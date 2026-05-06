package com.grow.gallery.feature.slideshow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grow.gallery.core.common.DataStoreManager
import com.grow.gallery.core.media.MediaItem
import com.grow.gallery.core.media.MediaQuery
import com.grow.gallery.core.media.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SlideshowUiState(
    val items: List<MediaItem> = emptyList(),
    val intervalMs: Long = 3000L,
    val isLoading: Boolean = true,
)

@HiltViewModel
class SlideshowViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val dataStoreManager: DataStoreManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SlideshowUiState())
    val uiState: StateFlow<SlideshowUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            dataStoreManager.slideshowSpeed.collect { seconds ->
                _uiState.update { it.copy(intervalMs = seconds.coerceAtLeast(1) * 1000L) }
            }
        }
    }

    fun loadSlideshow(albumId: Long, mediaIds: List<Long> = emptyList()) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val items: List<MediaItem> = if (mediaIds.isNotEmpty()) {
                // Memory-group slideshow: load the exact set of items by id
                val query = MediaQuery()
                val all = mediaRepository.loadMedia(query).flatMap { it.items }
                val idSet = mediaIds.toHashSet()
                // Preserve the original memory ordering
                val idOrder = mediaIds.withIndex().associate { (idx, id) -> id to idx }
                all.filter { it.id in idSet }
                    .filter { it.isPhoto }
                    .sortedBy { idOrder[it.id] ?: Int.MAX_VALUE }
            } else {
                val query = if (albumId > 0) MediaQuery(albumId = albumId) else MediaQuery()
                mediaRepository.loadMedia(query).flatMap { it.items }.filter { it.isPhoto }.shuffled()
            }
            _uiState.update { it.copy(items = items, isLoading = false) }
        }
    }
}
