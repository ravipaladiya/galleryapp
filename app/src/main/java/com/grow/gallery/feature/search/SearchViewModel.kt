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
        _uiState.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(results = emptyList(), isLoading = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            _uiState.update { it.copy(isLoading = true) }
            try {
                val results = mediaRepository.searchMedia(query)
                val filtered = applyFilter(results, _uiState.value.filter)
                _uiState.update { it.copy(results = filtered, isLoading = false) }
                // Save all queries to recents (including zero-result ones) so users see what they searched
                dataStoreManager.addRecentSearch(query.trim())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun setFilter(filter: MediaFilter) {
        // Cancel in-flight search and apply filter immediately without re-debouncing
        searchJob?.cancel()
        _uiState.update { it.copy(filter = filter) }
        val currentQuery = _uiState.value.query
        if (currentQuery.isBlank()) return
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val results = mediaRepository.searchMedia(currentQuery)
                val filtered = applyFilter(results, filter)
                _uiState.update { it.copy(results = filtered, isLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun clearRecentSearches() {
        viewModelScope.launch { dataStoreManager.clearRecentSearches() }
    }

    fun removeRecentSearch(query: String) {
        viewModelScope.launch { dataStoreManager.removeRecentSearch(query) }
    }

    private fun applyFilter(items: List<MediaItem>, filter: MediaFilter) = when (filter) {
        MediaFilter.PHOTOS -> items.filter { it.isPhoto }
        MediaFilter.VIDEOS -> items.filter { it.isVideo }
        MediaFilter.FAVORITES -> items.filter { it.isFavorite }
        MediaFilter.ALL -> items
    }
}
