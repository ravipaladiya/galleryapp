package com.grow.gallery.feature.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grow.gallery.core.database.AlbumDao
import com.grow.gallery.core.database.CustomAlbum
import com.grow.gallery.core.media.Album
import com.grow.gallery.core.media.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlbumsUiState(
    val albums: List<Album> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val snackbarMessage: String? = null,
)

@HiltViewModel
class AlbumsViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val albumDao: AlbumDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlbumsUiState())
    val uiState: StateFlow<AlbumsUiState> = _uiState.asStateFlow()

    init { loadAlbums() }

    fun loadAlbums() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val systemAlbums = mediaRepository.loadAlbums()
                // Single JOIN query instead of N+1 getMediaCount calls
                val customAlbums = albumDao.getAlbumsWithCounts().map { row ->
                    Album(
                        id = -row.id,
                        name = row.name,
                        coverUri = null,
                        mediaCount = row.mediaCount,
                        isSystemAlbum = false,
                    )
                }
                _uiState.update {
                    it.copy(albums = systemAlbums + customAlbums, isLoading = false)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun createAlbum(name: String) {
        val trimmed = name.trim().filter { it >= ' ' && it != '/' }
        when {
            trimmed.isBlank() -> {
                _uiState.update { it.copy(snackbarMessage = "Album name cannot be empty") }
                return
            }
            trimmed.length > 64 -> {
                _uiState.update { it.copy(snackbarMessage = "Album name is too long (max 64 characters)") }
                return
            }
        }
        viewModelScope.launch {
            if (albumDao.countByName(trimmed) > 0) {
                _uiState.update { it.copy(snackbarMessage = "An album named \"$trimmed\" already exists") }
                return@launch
            }
            val insertedId = albumDao.insertAlbum(CustomAlbum(name = trimmed))
            // Append to existing list instead of re-querying MediaStore
            val newAlbum = Album(
                id = -insertedId,
                name = trimmed,
                coverUri = null,
                mediaCount = 0,
                isSystemAlbum = false,
            )
            _uiState.update { state ->
                state.copy(albums = state.albums + newAlbum)
            }
        }
    }

    fun onSnackbarShown() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
