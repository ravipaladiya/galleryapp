package com.grow.gallery.feature.video

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMuxer
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
import java.io.File
import java.nio.ByteBuffer
import javax.inject.Inject

data class VideoUiState(
    val videoUri: Uri? = null,
    val displayName: String = "",
    val duration: Long = 0L,
    val isLoading: Boolean = true,
    val error: String? = null,
    val trimStart: Long = 0L,
    val trimEnd: Long = 0L,
    val isExporting: Boolean = false,
    val exportProgress: Float = 0f,
    val exportSuccess: Boolean = false,
    val exportError: String? = null,
)

@HiltViewModel
class VideoViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VideoUiState())
    val uiState: StateFlow<VideoUiState> = _uiState.asStateFlow()

    fun loadVideo(mediaId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val item = mediaRepository.getMediaById(mediaId, isVideo = true)
                _uiState.update {
                    it.copy(
                        videoUri = item?.uri,
                        displayName = item?.displayName ?: "",
                        duration = item?.duration ?: 0L,
                        trimEnd = item?.duration ?: 0L,
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun setTrimStart(ms: Long) {
        val end = _uiState.value.trimEnd
        _uiState.update { it.copy(trimStart = ms.coerceIn(0L, (end - 1000L).coerceAtLeast(0L))) }
    }

    fun setTrimEnd(ms: Long) {
        val duration = _uiState.value.duration
        if (duration <= 0L) return  // metadata not loaded yet; avoid coerceIn(min > max) crash
        val start = _uiState.value.trimStart
        val minEnd = (start + 1000L).coerceAtMost(duration)
        _uiState.update { it.copy(trimEnd = ms.coerceIn(minEnd, duration)) }
    }

    fun exportTrimmedVideo() {
        val state = _uiState.value
        val inputUri = state.videoUri ?: return
        if (state.isExporting) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(isExporting = true, exportError = null, exportSuccess = false, exportProgress = 0f)
            }
            var outputUri: Uri? = null
            try {
                outputUri = createOutputUri(state.displayName)
                    ?: throw IllegalStateException("Could not create output file in MediaStore")

                withContext(Dispatchers.IO) {
                    trimWithMuxer(
                        context = context,
                        inputUri = inputUri,
                        outputUri = outputUri,
                        startMs = state.trimStart,
                        endMs = state.trimEnd,
                        onProgress = { p -> _uiState.update { it.copy(exportProgress = p) } },
                    )
                }

                // Mark file as no longer pending
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Video.Media.IS_PENDING, 0)
                    }
                    context.contentResolver.update(outputUri, values, null, null)
                }

                _uiState.update {
                    it.copy(isExporting = false, exportSuccess = true, exportProgress = 1f)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                outputUri?.let { context.contentResolver.delete(it, null, null) }
                throw e
            } catch (e: Exception) {
                outputUri?.let { context.contentResolver.delete(it, null, null) }
                _uiState.update {
                    it.copy(isExporting = false, exportError = e.message ?: "Export failed")
                }
            }
        }
    }

    private fun createOutputUri(originalName: String): Uri? {
        val base = originalName.substringBeforeLast(".")
        // Always output an mp4 container — the muxer writes MPEG-4 regardless of source extension
        val outputName = "${base}_trimmed.mp4"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, outputName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/GalleryApp")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            context.contentResolver.insert(
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                values,
            )
        } else {
            // Pre-Q: write to public Movies so user can see it in other gallery apps
            @Suppress("DEPRECATION")
            val dir = File(
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES),
                "GalleryApp",
            )
            dir.mkdirs()
            val file = File(dir, outputName)
            Uri.fromFile(file)
        }
    }

    fun onExportDismissed() {
        _uiState.update { it.copy(exportSuccess = false, exportError = null, exportProgress = 0f) }
    }
}

internal fun trimWithMuxer(
    context: Context,
    inputUri: Uri,
    outputUri: Uri,
    startMs: Long,
    endMs: Long,
    onProgress: (Float) -> Unit,
) {
    val inputPfd = context.contentResolver.openFileDescriptor(inputUri, "r")
        ?: throw IllegalStateException("Cannot open input video")
    val outputPfd = context.contentResolver.openFileDescriptor(outputUri, "rw")
        ?: throw IllegalStateException("Cannot open output file for writing")

    inputPfd.use { ipfd ->
        outputPfd.use { opfd ->
            val extractor = MediaExtractor()
            val muxer = MediaMuxer(opfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            try {
                extractor.setDataSource(ipfd.fileDescriptor)

                // Determine per-track max input size and read rotation from video track
                val trackIndexMap = mutableMapOf<Int, Int>()
                var maxInputSize = 512 * 1024 // 512 KB minimum
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: continue
                    if (mime.startsWith("video/")) {
                        // Preserve source rotation so portrait videos stay upright
                        val rotation = if (format.containsKey(android.media.MediaFormat.KEY_ROTATION))
                            format.getInteger(android.media.MediaFormat.KEY_ROTATION) else 0
                        if (rotation != 0) muxer.setOrientationHint(rotation)
                        // Size buffer to at least maxInputSize from the format
                        if (format.containsKey(android.media.MediaFormat.KEY_MAX_INPUT_SIZE)) {
                            maxInputSize = maxOf(maxInputSize, format.getInteger(android.media.MediaFormat.KEY_MAX_INPUT_SIZE))
                        }
                        extractor.selectTrack(i)
                        trackIndexMap[i] = muxer.addTrack(format)
                    } else if (mime.startsWith("audio/")) {
                        if (format.containsKey(android.media.MediaFormat.KEY_MAX_INPUT_SIZE)) {
                            maxInputSize = maxOf(maxInputSize, format.getInteger(android.media.MediaFormat.KEY_MAX_INPUT_SIZE))
                        }
                        extractor.selectTrack(i)
                        trackIndexMap[i] = muxer.addTrack(format)
                    }
                }

                muxer.start()
                extractor.seekTo(startMs * 1000L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

                val buffer = ByteBuffer.allocateDirect(maxInputSize)
                val bufferInfo = MediaCodec.BufferInfo()
                val rangeDurationUs = ((endMs - startMs) * 1000L).coerceAtLeast(1L)
                // Per-track PTS dedup map — a single global variable incorrectly drops the
                // first audio or video sample at PTS=0 when both tracks start simultaneously.
                val lastPtsPerTrack = mutableMapOf<Int, Long>()

                while (true) {
                    val trackIndex = extractor.sampleTrackIndex
                    if (trackIndex < 0) break

                    val sampleTimeUs = extractor.sampleTime
                    if (sampleTimeUs > endMs * 1000L) break

                    val muxerTrackIndex = trackIndexMap[trackIndex]
                    if (muxerTrackIndex == null) {
                        extractor.advance()
                        continue
                    }

                    bufferInfo.size = extractor.readSampleData(buffer, 0)
                    if (bufferInfo.size < 0) break

                    val rebased = (sampleTimeUs - startMs * 1000L).coerceAtLeast(0L)
                    val lastPts = lastPtsPerTrack[muxerTrackIndex] ?: Long.MIN_VALUE
                    // Deduplicate PTS=0 samples per-track to avoid dropping legitimate first frames
                    if (rebased <= lastPts && rebased == 0L) {
                        extractor.advance()
                        continue
                    }
                    lastPtsPerTrack[muxerTrackIndex] = rebased

                    bufferInfo.offset = 0
                    bufferInfo.presentationTimeUs = rebased
                    // Translate MediaExtractor flag space → MediaCodec flag space explicitly
                    bufferInfo.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0)
                        MediaCodec.BUFFER_FLAG_KEY_FRAME else 0

                    muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                    extractor.advance()

                    val elapsed = (sampleTimeUs - startMs * 1000L).coerceAtLeast(0L)
                    onProgress((elapsed.toFloat() / rangeDurationUs).coerceIn(0f, 1f))
                }

                muxer.stop()
            } finally {
                muxer.release()
                extractor.release()
            }
        }
    }
}
