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
)

data class MemoriesUiState(
    val memories: List<MemoryGroup> = emptyList(),
    val onThisDay: List<MediaItem> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class MemoriesViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MemoriesUiState())
    val uiState: StateFlow<MemoriesUiState> = _uiState.asStateFlow()

    init { loadMemories() }

    private fun loadMemories() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val groups = mediaRepository.loadMedia(MediaQuery(sortOrder = SortOrder.NEWEST))
                val allItems = groups.flatMap { it.items }

                // On this day - same month/day from previous years
                val cal = Calendar.getInstance()
                val currentMonth = cal.get(Calendar.MONTH)
                val currentDay = cal.get(Calendar.DAY_OF_MONTH)
                val currentYear = cal.get(Calendar.YEAR)

                val onThisDay = allItems.filter { item ->
                    val itemCal = Calendar.getInstance().apply {
                        timeInMillis = (item.dateTaken ?: item.dateAdded) * 1000
                    }
                    itemCal.get(Calendar.MONTH) == currentMonth &&
                            itemCal.get(Calendar.DAY_OF_MONTH) == currentDay &&
                            itemCal.get(Calendar.YEAR) < currentYear
                }

                // Group by month/year for memories
                val memories = allItems
                    .groupBy { item ->
                        val itemCal = Calendar.getInstance().apply {
                            timeInMillis = (item.dateTaken ?: item.dateAdded) * 1000
                        }
                        val month = itemCal.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault()) ?: ""
                        val year = itemCal.get(Calendar.YEAR)
                        Pair("Best of $month $year", year)
                    }
                    .entries
                    .filter { it.value.size >= 5 } // only groups with enough photos
                    .take(10)
                    .map { (labelYear, items) ->
                        MemoryGroup(
                            label = labelYear.first,
                            items = items.take(20),
                            year = labelYear.second,
                        )
                    }

                _uiState.update {
                    it.copy(memories = memories, onThisDay = onThisDay, isLoading = false)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}
