package com.grow.gallery.feature.viewer

import android.app.PendingIntent
import android.os.Build
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
    /** Non-null on Android R+ while waiting for the system delete confirmation dialog. */
    val pendingDeleteIntent: PendingIntent? = null,
    val pendingDeleteItem: MediaItem? = null,
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
                _uiState.update { it.copy(items = allItems, currentIndex = index, isLoading = false) }
            } catch (e: Exception) {
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val pendingIntent = try {
                    mediaRepository.prepareDelete(listOf(item))
                } catch (e: Exception) {
                    return@launch
                }
                _uiState.update { it.copy(pendingDeleteIntent = pendingIntent, pendingDeleteItem = item) }
            } else {
                val result = mediaRepository.deleteMedia(listOf(item))
                result.onSuccess { removeItemFromList(item) }
                    .onFailure { e ->
                        _uiState.update { it.copy(error = "Delete failed: ${e.message}") }
                    }
            }
        }
    }

    /** Called by the screen immediately after launching the intent sender. */
    fun onDeleteIntentConsumed() {
        _uiState.update { it.copy(pendingDeleteIntent = null) }
    }

    /** Called after the system delete confirmation dialog returns. */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun onDeleteResult(confirmed: Boolean): Boolean {
        val item = _uiState.value.pendingDeleteItem
        _uiState.update { it.copy(pendingDeleteItem = null) }
        if (confirmed && item != null) {
            removeItemFromList(item)
            return _uiState.value.items.isEmpty()
        }
        return false
    }

    private fun removeItemFromList(item: MediaItem) {
        _uiState.update { state ->
            val newItems = state.items.filter { it.id != item.id }
            val newIndex = minOf(state.currentIndex, (newItems.size - 1).coerceAtLeast(0))
            state.copy(items = newItems, currentIndex = newIndex)
        }
    }
}
