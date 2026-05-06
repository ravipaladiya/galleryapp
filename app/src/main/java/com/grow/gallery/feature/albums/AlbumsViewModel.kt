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
                val customAlbums = albumDao.getAllAlbums().map { custom ->
                    val count = albumDao.getMediaCount(custom.id)
                    Album(
                        id = -custom.id,
                        name = custom.name,
                        coverUri = null,
                        mediaCount = count,
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
        viewModelScope.launch {
            albumDao.insertAlbum(CustomAlbum(name = name))
            loadAlbums()
        }
    }
}
