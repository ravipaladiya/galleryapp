package com.grow.gallery.feature.storage

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class StorageUiState(
    val totalBytes: Long = 0L,
    val usedBytes: Long = 0L,
    val freeBytes: Long = 0L,
    val photosBytes: Long = 0L,
    val videosBytes: Long = 0L,
    val isLoading: Boolean = true,
)

@HiltViewModel
class StorageViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StorageUiState())
    val uiState: StateFlow<StorageUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { loadStorage() }
    }

    private suspend fun loadStorage() {
        withContext(Dispatchers.IO) {
            runCatching {
                val stat = StatFs(Environment.getExternalStorageDirectory().path)
                val total = stat.totalBytes
                val free = stat.availableBytes
                val used = total - free

                val photosBytes = queryMediaSize(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                val videosBytes = queryMediaSize(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)

                _uiState.update {
                    it.copy(
                        totalBytes = total,
                        usedBytes = used,
                        freeBytes = free,
                        photosBytes = photosBytes,
                        videosBytes = videosBytes,
                        isLoading = false,
                    )
                }
            }.onFailure {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun queryMediaSize(uri: android.net.Uri): Long {
        // Push aggregation into the content provider instead of iterating every row
        context.contentResolver.query(
            uri,
            arrayOf("SUM(${MediaStore.MediaColumns.SIZE})"),
            null, null, null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getLong(0)
        }
        return 0L
    }
}
