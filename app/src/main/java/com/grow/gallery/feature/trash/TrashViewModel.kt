package com.grow.gallery.feature.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grow.gallery.core.database.TrashDao
import com.grow.gallery.core.database.TrashItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrashUiState(
    val items: List<TrashItem> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val trashDao: TrashDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrashUiState())
    val uiState: StateFlow<TrashUiState> = _uiState.asStateFlow()

    init {
        load()
        // Auto-clean expired items
        viewModelScope.launch { trashDao.deleteExpired() }
    }

    fun load() {
        viewModelScope.launch {
            val items = trashDao.getAllTrashItems()
            _uiState.update { it.copy(items = items, isLoading = false) }
        }
    }

    fun restore(item: TrashItem) {
        viewModelScope.launch {
            trashDao.deleteById(item.mediaId)
            // TODO: Actually restore file to original location
            load()
        }
    }

    fun deletePermanently(item: TrashItem) {
        viewModelScope.launch {
            trashDao.deleteById(item.mediaId)
            // TODO: Delete file from storage
            load()
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            trashDao.deleteAll()
            load()
        }
    }
}
