package com.grow.gallery.feature.collage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grow.gallery.core.media.MediaItem
import com.grow.gallery.core.media.MediaQuery
import com.grow.gallery.core.media.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CollageUiState(
    val availableItems: List<MediaItem> = emptyList(),
    val selectedItems: List<MediaItem> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class CollageViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CollageUiState())
    val uiState: StateFlow<CollageUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val groups = mediaRepository.loadMedia(MediaQuery())
            val items = groups.flatMap { it.items }.filter { it.isPhoto }
            _uiState.update { it.copy(availableItems = items, isLoading = false) }
        }
    }

    fun toggleSelection(item: MediaItem) {
        _uiState.update { state ->
            val current = state.selectedItems.toMutableList()
            if (current.contains(item)) current.remove(item)
            else current.add(item)
            state.copy(selectedItems = current)
        }
    }
}
