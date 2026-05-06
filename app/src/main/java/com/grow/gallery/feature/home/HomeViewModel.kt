package com.grow.gallery.feature.home

import android.app.PendingIntent
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grow.gallery.core.common.DataStoreManager
import com.grow.gallery.core.database.TrashDao
import com.grow.gallery.core.database.TrashItem
import com.grow.gallery.core.media.*
import com.grow.gallery.core.permissions.MediaPermissionState
import com.grow.gallery.core.permissions.PermissionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
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
    val gridSize: Int = 3,
    val hideScreenshots: Boolean = false,
    /** Non-null on Android R+ while waiting for the system delete confirmation dialog. */
    val pendingDeleteIntent: PendingIntent? = null,
    /** Items queued for deletion — preserved across the async R+ confirmation flow. */
    val pendingDeleteItems: List<MediaItem> = emptyList(),
)

@OptIn(FlowPreview::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val permissionManager: PermissionManager,
    private val dataStoreManager: DataStoreManager,
    private val trashDao: TrashDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.NEWEST)
    private val _filter = MutableStateFlow(MediaFilter.ALL)

    private var loadJob: Job? = null

    init {
        observeExternalMediaChanges()
        observeSettings()
        loadMedia()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            dataStoreManager.gridSize.collect { size ->
                _uiState.update { it.copy(gridSize = size) }
            }
        }
        viewModelScope.launch {
            dataStoreManager.hideScreenshots.collect { hide ->
                _uiState.update { it.copy(hideScreenshots = hide) }
                loadMedia()
            }
        }
    }

    private fun observeExternalMediaChanges() {
        viewModelScope.launch {
            mediaRepository.observeMediaChanges()
                .debounce(600)
                .collect {
                    if (permissionManager.hasAnyAccess()) loadMedia()
                }
        }
    }

    fun onPermissionResult() {
        permissionManager.refresh()
        loadMedia()
    }

    fun loadMedia() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
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
                    hideScreenshots = _uiState.value.hideScreenshots,
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
            state.copy(selectedItems = setOf(mediaId), isSelectionMode = true)
        }
    }

    fun exitSelectionMode() {
        _uiState.update { it.copy(selectedItems = emptySet(), isSelectionMode = false) }
    }

    fun selectAll() {
        val allIds = _uiState.value.mediaGroups.flatMap { it.items }.map { it.id }.toSet()
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

            // Track in trash DB before deleting
            items.forEach { item ->
                trashDao.insertTrashItem(
                    TrashItem(
                        mediaId = item.id,
                        uri = item.uri.toString(),
                        displayName = item.displayName,
                        mimeType = item.mimeType,
                        size = item.size,
                        originalPath = item.bucketName,
                    )
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val pendingIntent = try {
                    mediaRepository.prepareDelete(items)
                } catch (e: Exception) {
                    _uiState.update { it.copy(snackbarMessage = "Delete failed: ${e.message}") }
                    return@launch
                }
                _uiState.update { it.copy(pendingDeleteIntent = pendingIntent, pendingDeleteItems = items) }
            } else {
                val result = mediaRepository.deleteMedia(items)
                if (result.isSuccess) {
                    exitSelectionMode()
                    loadMedia()
                    _uiState.update { it.copy(snackbarMessage = "${items.size} item(s) moved to Recently Deleted") }
                } else {
                    _uiState.update {
                        it.copy(snackbarMessage = "Delete failed: ${result.exceptionOrNull()?.message}")
                    }
                }
            }
        }
    }

    /** Called by the Screen immediately after it has launched the intent sender. */
    fun onDeleteIntentConsumed() {
        _uiState.update { it.copy(pendingDeleteIntent = null) }
    }

    /** Called after the system delete confirmation dialog returns. */
    fun onDeleteResult(confirmed: Boolean) {
        val count = _uiState.value.pendingDeleteItems.size
        _uiState.update { it.copy(pendingDeleteItems = emptyList()) }
        if (confirmed) {
            exitSelectionMode()
            loadMedia()
            _uiState.update { it.copy(snackbarMessage = "$count item(s) moved to Recently Deleted") }
        }
    }

    fun favoriteSelected() {
        val selectedIds = _uiState.value.selectedItems
        val items = _uiState.value.mediaGroups
            .flatMap { it.items }
            .filter { it.id in selectedIds }

        viewModelScope.launch {
            val allFavorited = items.all { it.isFavorite }
            items.forEach { mediaRepository.setFavorite(it, !allFavorited) }
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
