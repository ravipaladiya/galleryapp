package com.grow.gallery.feature.storage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grow.gallery.core.media.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StorageUiState(
    val photoSize: String = "Calculating…",
    val videoSize: String = "Calculating…",
)

@HiltViewModel
class StorageViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StorageUiState())
    val uiState: StateFlow<StorageUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val storage = mediaRepository.computeStorageByType()
                _uiState.update {
                    it.copy(
                        photoSize = storage.photoBytes.formatSize(),
                        videoSize = storage.videoBytes.formatSize(),
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(photoSize = "Unknown", videoSize = "Unknown") }
            }
        }
    }

    private fun Long.formatSize(): String = when {
        this >= 1_000_000_000L -> "%.1f GB".format(this / 1_000_000_000.0)
        this >= 1_000_000L -> "%.1f MB".format(this / 1_000_000.0)
        this >= 1_000L -> "%.0f KB".format(this / 1_000.0)
        else -> "$this B"
    }
}
