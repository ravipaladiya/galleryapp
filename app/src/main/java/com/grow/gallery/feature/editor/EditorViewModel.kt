package com.grow.gallery.feature.editor

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
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
    // Crop ratio: "Free" / "1:1" / "4:3" / "16:9" / "3:2" / "9:16"
    val cropRatio: String = "Free",
    // Filter preset name
    val filterName: String = "None",
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
            originalDisplayName = item?.displayName ?: "photo"
            _uiState.update { it.copy(imageUri = item?.uri, isLoading = false) }
        }
    }

    fun setBrightness(value: Float) = _uiState.update { it.copy(brightness = value) }
    fun setContrast(value: Float) = _uiState.update { it.copy(contrast = value) }
    fun setSaturation(value: Float) = _uiState.update { it.copy(saturation = value) }
    fun setSharpness(value: Float) = _uiState.update { it.copy(sharpness = value) }
    fun setCropRatio(ratio: String) = _uiState.update { it.copy(cropRatio = ratio) }
    fun setFilter(filter: String) = _uiState.update { it.copy(filterName = filter) }
    fun resetAdjustments() = _uiState.update {
        it.copy(brightness = 0f, contrast = 0f, saturation = 0f, sharpness = 0f,
            cropRatio = "Free", filterName = "None")
    }

    fun onSavedHandled() = _uiState.update { it.copy(isSaved = false, savedUri = null) }
    fun clearError() = _uiState.update { it.copy(error = null) }

    fun hasAdjustments(): Boolean {
        val s = _uiState.value
        return s.brightness != 0f || s.contrast != 0f || s.saturation != 0f ||
                s.sharpness != 0f || s.cropRatio != "Free" || s.filterName != "None"
    }

    fun saveImage() {
        val uri = _uiState.value.imageUri
        if (uri == null) {
            _uiState.update { it.copy(error = "No photo loaded") }
            return
        }
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
        var bmp = decodeSampledBitmap(sourceUri, maxDim = 4096) ?: return null

        // Apply crop
        if (state.cropRatio != "Free") {
            val cropped = applyCrop(bmp, state.cropRatio)
            if (cropped !== bmp) bmp.recycle()
            bmp = cropped
        }

        // Build combined color matrix (adjustments + filter)
        val hasColorWork = state.brightness != 0f || state.contrast != 0f ||
                state.saturation != 0f || state.filterName != "None"

        if (hasColorWork) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.colorFilter = ColorMatrixColorFilter(buildColorMatrix(state))
            val colored = Bitmap.createBitmap(bmp.width, bmp.height, bmp.config ?: Bitmap.Config.ARGB_8888)
            val canvas = Canvas(colored)
            canvas.drawBitmap(bmp, 0f, 0f, paint)
            bmp.recycle()
            bmp = colored
        }

        // Apply sharpening (pixel-level convolution, only if sharpness > 0)
        if (state.sharpness > 0.01f) {
            val sharpened = applySharpen(bmp, state.sharpness)
            if (sharpened !== bmp) bmp.recycle()
            bmp = sharpened
        }

        val base = originalDisplayName.substringBeforeLast(".").ifBlank { "photo" }
        val outputName = "${base}_edited_${System.currentTimeMillis()}.jpg"

        var outUri: Uri? = null
        return try {
            outUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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

            val written = outUri?.let { uri ->
                val stream = context.contentResolver.openOutputStream(uri)
                if (stream != null) {
                    stream.use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        context.contentResolver.update(
                            uri,
                            ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                            null, null,
                        )
                    }
                    true
                } else {
                    false
                }
            } ?: false

            if (!written) {
                outUri?.let { context.contentResolver.delete(it, null, null) }
                null
            } else {
                outUri
            }
        } catch (e: Exception) {
            outUri?.let { context.contentResolver.delete(it, null, null) }
            throw e
        } finally {
            bmp.recycle()
        }
    }

    private fun decodeSampledBitmap(uri: Uri, maxDim: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val sampleSize = run {
            var scale = 1
            while (bounds.outWidth / scale > maxDim || bounds.outHeight / scale > maxDim) scale *= 2
            scale
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    /** Builds a combined ColorMatrix for brightness/contrast/saturation + filter preset. */
    internal fun buildColorMatrix(state: EditorUiState): ColorMatrix {
        val matrix = ColorMatrix()

        // When a filter applies its own saturation, skip the user saturation slider
        // to avoid compounding (e.g. Vivid at 1.5× × user 1.5× = 2.25×) (#H-ED3)
        val filterOverridesSaturation = state.filterName in setOf("Vivid", "B&W", "Chrome")
        if (!filterOverridesSaturation) {
            val satMatrix = ColorMatrix()
            satMatrix.setSaturation(1f + state.saturation)
            matrix.postConcat(satMatrix)
        }

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

        // Filter preset
        buildFilterMatrix(state.filterName)?.let { matrix.postConcat(it) }

        return matrix
    }

    private fun buildFilterMatrix(filterName: String): ColorMatrix? = when (filterName) {
        "Vivid" -> {
            val m = ColorMatrix()
            m.setSaturation(1.5f)
            m.postConcat(ColorMatrix(floatArrayOf(
                1.1f, 0f, 0f, 0f, 8f,
                0f, 1.1f, 0f, 0f, 8f,
                0f, 0f, 1.1f, 0f, 8f,
                0f, 0f, 0f, 1f, 0f,
            )))
            m
        }
        "Warm" -> ColorMatrix(floatArrayOf(
            1.2f, 0f, 0f, 0f, 20f,
            0f, 1.0f, 0f, 0f, 5f,
            0f, 0f, 0.8f, 0f, -20f,
            0f, 0f, 0f, 1f, 0f,
        ))
        "Cool" -> ColorMatrix(floatArrayOf(
            0.8f, 0f, 0f, 0f, -20f,
            0f, 1.0f, 0f, 0f, 5f,
            0f, 0f, 1.2f, 0f, 20f,
            0f, 0f, 0f, 1f, 0f,
        ))
        "B&W" -> ColorMatrix().apply { setSaturation(0f) }
        "Fade" -> ColorMatrix(floatArrayOf(
            0.8f, 0f, 0f, 0f, 40f,
            0f, 0.8f, 0f, 0f, 40f,
            0f, 0f, 0.8f, 0f, 40f,
            0f, 0f, 0f, 1f, 0f,
        ))
        "Chrome" -> {
            val m = ColorMatrix()
            m.setSaturation(0.3f)
            m.postConcat(ColorMatrix(floatArrayOf(
                1.3f, 0f, 0f, 0f, -20f,
                0f, 1.2f, 0f, 0f, -10f,
                0f, 0f, 1.1f, 0f, -5f,
                0f, 0f, 0f, 1f, 0f,
            )))
            m
        }
        else -> null
    }

    /** Center-crops a bitmap to the requested aspect ratio. */
    private fun applyCrop(src: Bitmap, cropRatio: String): Bitmap {
        val (rw, rh) = when (cropRatio) {
            "1:1" -> 1f to 1f
            "4:3" -> 4f to 3f
            "16:9" -> 16f to 9f
            "3:2" -> 3f to 2f
            "9:16" -> 9f to 16f
            else -> return src
        }
        val targetAspect = rw / rh
        val srcAspect = src.width.toFloat() / src.height.toFloat()
        val (cropW, cropH) = if (srcAspect > targetAspect) {
            (src.height * targetAspect).toInt() to src.height
        } else {
            src.width to (src.width / targetAspect).toInt()
        }
        val startX = (src.width - cropW) / 2
        val startY = (src.height - cropH) / 2
        return Bitmap.createBitmap(
            src,
            startX.coerceAtLeast(0),
            startY.coerceAtLeast(0),
            cropW.coerceIn(1, src.width),
            cropH.coerceIn(1, src.height),
        )
    }

    /**
     * Unsharp-mask sharpening via a 5-point Laplacian kernel.
     * Runs on a background thread (called from applyAndSave via Dispatchers.IO).
     */
    private fun applySharpen(src: Bitmap, strength: Float): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val out = IntArray(w * h)
        val k = strength.coerceIn(0f, 1f)

        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val c = pixels[y * w + x]
                val t = pixels[(y - 1) * w + x]
                val b = pixels[(y + 1) * w + x]
                val l = pixels[y * w + x - 1]
                val r = pixels[y * w + x + 1]
                val a = c ushr 24 and 0xFF
                fun sharpenChannel(shift: Int): Int {
                    val cv = (c shr shift and 0xFF).toFloat()
                    val neighbors = ((t shr shift and 0xFF) + (b shr shift and 0xFF) +
                            (l shr shift and 0xFF) + (r shr shift and 0xFF)).toFloat()
                    return (cv + k * (cv * 4f - neighbors)).toInt().coerceIn(0, 255)
                }
                out[y * w + x] = (a shl 24) or (sharpenChannel(16) shl 16) or
                        (sharpenChannel(8) shl 8) or sharpenChannel(0)
            }
        }
        // Copy border pixels unchanged
        for (x in 0 until w) {
            out[x] = pixels[x]
            out[(h - 1) * w + x] = pixels[(h - 1) * w + x]
        }
        for (y in 1 until h - 1) {
            out[y * w] = pixels[y * w]
            out[y * w + w - 1] = pixels[y * w + w - 1]
        }

        val result = Bitmap.createBitmap(w, h, src.config ?: Bitmap.Config.ARGB_8888)
        result.setPixels(out, 0, w, 0, 0, w, h)
        return result
    }
}
