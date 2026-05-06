package com.grow.gallery.feature.video

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grow.gallery.core.media.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VideoUiState(
    val videoUri: Uri? = null,
    val displayName: String = "",
    val duration: Long = 0L,
    val isLoading: Boolean = true,
    val error: String? = null,
    val trimStart: Long = 0L,
    val trimEnd: Long = 0L,
)

@HiltViewModel
class VideoViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VideoUiState())
    val uiState: StateFlow<VideoUiState> = _uiState.asStateFlow()

    fun loadVideo(mediaId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val item = mediaRepository.getMediaById(mediaId, isVideo = true)
                _uiState.update {
                    it.copy(
                        videoUri = item?.uri,
                        displayName = item?.displayName ?: "",
                        duration = item?.duration ?: 0L,
                        trimEnd = item?.duration ?: 0L,
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun setTrimStart(ms: Long) = _uiState.update { it.copy(trimStart = ms) }
    fun setTrimEnd(ms: Long) = _uiState.update { it.copy(trimEnd = ms) }
}
