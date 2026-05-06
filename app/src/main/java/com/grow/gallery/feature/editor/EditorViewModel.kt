package com.grow.gallery.feature.editor

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grow.gallery.core.media.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class EditorUiState(
    val imageUri: Uri? = null,
    val savedUri: Uri? = null,
    val isProcessing: Boolean = false,
    val isSaved: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    // Adjustment values: all in range -1f..1f, default 0f
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val sharpness: Float = 0f,
)

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private var originalDisplayName: String = ""

    fun loadMedia(mediaId: Long) {
        viewModelScope.launch {
            val item = mediaRepository.getMediaById(mediaId, isVideo = false)
            originalDisplayName = item?.displayName ?: ""
            _uiState.update { it.copy(imageUri = item?.uri, isLoading = false) }
        }
    }

    fun setBrightness(value: Float) = _uiState.update { it.copy(brightness = value) }
    fun setContrast(value: Float) = _uiState.update { it.copy(contrast = value) }
    fun setSaturation(value: Float) = _uiState.update { it.copy(saturation = value) }
    fun setSharpness(value: Float) = _uiState.update { it.copy(sharpness = value) }
    fun resetAdjustments() = _uiState.update { it.copy(brightness = 0f, contrast = 0f, saturation = 0f, sharpness = 0f) }

    fun onSavedHandled() = _uiState.update { it.copy(isSaved = false, savedUri = null) }
    fun clearError() = _uiState.update { it.copy(error = null) }

    fun saveImage() {
        val uri = _uiState.value.imageUri ?: return
        if (_uiState.value.isProcessing) return

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, error = null) }
            try {
                val savedUri = withContext(Dispatchers.IO) {
                    applyAndSave(uri)
                }
                _uiState.update {
                    it.copy(isProcessing = false, isSaved = savedUri != null, savedUri = savedUri,
                        error = if (savedUri == null) "Failed to save image" else null)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isProcessing = false, error = "Save failed: ${e.message}") }
            }
        }
    }

    private fun applyAndSave(sourceUri: Uri): Uri? {
        val state = _uiState.value
        val src = context.contentResolver.openInputStream(sourceUri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        } ?: return null

        val output: Bitmap
        val hasAdjustments = state.brightness != 0f || state.contrast != 0f ||
                state.saturation != 0f || state.sharpness != 0f

        if (hasAdjustments) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.colorFilter = ColorMatrixColorFilter(buildColorMatrix(state))
            output = Bitmap.createBitmap(src.width, src.height, src.config ?: Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            canvas.drawBitmap(src, 0f, 0f, paint)
            src.recycle()
        } else {
            output = src
        }

        val base = originalDisplayName.substringBeforeLast(".")
        val outputName = "${base}_edited_${System.currentTimeMillis()}.jpg"

        val outUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, outputName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/GalleryApp")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            context.contentResolver.insert(
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values
            )
        } else {
            @Suppress("DEPRECATION")
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, outputName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            }
            context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        }

        outUri?.let { uri ->
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                output.compress(Bitmap.CompressFormat.JPEG, 95, stream)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                    null, null,
                )
            }
        }
        if (output !== src) output.recycle()
        return outUri
    }

    private fun buildColorMatrix(state: EditorUiState): ColorMatrix {
        val matrix = ColorMatrix()

        // Saturation
        val satMatrix = ColorMatrix()
        satMatrix.setSaturation(1f + state.saturation)
        matrix.postConcat(satMatrix)

        // Brightness and Contrast combined
        val bright = state.brightness
        val cont = 1f + state.contrast
        val translate = ((-0.5f * cont + 0.5f + bright) * 255f)
        val bcArray = floatArrayOf(
            cont, 0f, 0f, 0f, translate,
            0f, cont, 0f, 0f, translate,
            0f, 0f, cont, 0f, translate,
            0f, 0f, 0f, 1f, 0f,
        )
        matrix.postConcat(ColorMatrix(bcArray))

        return matrix
    }
}
