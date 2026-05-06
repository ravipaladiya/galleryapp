package com.grow.gallery.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grow.gallery.core.common.DataStoreManager
import com.grow.gallery.core.media.MediaFilter
import com.grow.gallery.core.media.MediaItem
import com.grow.gallery.core.media.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val allResults: List<MediaItem> = emptyList(),
    val results: List<MediaItem> = emptyList(),
    val filter: MediaFilter = MediaFilter.ALL,
    val isLoading: Boolean = false,
    val recentSearches: List<String> = emptyList(),
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val dataStoreManager: DataStoreManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            dataStoreManager.recentSearches.collect { searches ->
                _uiState.update { it.copy(recentSearches = searches) }
            }
        }
    }

    fun onQueryChange(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(query = query, allResults = emptyList(), results = emptyList(), isLoading = false) }
            return
        }
        // Set isLoading immediately so the UI shows a spinner instead of the empty-state during debounce
        _uiState.update { it.copy(query = query, isLoading = true) }
        searchJob = viewModelScope.launch {
            delay(300)
            try {
                val results = mediaRepository.searchMedia(query)
                val filtered = applyFilter(results, _uiState.value.filter)
                _uiState.update { it.copy(allResults = results, results = filtered, isLoading = false) }
                // Save every query the user typed, regardless of result count — failed searches
                // are the ones they'll want to retry next time.
                dataStoreManager.addRecentSearch(query.trim())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun setFilter(filter: MediaFilter) {
        // Cancel any in-flight search so it cannot overwrite results with the stale filter it captured
        searchJob?.cancel()
        val cached = _uiState.value.allResults
        _uiState.update { it.copy(filter = filter, results = applyFilter(cached, filter)) }
    }

    fun removeRecentSearch(query: String) {
        viewModelScope.launch { dataStoreManager.removeRecentSearch(query) }
    }

    fun clearRecentSearches() {
        viewModelScope.launch { dataStoreManager.clearRecentSearches() }
    }

    private fun applyFilter(items: List<MediaItem>, filter: MediaFilter) = when (filter) {
        MediaFilter.PHOTOS -> items.filter { it.isPhoto }
        MediaFilter.VIDEOS -> items.filter { it.isVideo }
        MediaFilter.FAVORITES -> items.filter { it.isFavorite }
        MediaFilter.ALL -> items
    }
}
