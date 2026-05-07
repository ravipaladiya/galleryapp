package com.grow.gallery.feature.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grow.gallery.core.database.AlbumDao
import com.grow.gallery.core.media.MediaItem
import com.grow.gallery.core.media.MediaQuery
import com.grow.gallery.core.media.MediaRepository
import com.grow.gallery.core.media.SortOrder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlbumDetailUiState(
    val items: List<MediaItem> = emptyList(),
    val sortOrder: SortOrder = SortOrder.NEWEST,
    val showSortSheet: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    val snackbarMessage: String? = null,
    val selectedItems: Set<Long> = emptySet(),
    val isSelectionMode: Boolean = false,
)

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val albumDao: AlbumDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlbumDetailUiState())
    val uiState: StateFlow<AlbumDetailUiState> = _uiState.asStateFlow()

    private var currentAlbumId: Long = Long.MIN_VALUE
    private var reloadJob: Job? = null

    fun loadAlbumMedia(albumId: Long) {
        currentAlbumId = albumId
        reload()
    }

    fun setSortOrder(order: SortOrder) {
        _uiState.update { it.copy(sortOrder = order, showSortSheet = false) }
        reload()
    }

    fun showSortSheet() { _uiState.update { it.copy(showSortSheet = true) } }
    fun dismissSortSheet() { _uiState.update { it.copy(showSortSheet = false) } }

    private fun reload() {
        if (currentAlbumId == Long.MIN_VALUE) return
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val sortOrder = _uiState.value.sortOrder
                val items = if (currentAlbumId < 0) {
                    val dbAlbumId = -currentAlbumId
                    val mediaIds = albumDao.getMediaIdsForAlbum(dbAlbumId).toSet()
                    val loaded = mediaRepository.loadMediaByIds(mediaIds)
                    when (sortOrder) {
                        SortOrder.NEWEST -> loaded.sortedByDescending { it.dateTaken ?: it.dateAdded }
                        SortOrder.OLDEST -> loaded.sortedBy { it.dateTaken ?: it.dateAdded }
                        SortOrder.SIZE_DESC -> loaded.sortedByDescending { it.size }
                        SortOrder.SIZE_ASC -> loaded.sortedBy { it.size }
                    }
                } else {
                    val query = MediaQuery(albumId = currentAlbumId, sortOrder = sortOrder)
                    val groups = mediaRepository.loadMedia(query)
                    groups.flatMap { it.items }
                }
                _uiState.update { it.copy(items = items, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, snackbarMessage = e.message ?: "Failed to load album")
                }
            }
        }
    }

    fun toggleSelection(mediaId: Long) {
        _uiState.update { state ->
            val newSel = state.selectedItems.toMutableSet().also {
                if (it.contains(mediaId)) it.remove(mediaId) else it.add(mediaId)
            }
            state.copy(selectedItems = newSel, isSelectionMode = newSel.isNotEmpty())
        }
    }

    fun enterSelectionMode(mediaId: Long) {
        _uiState.update { it.copy(selectedItems = setOf(mediaId), isSelectionMode = true) }
    }

    fun exitSelectionMode() {
        _uiState.update { it.copy(selectedItems = emptySet(), isSelectionMode = false) }
    }

    fun onSnackbarShown() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
