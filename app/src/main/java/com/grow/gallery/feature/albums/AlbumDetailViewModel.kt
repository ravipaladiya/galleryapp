package com.grow.gallery.feature.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grow.gallery.core.media.MediaItem
import com.grow.gallery.core.media.MediaQuery
import com.grow.gallery.core.media.MediaRepository
import com.grow.gallery.core.media.SortOrder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlbumDetailUiState(
    val items: List<MediaItem> = emptyList(),
    val isLoading: Boolean = true,
    val sortOrder: SortOrder = SortOrder.NEWEST,
    val error: String? = null,
)

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlbumDetailUiState())
    val uiState: StateFlow<AlbumDetailUiState> = _uiState.asStateFlow()

    private var currentAlbumId: Long = -1L

    fun loadAlbumMedia(albumId: Long) {
        currentAlbumId = albumId
        reload()
    }

    fun setSortOrder(order: SortOrder) {
        _uiState.update { it.copy(sortOrder = order) }
        reload()
    }

    private fun reload() {
        if (currentAlbumId < 0) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val query = MediaQuery(albumId = currentAlbumId, sortOrder = _uiState.value.sortOrder)
                val groups = mediaRepository.loadMedia(query)
                val items = groups.flatMap { it.items }
                _uiState.update { it.copy(items = items, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}
