package com.grow.gallery.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grow.gallery.core.media.MediaFilter
import com.grow.gallery.core.media.MediaItem
import com.grow.gallery.core.media.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val results: List<MediaItem> = emptyList(),
    val filter: MediaFilter = MediaFilter.ALL,
    val isLoading: Boolean = false,
    val recentSearches: List<String> = emptyList(),
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(results = emptyList(), isLoading = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(300) // debounce
            _uiState.update { it.copy(isLoading = true) }
            try {
                val results = mediaRepository.searchMedia(query)
                val filtered = when (_uiState.value.filter) {
                    MediaFilter.PHOTOS -> results.filter { it.isPhoto }
                    MediaFilter.VIDEOS -> results.filter { it.isVideo }
                    MediaFilter.FAVORITES -> results.filter { it.isFavorite }
                    MediaFilter.ALL -> results
                }
                _uiState.update { it.copy(results = filtered, isLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun setFilter(filter: MediaFilter) {
        _uiState.update { it.copy(filter = filter) }
        if (_uiState.value.query.isNotBlank()) {
            onQueryChange(_uiState.value.query)
        }
    }
}
