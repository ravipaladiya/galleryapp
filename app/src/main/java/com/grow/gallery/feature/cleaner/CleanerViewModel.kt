package com.grow.gallery.feature.cleaner

import android.app.PendingIntent
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grow.gallery.core.common.toFormattedSize
import com.grow.gallery.core.media.MediaItem
import com.grow.gallery.core.media.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class CleanerPhase { IDLE, SCANNING, RESULTS, CLEANING, DONE }

data class CleanerCategory(
    val name: String,
    val itemCount: Int,
    val size: String,
    val isSelected: Boolean = true,
    val items: List<MediaItem> = emptyList(),
)

data class CleanerUiState(
    val phase: CleanerPhase = CleanerPhase.IDLE,
    val categories: List<CleanerCategory> = emptyList(),
    val totalReclaimable: String = "0 MB",
    val freedSpace: String = "0 MB",
    val deletedCount: Int = 0,
    val pendingDeleteIntent: PendingIntent? = null,
    val pendingDeleteItems: List<MediaItem> = emptyList(),
    val snackbarMessage: String? = null,
)

@HiltViewModel
class CleanerViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CleanerUiState())
    val uiState: StateFlow<CleanerUiState> = _uiState.asStateFlow()

    fun startScan() {
        viewModelScope.launch {
            _uiState.update { it.copy(phase = CleanerPhase.SCANNING) }

            try {
                val found = mediaRepository.getCleanerCategories()
                val categories = mutableListOf<CleanerCategory>()

                if (found.screenshots.isNotEmpty()) {
                    categories.add(
                        CleanerCategory(
                            name = "Screenshots",
                            itemCount = found.screenshots.size,
                            size = found.screenshots.sumOf { it.size }.toFormattedSize(),
                            items = found.screenshots,
                        )
                    )
                }

                if (found.burstPhotos.isNotEmpty()) {
                    categories.add(
                        CleanerCategory(
                            name = "Burst / Similar Photos",
                            itemCount = found.burstPhotos.size,
                            size = found.burstPhotos.sumOf { it.size }.toFormattedSize(),
                            items = found.burstPhotos,
                        )
                    )
                }

                if (found.largeMedia.isNotEmpty()) {
                    categories.add(
                        CleanerCategory(
                            name = "Large Files (>50 MB)",
                            itemCount = found.largeMedia.size,
                            size = found.largeMedia.sumOf { it.size }.toFormattedSize(),
                            items = found.largeMedia,
                        )
                    )
                }

                val totalBytes = categories.filter { it.isSelected }.sumOf { cat ->
                    cat.items.sumOf { it.size }
                }

                _uiState.update {
                    it.copy(
                        phase = CleanerPhase.RESULTS,
                        categories = categories,
                        totalReclaimable = totalBytes.toFormattedSize(),
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        phase = CleanerPhase.IDLE,
                        snackbarMessage = "Scan failed: ${e.message}",
                    )
                }
            }
        }
    }

    fun toggleCategory(categoryName: String, selected: Boolean) {
        _uiState.update { state ->
            val updated = state.categories.map { cat ->
                if (cat.name == categoryName) cat.copy(isSelected = selected) else cat
            }
            val totalBytes = updated.filter { it.isSelected }.sumOf { cat ->
                cat.items.sumOf { it.size }
            }
            state.copy(categories = updated, totalReclaimable = totalBytes.toFormattedSize())
        }
    }

    fun cleanSelected() {
        val selectedItems = _uiState.value.categories
            .filter { it.isSelected }
            .flatMap { it.items }
            .distinctBy { it.id }
        if (selectedItems.isEmpty()) return
        requestDelete(selectedItems)
    }

    fun cleanAll() {
        val allItems = _uiState.value.categories
            .flatMap { it.items }
            .distinctBy { it.id }
        if (allItems.isEmpty()) return
        requestDelete(allItems)
    }

    private fun requestDelete(items: List<MediaItem>) {
        viewModelScope.launch {
            _uiState.update { it.copy(phase = CleanerPhase.CLEANING) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val pendingIntent = mediaRepository.prepareDelete(items)
                    if (pendingIntent != null) {
                        _uiState.update {
                            it.copy(
                                pendingDeleteIntent = pendingIntent,
                                pendingDeleteItems = items,
                                phase = CleanerPhase.RESULTS,
                            )
                        }
                    } else {
                        finalizeDeletion(items)
                    }
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(
                            phase = CleanerPhase.RESULTS,
                            snackbarMessage = "Delete failed: ${e.message}",
                        )
                    }
                }
            } else {
                val result = mediaRepository.deleteMedia(items)
                if (result.isSuccess) {
                    finalizeDeletion(items)
                } else {
                    _uiState.update {
                        it.copy(
                            phase = CleanerPhase.RESULTS,
                            snackbarMessage = "Delete failed: ${result.exceptionOrNull()?.message}",
                        )
                    }
                }
            }
        }
    }

    fun onDeleteIntentConsumed() {
        _uiState.update { it.copy(pendingDeleteIntent = null) }
    }

    fun onDeleteResult(confirmed: Boolean) {
        val items = _uiState.value.pendingDeleteItems
        _uiState.update { it.copy(pendingDeleteItems = emptyList()) }
        if (confirmed) {
            finalizeDeletion(items)
        } else {
            _uiState.update { it.copy(phase = CleanerPhase.RESULTS) }
        }
    }

    private fun finalizeDeletion(items: List<MediaItem>) {
        val freedBytes = items.sumOf { it.size }
        _uiState.update {
            it.copy(
                phase = CleanerPhase.DONE,
                deletedCount = items.size,
                freedSpace = freedBytes.toFormattedSize(),
            )
        }
    }

    fun onSnackbarShown() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun reset() {
        _uiState.update { CleanerUiState() }
    }
}
