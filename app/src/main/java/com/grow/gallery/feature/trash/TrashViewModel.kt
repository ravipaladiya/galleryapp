package com.grow.gallery.feature.trash

import android.app.PendingIntent
import android.content.ContentValues
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
    val pendingDeleteIntent: PendingIntent? = null,
    val pendingDeleteItem: TrashItem? = null,
    val pendingRestoreIntent: PendingIntent? = null,
    val pendingRestoreItem: TrashItem? = null,
    val snackbarMessage: String? = null,
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

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val items = trashDao.getAllTrashItems()
            _uiState.update { it.copy(items = items, isLoading = false) }
        }
    }

    fun restore(item: TrashItem) {
        viewModelScope.launch {
            val uri = Uri.parse(item.uri)
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    // API 30+: createTrashRequest with false un-trashes the item
                    try {
                        val pendingIntent = MediaStore.createTrashRequest(
                            context.contentResolver, listOf(uri), false
                        )
                        _uiState.update {
                            it.copy(pendingRestoreIntent = pendingIntent, pendingRestoreItem = item)
                        }
                    } catch (_: Exception) {
                        // URI may no longer exist; remove from DB and inform user
                        trashDao.deleteById(item.mediaId)
                        load()
                        _uiState.update { it.copy(snackbarMessage = "${item.displayName} restored.") }
                    }
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                    // API 29: update IS_TRASHED = 0 directly
                    withContext(Dispatchers.IO) {
                        try {
                            context.contentResolver.update(
                                uri,
                                ContentValues().apply {
                                    put(MediaStore.Images.Media.IS_TRASHED, 0)
                                },
                                null, null,
                            )
                        } catch (_: Exception) {}
                    }
                    trashDao.deleteById(item.mediaId)
                    load()
                    _uiState.update { it.copy(snackbarMessage = "${item.displayName} restored.") }
                }
                else -> {
                    // Pre-Q: no system trash concept, just remove from app DB
                    trashDao.deleteById(item.mediaId)
                    load()
                    _uiState.update { it.copy(snackbarMessage = "${item.displayName} restored.") }
                }
            }
        }
    }

    fun onRestoreIntentConsumed() {
        _uiState.update { it.copy(pendingRestoreIntent = null) }
    }

    fun onRestoreResult(confirmed: Boolean) {
        val item = _uiState.value.pendingRestoreItem
        _uiState.update { it.copy(pendingRestoreItem = null) }
        if (confirmed && item != null) {
            viewModelScope.launch {
                trashDao.deleteById(item.mediaId)
                load()
                _uiState.update { it.copy(snackbarMessage = "${item.displayName} restored.") }
            }
        }
    }

    fun deletePermanently(item: TrashItem) {
        viewModelScope.launch {
            val uri = Uri.parse(item.uri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val pendingIntent = MediaStore.createDeleteRequest(
                        context.contentResolver, listOf(uri)
                    )
                    _uiState.update { it.copy(pendingDeleteIntent = pendingIntent, pendingDeleteItem = item) }
                } catch (e: Exception) {
                    // URI likely gone — clean up the DB entry and inform the user
                    deleteFromDbOnly(item)
                    _uiState.update { it.copy(snackbarMessage = "Could not delete: ${e.message}") }
                }
            } else {
                val deleteError = withContext(Dispatchers.IO) {
                    try { context.contentResolver.delete(uri, null, null); null }
                    catch (e: Exception) { e.message }
                }
                if (deleteError != null) {
                    _uiState.update { it.copy(snackbarMessage = "Delete failed: $deleteError") }
                } else {
                    deleteFromDbOnly(item)
                }
            }
        }
    }

    fun onDeleteIntentConsumed() {
        _uiState.update { it.copy(pendingDeleteIntent = null) }
    }

    fun onDeleteResult(confirmed: Boolean) {
        val item = _uiState.value.pendingDeleteItem
        _uiState.update { it.copy(pendingDeleteItem = null) }
        if (confirmed) {
            viewModelScope.launch {
                if (item != null) {
                    deleteFromDbOnly(item)
                    _uiState.update { it.copy(snackbarMessage = "${item.displayName} permanently deleted.") }
                } else {
                    emptyTrashFromDbOnly()
                    _uiState.update { it.copy(snackbarMessage = "Trash emptied.") }
                }
            }
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            val items = _uiState.value.items
            if (items.isEmpty()) return@launch
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val uris = items.map { Uri.parse(it.uri) }
                    val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, uris)
                    _uiState.update { it.copy(pendingDeleteIntent = pendingIntent, pendingDeleteItem = null) }
                    return@launch
                } catch (e: Exception) {
                    _uiState.update { it.copy(snackbarMessage = "Could not delete: ${e.message}") }
                    return@launch
                }
            }
            var errorCount = 0
            withContext(Dispatchers.IO) {
                items.forEach { item ->
                    try { context.contentResolver.delete(Uri.parse(item.uri), null, null) }
                    catch (_: Exception) { errorCount++ }
                }
            }
            emptyTrashFromDbOnly()
            val msg = if (errorCount > 0) "Trash emptied ($errorCount file(s) could not be deleted)."
                      else "Trash emptied."
            _uiState.update { it.copy(snackbarMessage = msg) }
        }
    }

    private suspend fun deleteFromDbOnly(item: TrashItem) {
        trashDao.deleteById(item.mediaId)
        load()
    }

    private suspend fun emptyTrashFromDbOnly() {
        trashDao.deleteAll()
        load()
    }

    fun onSnackbarShown() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
