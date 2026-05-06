package com.grow.gallery.feature.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grow.gallery.core.media.MediaItem
import com.grow.gallery.core.media.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ViewerUiState(
    val currentItem: MediaItem? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class ViewerViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ViewerUiState())
    val uiState: StateFlow<ViewerUiState> = _uiState.asStateFlow()

    fun loadMedia(mediaId: Long, isVideo: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val item = mediaRepository.getMediaById(mediaId, isVideo)
                _uiState.update { it.copy(currentItem = item, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun toggleFavorite(item: MediaItem) {
        viewModelScope.launch {
            mediaRepository.setFavorite(item, !item.isFavorite)
            _uiState.update { state ->
                state.copy(currentItem = state.currentItem?.copy(isFavorite = !item.isFavorite))
            }
        }
    }

    fun deleteItem(item: MediaItem) {
        viewModelScope.launch {
            mediaRepository.deleteMedia(listOf(item))
        }
    }
}
