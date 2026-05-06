package com.grow.gallery.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(bottom = Spacing.xs),
            ) {
                Text(
                    "Search",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                )

                OutlinedTextField(
                    value = uiState.query,
                    onValueChange = viewModel::onQueryChange,
                    placeholder = { Text("Search photos, albums, dates…") },
                    leadingIcon = { Icon(Icons.Default.Search, "Search") },
                    trailingIcon = {
                        if (uiState.query.isNotBlank()) {
                            IconButton(onClick = { viewModel.onQueryChange("") }) {
                                Icon(Icons.Default.Clear, "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg)
                        .focusRequester(focusRequester),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Brand.Blue,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                )

                Spacer(Modifier.height(Spacing.sm))

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
                    onClearRecents = viewModel::clearRecentSearches,
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
                Column(modifier = Modifier.padding(paddingValues)) {
                    Text(
                        "${uiState.results.size} result${if (uiState.results.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(bottom = 16.dp),
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
}

@Composable
private fun SearchSuggestions(
    recentSearches: List<String>,
    onQuerySelect: (String) -> Unit,
    onClearRecents: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (recentSearches.isNotEmpty()) {
        Column(modifier = modifier) {
            SectionHeader(
                title = "Recent Searches",
                trailing = {
                    TextButton(onClick = onClearRecents) {
                        Text("Clear", style = MaterialTheme.typography.labelSmall)
                    }
                },
            )
            recentSearches.forEach { query ->
                ListItem(
                    headlineContent = { Text(query) },
                    leadingContent = { Icon(Icons.Default.History, null) },
                    trailingContent = {
                        Icon(
                            Icons.Default.NorthWest,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    modifier = Modifier.clickable { onQuerySelect(query) },
                )
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(Spacing.xxxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Default.Search,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f),
                modifier = Modifier.size(72.dp),
            )
            Spacer(Modifier.height(Spacing.xl))
            Text(
                "Search photos, albums, and dates",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "Find your memories by filename, album name, or date.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}
