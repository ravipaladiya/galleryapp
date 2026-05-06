package com.grow.gallery.feature.trash

import android.app.Activity
import android.app.PendingIntent
import android.content.ContentResolver
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
            _uiState.update { it.copy(snackbarMessage = "${item.displayName} restored") }
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
                    _uiState.update {
                        it.copy(pendingDeleteIntent = pendingIntent, pendingDeleteItem = item)
                    }
                } catch (e: Exception) {
                    deleteFromDbOnly(item)
                }
            } else {
                withContext(Dispatchers.IO) {
                    try { context.contentResolver.delete(uri, null, null) } catch (_: Exception) {}
                }
                deleteFromDbOnly(item)
            }
        }
    }

    fun onDeleteIntentConsumed() {
        _uiState.update { it.copy(pendingDeleteIntent = null) }
    }

    fun onDeleteResult(confirmed: Boolean) {
        val item = _uiState.value.pendingDeleteItem ?: return
        _uiState.update { it.copy(pendingDeleteItem = null) }
        if (confirmed) {
            viewModelScope.launch { deleteFromDbOnly(item) }
        }
    }

    private suspend fun deleteFromDbOnly(item: TrashItem) {
        trashDao.deleteById(item.mediaId)
        load()
    }

    fun emptyTrash() {
        viewModelScope.launch {
            val items = _uiState.value.items
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val uris = items.map { Uri.parse(it.uri) }
                    val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, uris)
                    _uiState.update { it.copy(pendingDeleteIntent = pendingIntent, pendingDeleteItem = null) }
                } catch (_: Exception) {
                    emptyTrashFromDbOnly()
                }
            } else {
                withContext(Dispatchers.IO) {
                    items.forEach { item ->
                        try { context.contentResolver.delete(Uri.parse(item.uri), null, null) } catch (_: Exception) {}
                    }
                }
                emptyTrashFromDbOnly()
            }
        }
    }

    private suspend fun emptyTrashFromDbOnly() {
        trashDao.deleteAll()
        load()
    }

    fun onSnackbarShown() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
