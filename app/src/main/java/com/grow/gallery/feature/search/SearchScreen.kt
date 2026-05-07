package com.grow.gallery.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.saveable.rememberSaveable
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
    // Only focus on the very first entry into this screen
    var hasFocused by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!hasFocused) {
            hasFocused = true
            focusRequester.requestFocus()
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(bottom = Spacing.xs),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Search",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = Spacing.sm),
                    )
                    IconButton(onClick = onOpenMapView) {
                        Icon(Icons.Default.Map, "Map view")
                    }
                }

                OutlinedTextField(
                    value = uiState.query,
                    onValueChange = viewModel::onQueryChange,
                    placeholder = { Text("Search photos and albums…") },
                    leadingIcon = { Icon(Icons.Default.Search, "Search") },
                    trailingIcon = {
                        if (uiState.query.isNotBlank()) {
                            IconButton(onClick = {
                                viewModel.onQueryChange("")
                                focusRequester.requestFocus()
                            }) {
                                Icon(Icons.Default.Clear, "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        focusManager.clearFocus()
                        viewModel.commitSearch()
                    }),
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
                }
            }
        },
    ) { paddingValues ->
        when {
            uiState.isLoading -> LoadingScreen(Modifier.padding(paddingValues))
            uiState.query.isBlank() -> {
                SearchSuggestions(
                    recentSearches = uiState.recentSearches,
                    onQuerySelect = { query ->
                        viewModel.onQueryChange(query)
                        viewModel.commitSearch()
                    },
                    onClearRecents = viewModel::clearRecentSearches,
                    onRemoveRecent = viewModel::removeRecentSearch,
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
                    val size = uiState.results.size
                    Text(
                        "$size result${if (size == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(
                            start = Spacing.xs,
                            end = Spacing.xs,
                            bottom = 16.dp,
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
                                onClick = {
                                    viewModel.commitSearch()
                                    onOpenViewer(item.id, item.isVideo)
                                },
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
    onRemoveRecent: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (recentSearches.isNotEmpty()) {
        LazyColumn(modifier = modifier) {
            item {
                SectionHeader(
                    title = "Recent Searches",
                    trailing = {
                        TextButton(onClick = onClearRecents) {
                            Text("Clear", style = MaterialTheme.typography.labelSmall)
                        }
                    },
                )
            }
            items(recentSearches, key = { it }) { query ->
                ListItem(
                    headlineContent = { Text(query) },
                    leadingContent = { Icon(Icons.Default.History, null) },
                    trailingContent = {
                        IconButton(onClick = { onRemoveRecent(query) }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove $query from recent searches",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
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
                "Search your gallery",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "Find photos and videos by filename or album name.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}
