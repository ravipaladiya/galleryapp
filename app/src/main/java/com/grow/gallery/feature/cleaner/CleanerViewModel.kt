package com.grow.gallery.feature.cleaner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grow.gallery.core.media.MediaQuery
import com.grow.gallery.core.media.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class CleanerPhase { IDLE, SCANNING, RESULTS, DONE }

data class CleanerCategory(
    val name: String,
    val itemCount: Int,
    val size: String,
    val isSelected: Boolean = true,
)

data class CleanerUiState(
    val phase: CleanerPhase = CleanerPhase.IDLE,
    val categories: List<CleanerCategory> = emptyList(),
    val totalReclaimable: String = "0 MB",
    val freedSpace: String = "0 MB",
    val deletedCount: Int = 0,
)

@HiltViewModel
class CleanerViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CleanerUiState())
    val uiState: StateFlow<CleanerUiState> = _uiState.asStateFlow()

    fun startScan() {
        viewModelScope.launch {
            _uiState.update { it.copy(phase = CleanerPhase.SCANNING) }
            delay(2500) // simulated scan time

            val groups = mediaRepository.loadMedia(MediaQuery())
            val allItems = groups.flatMap { it.items }

            // Detect categories
            val screenshots = allItems.filter {
                it.displayName.lowercase().contains("screenshot") ||
                        it.bucketName.lowercase().contains("screenshot")
            }
            val videos = allItems.filter { it.isVideo }
            val largeItems = allItems.filter { it.size > 10_000_000 } // > 10MB

            val categories = mutableListOf<CleanerCategory>()

            if (screenshots.isNotEmpty()) {
                categories.add(
                    CleanerCategory(
                        name = "Screenshots",
                        itemCount = screenshots.size,
                        size = screenshots.sumOf { it.size }.toFormattedSize(),
                    )
                )
            }

            // Simulated similar photos detection
            if (allItems.size > 10) {
                val similarCount = allItems.size / 10
                categories.add(
                    CleanerCategory(
                        name = "Similar Photos",
                        itemCount = similarCount,
                        size = (similarCount * 3_000_000L).toFormattedSize(),
                    )
                )
            }

            if (largeItems.isNotEmpty()) {
                categories.add(
                    CleanerCategory(
                        name = "Large Videos",
                        itemCount = largeItems.size,
                        size = largeItems.sumOf { it.size }.toFormattedSize(),
                    )
                )
            }

            val totalBytes = categories.sumOf { extractBytes(it.size) }
            _uiState.update {
                it.copy(
                    phase = CleanerPhase.RESULTS,
                    categories = categories,
                    totalReclaimable = totalBytes.toFormattedSize(),
                )
            }
        }
    }

    fun cleanSelected() {
        viewModelScope.launch {
            val selected = _uiState.value.categories.filter { it.isSelected }
            val count = selected.sumOf { it.itemCount }
            val freed = selected.sumOf { extractBytes(it.size) }
            delay(1000)
            _uiState.update {
                it.copy(
                    phase = CleanerPhase.DONE,
                    deletedCount = count,
                    freedSpace = freed.toFormattedSize(),
                )
            }
        }
    }

    fun cleanAll() {
        viewModelScope.launch {
            val count = _uiState.value.categories.sumOf { it.itemCount }
            val freed = extractBytes(_uiState.value.totalReclaimable)
            delay(1000)
            _uiState.update {
                it.copy(
                    phase = CleanerPhase.DONE,
                    deletedCount = count,
                    freedSpace = freed.toFormattedSize(),
                )
            }
        }
    }

    private fun extractBytes(formatted: String): Long {
        val num = formatted.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0
        return when {
            formatted.contains("GB") -> (num * 1_000_000_000).toLong()
            formatted.contains("MB") -> (num * 1_000_000).toLong()
            formatted.contains("KB") -> (num * 1_000).toLong()
            else -> num.toLong()
        }
    }

    private fun Long.toFormattedSize(): String = when {
        this >= 1_000_000_000L -> "%.1f GB".format(this / 1_000_000_000.0)
        this >= 1_000_000L -> "%.1f MB".format(this / 1_000_000.0)
        this >= 1_000L -> "%.0f KB".format(this / 1_000.0)
        else -> "$this B"
    }
}
