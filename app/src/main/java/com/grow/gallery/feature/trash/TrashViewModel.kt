package com.grow.gallery.feature.trash

import android.app.PendingIntent
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grow.gallery.core.database.TrashDao
import com.grow.gallery.core.database.TrashItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class TrashUiState(
    val items: List<TrashItem> = emptyList(),
    val isLoading: Boolean = true,
    val snackbarMessage: String? = null,
    val pendingDeleteIntent: PendingIntent? = null,
)

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val trashDao: TrashDao,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrashUiState())
    val uiState: StateFlow<TrashUiState> = _uiState.asStateFlow()

    init {
        load()
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
            load()
            _uiState.update { it.copy(snackbarMessage = "${item.displayName} removed from Recently Deleted.") }
        }
    }

    fun deletePermanently(item: TrashItem) {
        viewModelScope.launch {
            val deleted = deleteFromMediaStore(Uri.parse(item.uri))
            trashDao.deleteById(item.mediaId)
            load()
            _uiState.update {
                it.copy(
                    snackbarMessage = if (deleted) "${item.displayName} permanently deleted."
                    else "Item removed from list.",
                )
            }
        }
    }

    fun requestPermanentDeleteAll() {
        viewModelScope.launch {
            val items = _uiState.value.items
            if (items.isEmpty()) return@launch

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val uris = items.mapNotNull { runCatching { Uri.parse(it.uri) }.getOrNull() }
                if (uris.isNotEmpty()) {
                    val pendingIntent = runCatching {
                        MediaStore.createDeleteRequest(context.contentResolver, uris)
                    }.getOrNull()
                    if (pendingIntent != null) {
                        _uiState.update { it.copy(pendingDeleteIntent = pendingIntent) }
                        return@launch
                    }
                }
            }
            emptyTrashDirectly()
        }
    }

    fun onDeleteIntentConsumed() {
        _uiState.update { it.copy(pendingDeleteIntent = null) }
    }

    fun onDeleteResult(confirmed: Boolean) {
        if (confirmed) {
            viewModelScope.launch {
                trashDao.deleteAll()
                load()
                _uiState.update { it.copy(snackbarMessage = "Trash emptied.") }
            }
        }
    }

    fun emptyTrash() {
        viewModelScope.launch { emptyTrashDirectly() }
    }

    private suspend fun emptyTrashDirectly() {
        val items = _uiState.value.items
        items.forEach { item ->
            runCatching { deleteFromMediaStore(Uri.parse(item.uri)) }
        }
        trashDao.deleteAll()
        load()
        _uiState.update { it.copy(snackbarMessage = "Trash emptied.") }
    }

    fun onSnackbarShown() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    private suspend fun deleteFromMediaStore(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching { context.contentResolver.delete(uri, null, null) > 0 }.getOrDefault(false)
    }
}
