package com.grow.gallery.feature.slideshow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(SlideshowUiState())
    val uiState: StateFlow<SlideshowUiState> = _uiState.asStateFlow()

    fun loadSlideshow(albumId: Long) {
        viewModelScope.launch {
            val query = if (albumId > 0) MediaQuery(albumId = albumId) else MediaQuery()
            val groups = mediaRepository.loadMedia(query)
            val items = groups.flatMap { it.items }.filter { it.isPhoto }
            _uiState.update { it.copy(items = items.shuffled(), isLoading = false) }
        }
    }
}
