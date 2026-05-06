package com.grow.gallery.feature.home

import android.net.Uri
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
    val shareUris: List<Uri>? = null,
    val showDeleteConfirm: Boolean = false,
    val snackbarMessage: String? = null,
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

    fun prepareShare() {
        val selectedIds = _uiState.value.selectedItems
        val uris = _uiState.value.mediaGroups
            .flatMap { it.items }
            .filter { it.id in selectedIds }
            .map { it.uri }
        _uiState.update { it.copy(shareUris = uris) }
    }

    fun onShareHandled() {
        _uiState.update { it.copy(shareUris = null) }
    }

    fun requestDeleteSelected() {
        _uiState.update { it.copy(showDeleteConfirm = true) }
    }

    fun cancelDelete() {
        _uiState.update { it.copy(showDeleteConfirm = false) }
    }

    fun confirmDeleteSelected() {
        val selectedIds = _uiState.value.selectedItems
        val items = _uiState.value.mediaGroups
            .flatMap { it.items }
            .filter { it.id in selectedIds }

        viewModelScope.launch {
            _uiState.update { it.copy(showDeleteConfirm = false) }
            val result = mediaRepository.deleteMedia(items)
            if (result.isSuccess) {
                exitSelectionMode()
                loadMedia()
                _uiState.update { it.copy(snackbarMessage = "${items.size} item(s) deleted") }
            } else {
                _uiState.update { it.copy(snackbarMessage = "Delete failed: ${result.exceptionOrNull()?.message}") }
            }
        }
    }

    fun favoriteSelected() {
        val selectedIds = _uiState.value.selectedItems
        val items = _uiState.value.mediaGroups
            .flatMap { it.items }
            .filter { it.id in selectedIds }

        viewModelScope.launch {
            val allFavorited = items.all { it.isFavorite }
            items.forEach { item ->
                mediaRepository.setFavorite(item, !allFavorited)
            }
            exitSelectionMode()
            loadMedia()
            val action = if (allFavorited) "removed from" else "added to"
            _uiState.update { it.copy(snackbarMessage = "${items.size} item(s) $action favorites") }
        }
    }

    fun onSnackbarShown() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
