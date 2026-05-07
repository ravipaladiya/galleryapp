package com.grow.gallery.feature.collage

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.grow.gallery.core.media.MediaItem
import com.grow.gallery.core.media.MediaQuery
import com.grow.gallery.core.media.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CollageUiState(
    val availableItems: List<MediaItem> = emptyList(),
    val selectedItems: List<MediaItem> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val saveMessage: String? = null,
)

@HiltViewModel
class CollageViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CollageUiState())
    val uiState: StateFlow<CollageUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val groups = mediaRepository.loadMedia(MediaQuery())
            val items = groups.flatMap { it.items }.filter { it.isPhoto }
            _uiState.update { it.copy(availableItems = items, isLoading = false) }
        }
    }

    fun toggleSelection(item: MediaItem, maxSlots: Int) {
        _uiState.update { state ->
            val current = state.selectedItems.toMutableList()
            if (current.contains(item)) {
                current.remove(item)
            } else if (current.size < maxSlots) {
                current.add(item)
            }
            state.copy(selectedItems = current)
        }
    }

    fun saveCollage(context: Context, layout: CollageLayout) {
        val items = _uiState.value.selectedItems
        if (items.isEmpty()) {
            _uiState.update { it.copy(saveMessage = "Select at least one photo") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val canvasSize = 1200
                val gap = 4
                val cellW = (canvasSize - gap * (layout.columns - 1)) / layout.columns
                val cellH = (canvasSize - gap * (layout.rows - 1)) / layout.rows
                val output = Bitmap.createBitmap(canvasSize, canvasSize, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(output)
                val imageLoader = ImageLoader(context)

                items.forEachIndexed { idx, item ->
                    // Column-major order to match CollagePreview
                    val col = idx / layout.rows
                    val row = idx % layout.rows
                    if (col >= layout.columns || row >= layout.rows) return@forEachIndexed

                    val request = ImageRequest.Builder(context)
                        .data(item.uri)
                        .size(cellW, cellH)
                        .allowHardware(false)
                        .build()
                    val result = imageLoader.execute(request)
                    if (result is SuccessResult) {
                        val bm = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                            ?: continue
                        val left = col * (cellW + gap)
                        val top = row * (cellH + gap)
                        val dst = Rect(left, top, left + cellW, top + cellH)
                        canvas.drawBitmap(bm, centerCropSrcRect(bm, cellW, cellH), dst, null)
                    }
                }

                val filename = "Collage_${System.currentTimeMillis()}.jpg"
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/GalleryApp")
                    }
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues
                )
                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { stream ->
                        output.compress(Bitmap.CompressFormat.JPEG, 92, stream)
                    }
                }
                output.recycle()
                _uiState.update { it.copy(isSaving = false, saveMessage = "Collage saved to Pictures/GalleryApp") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, saveMessage = "Failed to save collage") }
            }
        }
    }

    fun onSaveMessageShown() = _uiState.update { it.copy(saveMessage = null) }

    private fun centerCropSrcRect(bitmap: Bitmap, targetW: Int, targetH: Int): Rect {
        val srcAspect = bitmap.width.toFloat() / bitmap.height
        val dstAspect = targetW.toFloat() / targetH
        return if (srcAspect > dstAspect) {
            val srcW = (bitmap.height * dstAspect).toInt()
            val srcX = (bitmap.width - srcW) / 2
            Rect(srcX, 0, srcX + srcW, bitmap.height)
        } else {
            val srcH = (bitmap.width / dstAspect).toInt()
            val srcY = (bitmap.height - srcH) / 2
            Rect(0, srcY, bitmap.width, srcY + srcH)
        }
    }
}
