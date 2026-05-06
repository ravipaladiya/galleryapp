package com.grow.gallery.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grow.gallery.core.media.*
import com.grow.gallery.core.permissions.MediaPermissionState
import com.grow.gallery.core.permissions.PermissionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val mediaGroups: List<MediaGroup> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val permissionState: MediaPermissionState = MediaPermissionState.NOT_ASKED,
    val sortOrder: SortOrder = SortOrder.NEWEST,
    val filter: MediaFilter = MediaFilter.ALL,
    val selectedItems: Set<Long> = emptySet(),
    val isSelectionMode: Boolean = false,
    val totalCount: Int = 0,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val permissionManager: PermissionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.NEWEST)
    private val _filter = MutableStateFlow(MediaFilter.ALL)

    init {
        loadMedia()
    }

    fun onPermissionResult() {
        permissionManager.refresh()
        loadMedia()
    }

    fun loadMedia() {
        viewModelScope.launch {
            val permState = permissionManager.checkCurrentState()
            _uiState.update { it.copy(permissionState = permState, isLoading = true, error = null) }

            if (!permissionManager.hasAnyAccess()) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }

            try {
                val query = MediaQuery(
                    sortOrder = _sortOrder.value,
                    filter = _filter.value,
                )
                val groups = mediaRepository.loadMedia(query)
                val total = groups.sumOf { it.items.size }
                _uiState.update {
                    it.copy(
                        mediaGroups = groups,
                        isLoading = false,
                        totalCount = total,
                        permissionState = permState,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Failed to load media")
                }
            }
        }
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
        loadMedia()
    }

    fun setFilter(filter: MediaFilter) {
        _filter.value = filter
        loadMedia()
    }

    fun toggleSelection(mediaId: Long) {
        _uiState.update { state ->
            val newSelection = state.selectedItems.toMutableSet()
            if (newSelection.contains(mediaId)) newSelection.remove(mediaId)
            else newSelection.add(mediaId)
            state.copy(
                selectedItems = newSelection,
                isSelectionMode = newSelection.isNotEmpty(),
            )
        }
    }

    fun enterSelectionMode(mediaId: Long) {
        _uiState.update { state ->
            state.copy(
                selectedItems = setOf(mediaId),
                isSelectionMode = true,
            )
        }
    }

    fun exitSelectionMode() {
        _uiState.update { it.copy(selectedItems = emptySet(), isSelectionMode = false) }
    }

    fun selectAll() {
        val allIds = _uiState.value.mediaGroups
            .flatMap { it.items }
            .map { it.id }
            .toSet()
        _uiState.update { it.copy(selectedItems = allIds) }
    }
}
