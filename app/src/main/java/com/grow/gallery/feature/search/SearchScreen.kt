package com.grow.gallery.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grow.gallery.core.designsystem.*
import com.grow.gallery.core.designsystem.components.*
import com.grow.gallery.core.media.MediaFilter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onOpenViewer: (Long, Boolean) -> Unit,
    onOpenMapView: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    Scaffold(
        topBar = {
            Column {
                Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                Text(
                    "Search",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                )
                SearchBar(
                    query = uiState.query,
                    onQueryChange = viewModel::onQueryChange,
                    onSearch = { focusManager.clearFocus() },
                    active = false,
                    onActiveChange = {},
                    placeholder = { Text("Search photos, albums, dates…") },
                    leadingIcon = { Icon(Icons.Default.Search, "Search") },
                    trailingIcon = {
                        if (uiState.query.isNotBlank()) {
                            IconButton(onClick = { viewModel.onQueryChange("") }) {
                                Icon(Icons.Default.Clear, "Clear")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg)
                        .focusRequester(focusRequester),
                ) {}
                Spacer(Modifier.height(Spacing.sm))

                // Filter chips
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(
                        listOf(
                            MediaFilter.ALL to "All",
                            MediaFilter.PHOTOS to "Photos",
                            MediaFilter.VIDEOS to "Videos",
                            MediaFilter.FAVORITES to "Favorites",
                        )
                    ) { (filter, label) ->
                        FilterChip(
                            selected = uiState.filter == filter,
                            onClick = { viewModel.setFilter(filter) },
                            label = { Text(label) },
                            leadingIcon = if (uiState.filter == filter) {
                                { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                            } else null,
                        )
                    }

                    item {
                        AssistChip(
                            onClick = onOpenMapView,
                            label = { Text("Map") },
                            leadingIcon = { Icon(Icons.Default.Map, null, Modifier.size(16.dp)) },
                        )
                    }
                }
            }
        },
    ) { paddingValues ->
        when {
            uiState.isLoading -> LoadingScreen(Modifier.padding(paddingValues))
            uiState.query.isBlank() -> {
                SearchSuggestions(
                    recentSearches = uiState.recentSearches,
                    onQuerySelect = viewModel::onQueryChange,
                    modifier = Modifier.padding(paddingValues),
                )
            }
            uiState.results.isEmpty() -> {
                EmptyState(
                    icon = Icons.Default.SearchOff,
                    title = "No Results",
                    description = "Try different keywords or filters.",
                    modifier = Modifier.padding(paddingValues),
                )
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(
                        top = paddingValues.calculateTopPadding() + 8.dp,
                        bottom = paddingValues.calculateBottomPadding() + 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(
                        items = uiState.results,
                        key = { it.id },
                    ) { item ->
                        MediaGridItem(
                            item = item,
                            isSelected = false,
                            isSelectionMode = false,
                            onClick = { onOpenViewer(item.id, item.isVideo) },
                            onLongClick = {},
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSuggestions(
    recentSearches: List<String>,
    onQuerySelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(Spacing.lg)) {
        if (recentSearches.isNotEmpty()) {
            SectionHeader("Recent Searches")
            recentSearches.forEach { query ->
                ListItem(
                    headlineContent = { Text(query) },
                    leadingContent = { Icon(Icons.Default.History, null) },
                    modifier = Modifier.clickable { onQuerySelect(query) },
                )
            }
        } else {
            Text(
                "Search for photos by name, date, location, or album.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
