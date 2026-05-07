package com.grow.gallery.feature.memories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grow.gallery.core.media.MediaItem
import com.grow.gallery.core.media.MediaQuery
import com.grow.gallery.core.media.MediaRepository
import com.grow.gallery.core.media.SortOrder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

data class MemoryGroup(
    val label: String,
    val items: List<MediaItem>,
    val year: Int = 0,
    val monthIndex: Int = 0,
)

data class MemoriesUiState(
    val memories: List<MemoryGroup> = emptyList(),
    val onThisDay: List<MediaItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class MemoriesViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MemoriesUiState())
    val uiState: StateFlow<MemoriesUiState> = _uiState.asStateFlow()

    init { loadMemories() }

    fun refresh() = loadMemories()

    private fun loadMemories() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val groups = mediaRepository.loadMedia(MediaQuery(sortOrder = SortOrder.NEWEST))
                val allItems = groups.flatMap { it.items }

                val cal = Calendar.getInstance()
                val currentMonth = cal.get(Calendar.MONTH)
                val currentDay = cal.get(Calendar.DAY_OF_MONTH)
                val currentYear = cal.get(Calendar.YEAR)

                // Per-item Calendar — avoids shared-mutable-state bugs across filter/groupBy/map.
                fun Long.toCalendar(): Calendar = Calendar.getInstance().also { it.timeInMillis = this * 1000L }

                val onThisDay = allItems.filter { item ->
                    val c = (item.dateTaken ?: item.dateAdded).toCalendar()
                    c.get(Calendar.MONTH) == currentMonth &&
                            c.get(Calendar.DAY_OF_MONTH) == currentDay &&
                            c.get(Calendar.YEAR) < currentYear // exclude today's photos
                }

                // Group by month/year for memories (min 3 items — consistent with the filter below)
                val memories = allItems
                    .groupBy { item ->
                        val c = (item.dateTaken ?: item.dateAdded).toCalendar()
                        Pair(c.get(Calendar.MONTH), c.get(Calendar.YEAR))
                    }
                    .entries
                    .filter { it.value.size >= 3 }
                    .map { (monthYear, items) ->
                        val (month, year) = monthYear
                        // Fresh Calendar for display-name lookup avoids day-normalisation artifacts.
                        val nameCal = Calendar.getInstance().also { it.set(year, month, 1) }
                        val monthName = nameCal.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault())
                            ?.takeIf { it.isNotBlank() }
                            ?: (month + 1).toString()
                        MemoryGroup(
                            // "Photos from …" is honest — no curation signal is applied yet.
                            label = "Photos from $monthName $year",
                            items = items.take(20),
                            year = year,
                            monthIndex = month,
                        )
                    }
                    // Explicit sort: newest month first, independent of repository ordering
                    .sortedByDescending { it.year * 100 + it.monthIndex }

                _uiState.update {
                    it.copy(memories = memories, onThisDay = onThisDay, isLoading = false)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Failed to load memories") }
            }
        }
    }
}
