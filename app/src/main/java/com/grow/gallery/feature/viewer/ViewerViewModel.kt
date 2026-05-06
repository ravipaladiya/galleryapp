package com.grow.gallery.feature.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grow.gallery.core.media.MediaItem
import com.grow.gallery.core.media.MediaQuery
import com.grow.gallery.core.media.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ViewerUiState(
    val items: List<MediaItem> = emptyList(),
    val currentIndex: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null,
) {
    val currentItem: MediaItem? get() = items.getOrNull(currentIndex)
    val totalCount: Int get() = items.size
}

@HiltViewModel
class ViewerViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ViewerUiState())
    val uiState: StateFlow<ViewerUiState> = _uiState.asStateFlow()

    fun loadMedia(mediaId: Long, isVideo: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val groups = mediaRepository.loadMedia(MediaQuery())
                val allItems = groups.flatMap { it.items }
                val index = allItems.indexOfFirst { it.id == mediaId }.coerceAtLeast(0)
                _uiState.update {
                    it.copy(items = allItems, currentIndex = index, isLoading = false)
                }
            } catch (e: Exception) {
                // Fallback: load single item
                val single = mediaRepository.getMediaById(mediaId, isVideo)
                _uiState.update {
                    it.copy(
                        items = if (single != null) listOf(single) else emptyList(),
                        currentIndex = 0,
                        isLoading = false,
                        error = if (single == null) "Failed to load media" else null,
                    )
                }
            }
        }
    }

    fun setCurrentIndex(index: Int) {
        if (index in _uiState.value.items.indices) {
            _uiState.update { it.copy(currentIndex = index) }
        }
    }

    fun toggleFavorite(item: MediaItem) {
        viewModelScope.launch {
            val newFavorite = !item.isFavorite
            mediaRepository.setFavorite(item, newFavorite)
            _uiState.update { state ->
                state.copy(
                    items = state.items.map {
                        if (it.id == item.id) it.copy(isFavorite = newFavorite) else it
                    }
                )
            }
        }
    }

    fun deleteItem(item: MediaItem) {
        viewModelScope.launch {
            mediaRepository.deleteMedia(listOf(item))
            _uiState.update { state ->
                val newItems = state.items.filter { it.id != item.id }
                val newIndex = minOf(state.currentIndex, (newItems.size - 1).coerceAtLeast(0))
                state.copy(items = newItems, currentIndex = newIndex)
            }
        }
    }
}
