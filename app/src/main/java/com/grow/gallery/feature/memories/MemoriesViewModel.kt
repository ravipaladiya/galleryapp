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
    val month: Int = 0,
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

                val now = Calendar.getInstance()
                val currentMonth = now.get(Calendar.MONTH)
                val currentDay = now.get(Calendar.DAY_OF_MONTH)
                val currentYear = now.get(Calendar.YEAR)

                val onThisDay = allItems.filter { item ->
                    val ts = (item.dateTaken ?: item.dateAdded) * 1000L
                    val itemCal = Calendar.getInstance().apply { timeInMillis = ts }
                    itemCal.get(Calendar.MONTH) == currentMonth &&
                        itemCal.get(Calendar.DAY_OF_MONTH) == currentDay &&
                        itemCal.get(Calendar.YEAR) < currentYear
                }

                // Group by year+month, sort newest first, require ≥5 photos per group
                val memories = allItems
                    .groupBy { item ->
                        val ts = (item.dateTaken ?: item.dateAdded) * 1000L
                        val cal = Calendar.getInstance().apply { timeInMillis = ts }
                        Pair(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
                    }
                    .entries
                    .filter { it.value.size >= 5 }
                    .sortedWith(compareByDescending<Map.Entry<Pair<Int, Int>, List<MediaItem>>> { it.key.first }
                        .thenByDescending { it.key.second })
                    .map { (yearMonth, items) ->
                        val (year, month) = yearMonth
                        val cal = Calendar.getInstance().apply {
                            set(Calendar.YEAR, year)
                            set(Calendar.MONTH, month)
                        }
                        val monthName = cal.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault()) ?: ""
                        MemoryGroup(
                            label = "Best of $monthName $year",
                            items = items.take(20),
                            year = year,
                            month = month,
                        )
                    }

                _uiState.update {
                    it.copy(memories = memories, onThisDay = onThisDay, isLoading = false)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Could not load memories.") }
            }
        }
    }
}
